package com.frostlogic.warproject.server.captivity;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.passport.PassportComponentTypes;
import com.frostlogic.warproject.server.passport.PassportData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Optional;

/**
 * Service handling passport capture (captivity) and ransom mechanics.
 * <p>
 * The {@link #capture(ServerPlayer, ServerPlayer)} method atomically:
 * <ol>
 *   <li>Validates all preconditions (different factions, both ACCEPTED, raytrace ≤ 5,
 *       vulnerability predicate, target has passport)</li>
 *   <li>In a single DB transaction: updates passport (captured_by_uuid, trophy=1),
 *       updates player (captured=1), inserts audit log entry (CAPTURE_PASSPORT)</li>
 *   <li>Moves the passport ItemStack from target's inventory to initiator's inventory</li>
 * </ol>
 * <p>
 * The {@link #ransom(String, String, String)} method returns a captured passport to its
 * original owner (used by {@code RansomTradeMenu} or admin commands).
 * <p>
 * Replaces the legacy {@code server.CaptivityHandler}.
 * <p>
 * Requirements: 14.1–14.5
 * Design: §8.8
 */
public final class CaptivityService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Maximum raytrace distance for capture interaction (blocks). */
    private static final double MAX_CAPTURE_DISTANCE = 5.0;

    private final Database database;
    private final PassportsDao passportsDao;
    private final PlayersDao playersDao;
    private final AuditLogDao auditLogDao;

    public CaptivityService(Database database, PassportsDao passportsDao,
                            PlayersDao playersDao, AuditLogDao auditLogDao) {
        this.database = database;
        this.passportsDao = passportsDao;
        this.playersDao = playersDao;
        this.auditLogDao = auditLogDao;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Capture
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Result of a capture attempt.
     */
    public sealed interface CaptureResult {
        record Success(String passportId) implements CaptureResult {}
        record Failure(String errorKey) implements CaptureResult {}
    }

    /**
     * Attempts to capture the target's passport.
     * <p>
     * Validates all conditions, executes an atomic DB transaction, and moves the
     * passport ItemStack from the target's inventory to the initiator's inventory.
     *
     * @param initiator the player attempting the capture
     * @param target    the player whose passport is being captured
     * @return a {@link CaptureResult} indicating success or failure with an error key
     */
    public CaptureResult capture(ServerPlayer initiator, ServerPlayer target) {
        // --- Validation ---

        // 1. Different factions
        Optional<FactionId> initiatorFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        Optional<FactionId> targetFaction = target.getData(WpAttachmentTypes.FACTION.get());

        if (initiatorFaction.isEmpty() || targetFaction.isEmpty()) {
            return new CaptureResult.Failure("wp.captivity.error.no_faction");
        }
        if (initiatorFaction.get() == targetFaction.get()) {
            return new CaptureResult.Failure("wp.captivity.error.same_faction");
        }

        // 2. Both must be ACCEPTED
        PlayerState initiatorState = initiator.getData(WpAttachmentTypes.PLAYER_STATE.get());
        PlayerState targetState = target.getData(WpAttachmentTypes.PLAYER_STATE.get());

        if (initiatorState != PlayerState.ACCEPTED) {
            return new CaptureResult.Failure("wp.captivity.error.initiator_not_accepted");
        }
        if (targetState != PlayerState.ACCEPTED) {
            return new CaptureResult.Failure("wp.captivity.error.target_not_accepted");
        }

        // 3. Distance check (raytrace ≤ 5 blocks)
        if (!isWithinCaptureDistance(initiator, target)) {
            return new CaptureResult.Failure("wp.captivity.error.too_far");
        }

        // 4. Vulnerability predicate
        if (!isVulnerable(target)) {
            return new CaptureResult.Failure("wp.captivity.error.target_not_vulnerable");
        }

        // 5. Target has a passport
        PassportSlotInfo passportInfo = findPassportInInventory(target);
        if (passportInfo == null) {
            return new CaptureResult.Failure("wp.captivity.error.no_passport");
        }

        // 6. Target is not already captured
        if (target.getData(WpAttachmentTypes.CAPTURED.get())) {
            return new CaptureResult.Failure("wp.captivity.error.already_captured");
        }

        // --- Extract passport data ---
        PassportData passportData = passportInfo.stack().get(PassportComponentTypes.PASSPORT_DATA.get());
        if (passportData == null) {
            return new CaptureResult.Failure("wp.captivity.error.invalid_passport");
        }

        String passportId = passportData.passportId();
        String initiatorUuid = initiator.getUUID().toString();
        String targetUuid = target.getUUID().toString();
        String initiatorName = initiator.getGameProfile().getName();
        String targetName = target.getGameProfile().getName();

        // --- Atomic DB transaction ---
        database.transaction(conn -> {
            // Update passport: set captured_by_uuid and trophy flag
            passportsDao.updateCaptureState(conn, passportId, initiatorUuid, true);

            // Update player: set captured = 1
            playersDao.setCaptured(conn, targetUuid, true);

            // Insert audit log entry
            auditLogDao.insert(conn,
                    System.currentTimeMillis(),
                    initiatorUuid,
                    initiatorName,
                    targetUuid,
                    targetName,
                    "CAPTURE_PASSPORT",
                    null,
                    null
            );
        });

        // --- Move ItemStack from target to initiator ---
        // Create a trophy copy with updated PassportData
        PassportData trophyData = new PassportData(
                passportData.passportId(),
                passportData.faction(),
                passportData.rpName(),
                passportData.rpSurname(),
                passportData.dateOfBirth(),
                passportData.signatureSeed(),
                passportData.status(),
                passportData.acceptedAt(),
                passportData.acceptedBy(),
                true // trophy = true
        );

        ItemStack trophyStack = passportInfo.stack().copy();
        trophyStack.set(PassportComponentTypes.PASSPORT_DATA.get(), trophyData);

        // Remove from target's inventory
        target.getInventory().setItem(passportInfo.slot(), ItemStack.EMPTY);

        // Add to initiator's inventory
        if (!initiator.getInventory().add(trophyStack)) {
            // Fallback: place in main inventory slots (9..35)
            placeInMainInventory(initiator, trophyStack);
        }

        // --- Update attachments ---
        target.setData(WpAttachmentTypes.CAPTURED.get(), true);
        target.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.CAPTURED);

        // Legacy mirror: WarPlayerDataStore.captive drives the TAB prefix
        // (see WarPrefixManager) and the legacy CaptivityHandler tick effects.
        // Keeping both stores in sync until task 1 of the techdebt list lands.
        com.frostlogic.warproject.server.WarPlayerProfile legacy =
                com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(target);
        legacy.setCaptive(true);
        try {
            legacy.setCapturedBy(java.util.UUID.fromString(initiatorUuid));
        } catch (IllegalArgumentException ignored) {
            // Defensive: initiatorUuid is always a real UUID, but don't blow up
            // capture if somehow malformed.
        }
        com.frostlogic.warproject.server.WarPlayerDataStore.get().save();
        com.frostlogic.warproject.server.WarPrefixManager.refresh(target);

        // --- Disarm: drop weapons + armor at the prisoner's feet ---
        // Without this the captive can simply pull out a sword and re-engage.
        // We drop instead of voiding so the captor (or a teammate) can pick
        // up the gear; passport is already moved to the captor's inventory.
        disarmCaptive(target);

        LOGGER.info("Player {} captured passport {} from player {}",
                initiatorName, passportId, targetName);

        return new CaptureResult.Success(passportId);
    }

    /**
     * Drops the captive's weapons (entire hotbar 0..8) and armour pieces at
     * their feet. The passport itself is already gone — moved into the
     * captor's inventory before this call. The off-hand is cleared too,
     * because that is where shield / second-weapon binds typically sit.
     * <p>
     * Items are dropped via {@link ServerPlayer#drop(ItemStack, boolean)} so
     * they spawn as world {@link net.minecraft.world.entity.item.ItemEntity}s
     * with the player's velocity — natural and easily lootable.
     */
    private static void disarmCaptive(ServerPlayer captive) {
        Inventory inv = captive.getInventory();
        // Hotbar 0..8.
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;
            inv.setItem(slot, ItemStack.EMPTY);
            captive.drop(stack, true, false);
        }
        // Armor 36..39 (boots, leggings, chestplate, helmet).
        // Fetched via inv.armor list to be data-format agnostic.
        for (int i = 0; i < inv.armor.size(); i++) {
            ItemStack piece = inv.armor.get(i);
            if (piece.isEmpty()) continue;
            inv.armor.set(i, ItemStack.EMPTY);
            captive.drop(piece, true, false);
        }
        // Off-hand 40.
        for (int i = 0; i < inv.offhand.size(); i++) {
            ItemStack piece = inv.offhand.get(i);
            if (piece.isEmpty()) continue;
            inv.offhand.set(i, ItemStack.EMPTY);
            captive.drop(piece, true, false);
        }
        captive.inventoryMenu.broadcastChanges();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Ransom
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Result of a ransom attempt.
     */
    public sealed interface RansomResult {
        record Success(String passportId) implements RansomResult {}
        record Failure(String errorKey) implements RansomResult {}
    }

    /**
     * Returns a captured passport to its original owner.
     * <p>
     * Used by {@code RansomTradeMenu} or admin commands to release a prisoner.
     * Atomically clears the capture state in the database and updates player status.
     *
     * @param passportId the ID of the passport to return
     * @param fromUuid   the UUID of the player currently holding the trophy passport
     * @param toUuid     the UUID of the original passport owner (the prisoner)
     * @return a {@link RansomResult} indicating success or failure
     */
    public RansomResult ransom(String passportId, String fromUuid, String toUuid) {
        // Validate passport exists and is actually captured
        Optional<PassportsDao.Passport> passportOpt = database.inTx(conn ->
                passportsDao.findById(conn, passportId)
        );

        if (passportOpt.isEmpty()) {
            return new RansomResult.Failure("wp.captivity.error.passport_not_found");
        }

        PassportsDao.Passport passport = passportOpt.get();

        // Verify the passport is actually captured
        if (passport.capturedByUuid() == null || !passport.trophy()) {
            return new RansomResult.Failure("wp.captivity.error.not_captured");
        }

        // Verify the fromUuid matches the captor
        if (!fromUuid.equals(passport.capturedByUuid())) {
            return new RansomResult.Failure("wp.captivity.error.wrong_holder");
        }

        // Verify the toUuid matches the original owner
        if (!toUuid.equals(passport.ownerUuid())) {
            return new RansomResult.Failure("wp.captivity.error.wrong_owner");
        }

        // --- Atomic DB transaction ---
        database.transaction(conn -> {
            // Clear capture state on passport
            passportsDao.updateCaptureState(conn, passportId, null, false);

            // Clear captured flag on player
            playersDao.setCaptured(conn, toUuid, false);

            // Insert audit log entry
            auditLogDao.insert(conn,
                    System.currentTimeMillis(),
                    fromUuid,
                    null, // actor name resolved later if needed
                    toUuid,
                    null, // target name resolved later if needed
                    "RANSOM_COMPLETE",
                    null,
                    null
            );
        });

        LOGGER.info("Passport {} ransomed from {} to {}", passportId, fromUuid, toUuid);

        return new RansomResult.Success(passportId);
    }

    /**
     * Overload of ransom that works with online ServerPlayers, handling inventory transfer
     * and attachment updates.
     *
     * @param passportId the ID of the passport to return
     * @param from       the player currently holding the trophy passport
     * @param to         the original passport owner (the prisoner)
     * @return a {@link RansomResult} indicating success or failure
     */
    public RansomResult ransom(String passportId, ServerPlayer from, ServerPlayer to) {
        String fromUuid = from.getUUID().toString();
        String toUuid = to.getUUID().toString();

        // Perform the DB-level ransom
        RansomResult result = ransom(passportId, fromUuid, toUuid);
        if (!(result instanceof RansomResult.Success)) {
            return result;
        }

        // --- Move ItemStack from holder back to owner ---
        PassportSlotInfo trophyInfo = findPassportById(from, passportId);
        if (trophyInfo != null) {
            ItemStack passportStack = trophyInfo.stack().copy();

            // Update the PassportData to remove trophy flag
            PassportData currentData = passportStack.get(PassportComponentTypes.PASSPORT_DATA.get());
            if (currentData != null) {
                PassportData restoredData = new PassportData(
                        currentData.passportId(),
                        currentData.faction(),
                        currentData.rpName(),
                        currentData.rpSurname(),
                        currentData.dateOfBirth(),
                        currentData.signatureSeed(),
                        currentData.status(),
                        currentData.acceptedAt(),
                        currentData.acceptedBy(),
                        false // trophy = false
                );
                passportStack.set(PassportComponentTypes.PASSPORT_DATA.get(), restoredData);
            }

            // Remove from holder's inventory
            from.getInventory().setItem(trophyInfo.slot(), ItemStack.EMPTY);

            // Add to owner's inventory
            if (!to.getInventory().add(passportStack)) {
                placeInMainInventory(to, passportStack);
            }
        }

        // --- Update attachments on the freed player ---
        to.setData(WpAttachmentTypes.CAPTURED.get(), false);
        to.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.ACCEPTED);

        // Legacy mirror: see capture() above for rationale.
        com.frostlogic.warproject.server.WarPlayerProfile legacy =
                com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(to);
        legacy.setCaptive(false);
        legacy.setCapturedBy(null);
        com.frostlogic.warproject.server.WarPlayerDataStore.get().save();
        com.frostlogic.warproject.server.WarPrefixManager.refresh(to);

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Validation helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Checks if the initiator is within capture distance of the target.
     * Uses simple eye-position distance check (≤ 5 blocks).
     *
     * @param initiator the capturing player
     * @param target    the target player
     * @return true if within capture distance
     */
    public static boolean isWithinCaptureDistance(ServerPlayer initiator, ServerPlayer target) {
        Vec3 initiatorPos = initiator.getEyePosition();
        Vec3 targetPos = target.getEyePosition();
        double distanceSq = initiatorPos.distanceToSqr(targetPos);
        return distanceSq <= MAX_CAPTURE_DISTANCE * MAX_CAPTURE_DISTANCE;
    }

    /**
     * Evaluates the vulnerability predicate for the target player.
     * <p>
     * The predicate is configured via {@code cfg.captivity.vulnerability}.
     * Supported predicates:
     * <ul>
     *   <li>{@code HP_BELOW_HALF} — target's health is below 50% of max health</li>
     *   <li>{@code ALWAYS} — always vulnerable (for testing/admin purposes)</li>
     * </ul>
     *
     * @param target the target player to check vulnerability for
     * @return true if the target satisfies the vulnerability condition
     */
    public static boolean isVulnerable(ServerPlayer target) {
        String predicate = WpConfig.CAPTIVITY_VULNERABILITY.get();
        return switch (predicate.toUpperCase()) {
            case "HP_BELOW_HALF" -> target.getHealth() < (target.getMaxHealth() / 2.0f);
            case "HP_BELOW_QUARTER" -> target.getHealth() < (target.getMaxHealth() / 4.0f);
            case "ALWAYS" -> true;
            case "NEVER" -> false;
            default -> {
                LOGGER.warn("Unknown captivity vulnerability predicate: '{}', defaulting to HP_BELOW_HALF", predicate);
                yield target.getHealth() < (target.getMaxHealth() / 2.0f);
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Holds information about a passport found in a player's inventory.
     */
    private record PassportSlotInfo(int slot, ItemStack stack) {}

    /**
     * Finds the passport ItemStack in the target's inventory.
     *
     * @param player the player whose inventory to search
     * @return the slot info if found, or null if no passport exists
     */
    private static PassportSlotInfo findPassportInInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(PassportComponentTypes.PASSPORT_DATA.get())) {
                PassportData data = stack.get(PassportComponentTypes.PASSPORT_DATA.get());
                // Only consider non-trophy passports (the player's own passport)
                if (data != null && !data.trophy()) {
                    return new PassportSlotInfo(i, stack);
                }
            }
        }
        return null;
    }

    /**
     * Finds a passport with a specific ID in the player's inventory.
     *
     * @param player     the player whose inventory to search
     * @param passportId the passport ID to look for
     * @return the slot info if found, or null
     */
    private static PassportSlotInfo findPassportById(ServerPlayer player, String passportId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(PassportComponentTypes.PASSPORT_DATA.get())) {
                PassportData data = stack.get(PassportComponentTypes.PASSPORT_DATA.get());
                if (data != null && passportId.equals(data.passportId())) {
                    return new PassportSlotInfo(i, stack);
                }
            }
        }
        return null;
    }

    /**
     * Places an item in the main inventory area (slots 9..35).
     * Falls back to hotbar if main area is full.
     *
     * @param player the player to place the item for
     * @param stack  the item stack to place
     */
    private static void placeInMainInventory(ServerPlayer player, ItemStack stack) {
        for (int slot = 9; slot <= 35; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack);
                return;
            }
        }
        // Last resort: try hotbar
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack);
                return;
            }
        }
        // If absolutely no space, drop at player's feet (shouldn't happen normally)
        player.drop(stack, false);
    }
}
