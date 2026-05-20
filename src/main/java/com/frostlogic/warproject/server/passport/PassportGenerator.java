package com.frostlogic.warproject.server.passport;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.dao.PassportSequenceDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Generates passport data for newly accepted faction members.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Generate unique passport ID in format {@code <PREFIX>-NNNNNN} with uniqueness
 *       verification via {@link PassportsDao#exists(Connection, String)} and fallback
 *       to {@link PassportSequenceDao#nextValue(Connection, String)}</li>
 *   <li>Generate random date of birth (age 18–50 full years), format {@code dd.MM.yyyy}</li>
 *   <li>Generate signature seed via {@link SecureRandom#nextLong()}</li>
 * </ul>
 * <p>
 * Algorithm (Design §8.6):
 * <ol>
 *   <li>Try up to {@value #MAX_RANDOM_ATTEMPTS} random IDs using {@code SecureRandom.nextInt(1_000_000)}</li>
 *   <li>Verify each candidate does not already exist via {@code PassportsDao.exists}</li>
 *   <li>If all random attempts collide, fallback to monotonic sequence from {@code PassportSequenceDao}</li>
 * </ol>
 * <p>
 * Must be called within a JDBC transaction for atomicity.
 * <p>
 * Requirements: 12.4, 12.5, 12.6
 * Design: §8.6
 */
public final class PassportGenerator {

    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Maximum number of random ID generation attempts before falling back to the sequence.
     */
    static final int MAX_RANDOM_ATTEMPTS = 8;

    private static final int ID_SPACE = 1_000_000; // 000000..999999

    private final PassportsDao passportsDao;
    private final PassportSequenceDao sequenceDao;

    public PassportGenerator(PassportsDao passportsDao, PassportSequenceDao sequenceDao) {
        this.passportsDao = passportsDao;
        this.sequenceDao = sequenceDao;
    }

    /**
     * Generates a new {@link PassportsDao.Passport} record for the given player and faction.
     * Must be called within a transaction for atomicity.
     *
     * @param conn      the JDBC connection (within a transaction)
     * @param ownerUuid the UUID of the player receiving the passport
     * @param faction   the faction the player is joining
     * @param rpName    the player's RP first name
     * @param rpSurname the player's RP surname
     * @return a fully populated Passport record ready for insertion
     */
    public PassportsDao.Passport generate(Connection conn, String ownerUuid, FactionId faction,
                                          String rpName, String rpSurname) {
        String prefix = getPrefix(faction);
        String passportId = generateUniqueId(conn, prefix);

        String dateOfBirth = generateDateOfBirth();
        long signatureSeed = SECURE_RANDOM.nextLong();
        long now = System.currentTimeMillis();

        return new PassportsDao.Passport(
                passportId,
                ownerUuid,
                faction.getSerializedName().toUpperCase(),
                rpName,
                rpSurname,
                dateOfBirth,
                signatureSeed,
                "CANDIDATE",
                null,   // acceptedAt
                null,   // acceptedBy
                null,   // capturedByUuid
                null,   // capturedAt (set when player is actually captured; null at issue time)
                false,  // trophy
                now
        );
    }

    /**
     * Generates a unique passport ID using random attempts with fallback to sequence.
     * <p>
     * First tries up to {@link #MAX_RANDOM_ATTEMPTS} random 6-digit numbers, checking
     * each against the database via {@link PassportsDao#exists(Connection, String)}.
     * If all random attempts collide (extremely unlikely under normal conditions),
     * falls back to the monotonic sequence from {@link PassportSequenceDao}.
     *
     * @param conn   the JDBC connection (within a transaction)
     * @param prefix the faction prefix including dash (e.g. "ZRN-")
     * @return a unique passport ID string
     */
    private String generateUniqueId(Connection conn, String prefix) {
        // Phase 1: random attempts
        for (int attempt = 0; attempt < MAX_RANDOM_ATTEMPTS; attempt++) {
            int n = SECURE_RANDOM.nextInt(ID_SPACE);
            String candidateId = prefix + String.format("%06d", n);
            if (!passportsDao.exists(conn, candidateId)) {
                return candidateId;
            }
        }

        // Phase 2: fallback to monotonic sequence
        int seqNum = sequenceDao.nextValue(conn, prefix);
        String fallbackId = prefix + String.format("%06d", seqNum % ID_SPACE);

        // Even the sequence-based ID should be unique, but verify just in case
        if (!passportsDao.exists(conn, fallbackId)) {
            return fallbackId;
        }

        // If sequence also collides (extremely unlikely), keep incrementing
        for (int i = 0; i < MAX_RANDOM_ATTEMPTS; i++) {
            seqNum = sequenceDao.nextValue(conn, prefix);
            fallbackId = prefix + String.format("%06d", seqNum % ID_SPACE);
            if (!passportsDao.exists(conn, fallbackId)) {
                return fallbackId;
            }
        }

        throw new IllegalStateException(
                "Failed to generate unique passport ID for prefix '" + prefix +
                "' after " + MAX_RANDOM_ATTEMPTS + " random + " + (MAX_RANDOM_ATTEMPTS + 1) + " sequence attempts");
    }

    /**
     * Returns the passport ID prefix for the given faction (includes trailing dash).
     */
    static String getPrefix(FactionId faction) {
        return switch (faction) {
            case ZARNAVIA -> "ZRN-";
            case CHERNOGRYAD -> "CHN-";
        };
    }

    /**
     * Generates a random date of birth such that the person's age is between 18 and 50 full years.
     * <p>
     * The generated date guarantees that on the current date, the person is at least 18
     * and at most 50 years old (inclusive). Format: dd.MM.yyyy
     *
     * @return formatted date of birth string
     */
    static String generateDateOfBirth() {
        LocalDate today = LocalDate.now();
        // Earliest DOB: exactly 50 years ago today (person turns 50 today)
        LocalDate earliest = today.minusYears(50);
        // Latest DOB: exactly 18 years ago today (person turns 18 today)
        LocalDate latest = today.minusYears(18);

        long totalDays = ChronoUnit.DAYS.between(earliest, latest);
        long randomDays = nextLongInRange(totalDays + 1); // inclusive of both endpoints
        LocalDate dob = earliest.plusDays(randomDays);

        return dob.format(DOB_FORMAT);
    }

    /**
     * Returns a random long in [0, bound).
     */
    private static long nextLongInRange(long bound) {
        if (bound <= Integer.MAX_VALUE) {
            return SECURE_RANDOM.nextInt((int) bound);
        }
        // For very large ranges (unlikely here since ~32 years ≈ 11688 days)
        long r;
        do {
            r = SECURE_RANDOM.nextLong() & Long.MAX_VALUE;
        } while (r - (r % bound) + (bound - 1) < 0);
        return r % bound;
    }
}
