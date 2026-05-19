package com.frostlogic.warproject.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class WarPlayerProfile {

    /** Hard cap on biography length (in characters), enforced at write time. */
    public static final int BIO_MAX_CHARS = 500;

    /** Hard cap on a single diary entry length (in characters). */
    public static final int DIARY_ENTRY_MAX_CHARS = 500;

    /** Hard cap on diary length; oldest entry is dropped (FIFO) when exceeded. */
    public static final int DIARY_MAX_ENTRIES = 50;

    private static final String BCRYPT_SALT_MARKER = "bcrypt";
    private static final int BCRYPT_COST = 12;

    /**
     * Single diary entry. Persisted as JSON object {@code {"ts":<long>,"text":"…"}}
     * inside the profile's {@code diary} array.
     */
    public record DiaryEntry(long timestamp, String text) {
        public DiaryEntry {
            if (text == null) {
                text = "";
            }
        }
    }

    private final UUID uuid;
    private String lastKnownName;
    private String rpName;
    private Faction faction;
    private Faction candidateFaction;
    private Rank rank;
    private boolean collaborator;
    private Faction collaborationDeclaredBy;
    private boolean captchaPassed;
    private String passwordHash;
    private String passwordSalt;
    private transient boolean loggedIn;
    private int age;
    private Country birthCountry;
    private String subdivisionId;
    private boolean captive;
    private UUID capturedBy;
    private long createdAt;
    private long updatedAt;
    private long lastCommanderChatAt;
    private String bio;
    private final List<DiaryEntry> diary;

    private WarPlayerProfile(UUID uuid, String lastKnownName) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
        this.rpName = "";
        this.faction = Faction.NONE;
        this.candidateFaction = Faction.NONE;
        this.rank = Rank.NONE;
        this.collaborationDeclaredBy = Faction.NONE;
        this.age = 0;
        this.birthCountry = Country.UNKNOWN;
        this.subdivisionId = "";
        this.captive = false;
        this.capturedBy = null;
        this.bio = "";
        this.diary = new ArrayList<>();
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WarPlayerProfile create(UUID uuid, String lastKnownName) {
        return new WarPlayerProfile(uuid, lastKnownName);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            this.lastKnownName = lastKnownName;
            touch();
        }
    }

    public String getRpName() {
        return rpName;
    }

    public String getRpNameOrUsername() {
        return hasRpName() ? rpName : lastKnownName;
    }

    public boolean hasRpName() {
        return rpName != null && !rpName.isBlank();
    }

    public void setRpName(String rpName) {
        this.rpName = rpName == null ? "" : rpName.trim();
        touch();
    }

    public Faction getFaction() {
        return faction == null ? Faction.NONE : faction;
    }

    public void setFaction(Faction faction) {
        this.faction = faction == null ? Faction.NONE : faction;
        touch();
    }

    public Faction getCandidateFaction() {
        return candidateFaction == null ? Faction.NONE : candidateFaction;
    }

    public void setCandidateFaction(Faction candidateFaction) {
        this.candidateFaction = candidateFaction == null ? Faction.NONE : candidateFaction;
        touch();
    }

    public Rank getRank() {
        return rank == null ? Rank.NONE : rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank == null ? Rank.NONE : rank;
        touch();
    }

    public boolean isCollaborator() {
        return collaborator;
    }

    public void setCollaborator(boolean collaborator) {
        this.collaborator = collaborator;
        touch();
    }

    public Faction getCollaborationDeclaredBy() {
        return collaborationDeclaredBy == null ? Faction.NONE : collaborationDeclaredBy;
    }

    public void setCollaborationDeclaredBy(Faction collaborationDeclaredBy) {
        this.collaborationDeclaredBy = collaborationDeclaredBy == null ? Faction.NONE : collaborationDeclaredBy;
        touch();
    }

    public boolean isCaptchaPassed() {
        return captchaPassed;
    }

    public void setCaptchaPassed(boolean captchaPassed) {
        this.captchaPassed = captchaPassed;
        touch();
    }

    /** True if the player has set up a password (i.e. completed the registration step). */
    public boolean isRegistered() {
        return passwordHash != null && !passwordHash.isBlank()
                && passwordSalt != null && !passwordSalt.isBlank();
    }

    public String getPasswordHash() {
        return passwordHash == null ? "" : passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        touch();
    }

    public String getPasswordSalt() {
        return passwordSalt == null ? "" : passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt == null ? "" : passwordSalt;
        touch();
    }

    /**
     * Stores a production BCrypt hash for legacy JSON profiles.
     * <p>
     * Older profiles used SHA-256(salt || password). Those are still accepted by
     * {@link #verifyPassword(String)} once, then automatically re-hashed to BCrypt
     * in-memory and persisted by the normal profile save path.
     */
    public void setPassword(String rawPassword) {
        if (rawPassword == null) {
            rawPassword = "";
        }
        this.passwordSalt = BCRYPT_SALT_MARKER;
        this.passwordHash = com.frostlogic.warproject.server.auth.PasswordHasher.hash(rawPassword.toCharArray(), BCRYPT_COST);
        touch();
    }

    public boolean verifyPassword(String rawPassword) {
        if (!isRegistered()) {
            return false;
        }
        if (rawPassword == null) {
            rawPassword = "";
        }

        if (BCRYPT_SALT_MARKER.equals(passwordSalt)) {
            try {
                return com.frostlogic.warproject.server.auth.PasswordHasher.verify(rawPassword.toCharArray(), passwordHash);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        boolean legacyMatches = PasswordHasher.verify(rawPassword, passwordSalt, passwordHash);
        if (legacyMatches) {
            setPassword(rawPassword);
        }
        return legacyMatches;
    }

    /** Transient session flag, NOT persisted to disk. */
    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public boolean isOnboarded() {
        return isRegistered() && captchaPassed && hasRpName();
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
        touch();
    }

    public Country getBirthCountry() {
        return birthCountry == null ? Country.UNKNOWN : birthCountry;
    }

    public void setBirthCountry(Country birthCountry) {
        this.birthCountry = birthCountry == null ? Country.UNKNOWN : birthCountry;
        touch();
    }

    public String getSubdivisionId() {
        return subdivisionId == null ? "" : subdivisionId;
    }

    public void setSubdivisionId(String subdivisionId) {
        this.subdivisionId = subdivisionId == null ? "" : subdivisionId;
        touch();
    }

    public boolean hasSubdivision() {
        return subdivisionId != null && !subdivisionId.isBlank();
    }

    public boolean isCaptive() {
        return captive;
    }

    public void setCaptive(boolean captive) {
        this.captive = captive;
        touch();
    }

    public UUID getCapturedBy() {
        return capturedBy;
    }

    public void setCapturedBy(UUID capturedBy) {
        this.capturedBy = capturedBy;
        touch();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getLastCommanderChatAt() {
        return lastCommanderChatAt;
    }

    public void setLastCommanderChatAt(long lastCommanderChatAt) {
        this.lastCommanderChatAt = lastCommanderChatAt;
        touch();
    }

    // ---------------------------------------------------------------------
    // Biography (free-form RP text, visible to other players via /wp bio show).
    // ---------------------------------------------------------------------

    /** Returns the biography text. Empty string if never set. Never null. */
    public String getBio() {
        return bio == null ? "" : bio;
    }

    /** True iff the bio is non-blank. */
    public boolean hasBio() {
        return bio != null && !bio.isBlank();
    }

    /**
     * Sets the biography. Trims, truncates to {@link #BIO_MAX_CHARS},
     * and {@link #touch()}-es the profile so it gets persisted.
     * Pass empty string or null to clear.
     */
    public void setBio(String bio) {
        if (bio == null) {
            this.bio = "";
        } else {
            String trimmed = bio.trim();
            this.bio = trimmed.length() > BIO_MAX_CHARS ? trimmed.substring(0, BIO_MAX_CHARS) : trimmed;
        }
        touch();
    }

    // ---------------------------------------------------------------------
    // Diary (private, owner-only RP entries with timestamps).
    // ---------------------------------------------------------------------

    /** Read-only snapshot of diary entries in insertion order (oldest first). */
    public List<DiaryEntry> getDiary() {
        return Collections.unmodifiableList(diary);
    }

    /** Convenience: number of diary entries currently stored. */
    public int diarySize() {
        return diary.size();
    }

    /**
     * Appends a new diary entry. Trims and truncates the body to
     * {@link #DIARY_ENTRY_MAX_CHARS}; if the diary already has
     * {@link #DIARY_MAX_ENTRIES} entries, the oldest one is dropped (FIFO).
     *
     * @param text entry body; null/blank is rejected by returning {@code false}
     * @return the stored entry, or {@code null} if {@code text} was blank
     */
    public DiaryEntry addDiaryEntry(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > DIARY_ENTRY_MAX_CHARS) {
            trimmed = trimmed.substring(0, DIARY_ENTRY_MAX_CHARS);
        }
        DiaryEntry entry = new DiaryEntry(System.currentTimeMillis(), trimmed);
        diary.add(entry);
        while (diary.size() > DIARY_MAX_ENTRIES) {
            diary.remove(0);
        }
        touch();
        return entry;
    }

    /**
     * Removes a diary entry by its 1-based index as displayed in
     * {@code /wp diary list} (1 = oldest). Returns {@code true} on success.
     */
    public boolean removeDiaryEntry(int oneBasedIndex) {
        int idx = oneBasedIndex - 1;
        if (idx < 0 || idx >= diary.size()) {
            return false;
        }
        diary.remove(idx);
        touch();
        return true;
    }

    /**
     * Replaces the entire diary. Used by the data-store loader; not intended
     * for gameplay code (use {@link #addDiaryEntry(String)} instead).
     */
    public void replaceDiary(List<DiaryEntry> entries) {
        diary.clear();
        if (entries != null) {
            for (DiaryEntry e : entries) {
                if (e != null) {
                    diary.add(e);
                }
            }
            while (diary.size() > DIARY_MAX_ENTRIES) {
                diary.remove(0);
            }
        }
        touch();
    }

    public boolean isFactionMember() {
        return getFaction().isPlayable();
    }

    public boolean canUseCommanderTools() {
        return isFactionMember() && getRank().isCommander();
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }
}
