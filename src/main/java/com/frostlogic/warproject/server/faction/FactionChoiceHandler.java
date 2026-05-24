package com.frostlogic.warproject.server.faction;

import com.frostlogic.warproject.ModDataComponents;
import com.frostlogic.warproject.ModItems;
import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.item.PassportData;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportSequenceDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.ServerEvents;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntity;
import com.frostlogic.warproject.server.passport.PassportGenerator;
import com.frostlogic.warproject.server.passport.PassportPlacement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Server-side handler for the C2S {@code FactionChoicePayload}.
 * <p>
 * Validates that the player is in the correct state to choose a faction and that
 * they are within interaction distance of the corresponding NPC. On success,
 * executes the full faction acceptance flow in a single DB transaction:
 * <ol>
 *   <li>Set faction and status to CANDIDATE in DB</li>
 *   <li>Generate passport (ID, DOB, signature)</li>
 *   <li>Insert passport record into DB</li>
 *   <li>Place passport item in player inventory</li>
 *   <li>Write audit log entry</li>
 *   <li>Teleport player to faction spawn</li>
 * </ol>
 * If passport placement fails (inventory full), the transaction is rolled back
 * and a separate audit entry is written for the rejection.
 * <p>
 * Requirements: 6.3, 6.4, 6.5, 12.1, 12.2
 * Design: §3, §8.2, §13.3
 */
public final class FactionChoiceHandler {

    /** Maximum distance (in blocks) from the NPC for a valid faction choice. */
    private static final double MAX_NPC_DISTANCE = 5.0;

    private static final PlayersDao playersDao = new PlayersDao();
    private static final PassportsDao passportsDao = new PassportsDao();
    private static final PassportSequenceDao sequenceDao = new PassportSequenceDao();
    private static final AuditLogDao auditLogDao = new AuditLogDao();
    private static final PassportGenerator passportGenerator = new PassportGenerator(passportsDao, sequenceDao);

    private FactionChoiceHandler() {
        // utility class — no instantiation
    }

    /**
     * Handles a faction choice request from a player.
     * <p>
     * Validation steps:
     * <ol>
     *   <li>Parse and validate the faction ID string</li>
     *   <li>Check that the player is in {@link PlayerState#FACTIONLESS} state</li>
     *   <li>Check that the player does not already have a faction</li>
     *   <li>Check distance to the nearest FactionNpcEntity of the chosen faction ≤ 5 blocks</li>
     * </ol>
     * <p>
     * On success, delegates to {@link #acceptFactionChoice(ServerPlayer, FactionId)}.
     *
     * @param player       the server player who sent the faction choice
     * @param factionIdStr the serialized name of the chosen faction
     */
    public static void handleChoice(ServerPlayer player, String factionIdStr) {
        // 1. Parse faction ID
        FactionId chosenFaction = parseFactionId(factionIdStr);
        if (chosenFaction == null) {
            player.sendSystemMessage(Component.translatable("wp.faction.invalid_faction"));
            WarProject.LOGGER.warn("[WarProject] Player {} sent invalid faction ID: {}",
                    player.getGameProfile().getName(), factionIdStr);
            return;
        }

        // 2. Check player state is FACTIONLESS — OPs are exempt so /wp NPC
        // interaction works for admin testing regardless of lifecycle state.
        boolean isOp = player.hasPermissions(2);
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (!isOp && state != PlayerState.FACTIONLESS) {
            player.sendSystemMessage(Component.translatable("wp.faction.wrong_state"));
            WarProject.LOGGER.debug("[WarProject] Player {} tried to choose faction in state {}",
                    player.getGameProfile().getName(), state.getSerializedName());
            return;
        }

        // 3. Check player doesn't already have a faction — also exempt for OPs.
        Optional<FactionId> existingFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (!isOp && existingFaction.isPresent()) {
            player.sendSystemMessage(Component.translatable("wp.faction.already_chosen"));
            WarProject.LOGGER.debug("[WarProject] Player {} already has faction {}",
                    player.getGameProfile().getName(), existingFaction.get().getSerializedName());
            return;
        }

        // 4. Check distance to NPC of the chosen faction ≤ 5 blocks
        if (!isNearFactionNpc(player, chosenFaction)) {
            player.sendSystemMessage(Component.translatable("wp.faction.too_far_from_npc"));
            WarProject.LOGGER.debug("[WarProject] Player {} is too far from {} NPC",
                    player.getGameProfile().getName(), chosenFaction.getSerializedName());
            return;
        }

        // All validations passed — accept the faction choice
        acceptFactionChoice(player, chosenFaction);
    }

    /**
     * Accepts the faction choice for the player with full transactional flow.
     * <p>
     * In a single DB transaction:
     * <ol>
     *   <li>{@code PlayersDao.setFactionAndStatus(uuid, faction, CANDIDATE)}</li>
     *   <li>{@code PassportGenerator.generate(...)} + {@code PassportsDao.insert}</li>
     *   <li>{@code placePassport(player, stack)} — if Err → ROLLBACK</li>
     *   <li>{@code audit_log.insert(CHOOSE_FACTION)}</li>
     * </ol>
     * Post-commit: update attachments, teleport to faction spawn.
     * <p>
     * If placePassport fails, the transaction is rolled back and a separate
     * audit entry {@code CHOOSE_FACTION_REJECTED_INVENTORY} is written.
     *
     * @param player  the player choosing the faction
     * @param faction the chosen faction
     */
    private static void acceptFactionChoice(ServerPlayer player, FactionId faction) {
        Database db = ServerEvents.getDatabase();
        if (db == null) {
            WarProject.LOGGER.error("[WarProject] Database not available during faction choice for {}",
                    player.getGameProfile().getName());
            player.sendSystemMessage(Component.translatable("wp.error.internal"));
            return;
        }

        String uuid = player.getStringUUID();
        String playerName = player.getGameProfile().getName();

        // Resolve RP name from attachment
        Optional<com.frostlogic.warproject.attachment.RpName> rpNameOpt =
                player.getData(WpAttachmentTypes.RP_NAME.get());
        String rpName = rpNameOpt.map(com.frostlogic.warproject.attachment.RpName::name).orElse(playerName);
        String rpSurname = rpNameOpt.map(com.frostlogic.warproject.attachment.RpName::surname).orElse("");

        // Attempt the transactional flow
        try {
            PassportsDao.Passport[] generatedPassport = new PassportsDao.Passport[1];

            db.transaction(conn -> {
                // 1. Set faction and status in DB
                playersDao.setFactionAndStatus(conn, uuid,
                        faction.getSerializedName().toUpperCase(),
                        PlayerState.CANDIDATE.getSerializedName());

                // 2. Generate passport
                generatedPassport[0] = passportGenerator.generate(conn, uuid, faction, rpName, rpSurname);

                // 3. Insert passport into DB
                passportsDao.insert(conn, generatedPassport[0]);

                // 4. Create passport ItemStack and attempt placement
                ItemStack passportStack = createPassportStack(generatedPassport[0]);
                PlaceResult result = placePassport(player, passportStack);

                if (result == PlaceResult.ERR_INVENTORY_FULL) {
                    // Rollback by throwing — the transaction wrapper will catch and rollback
                    throw new InventoryFullException();
                }

                // 5. Write audit log
                auditLogDao.insert(conn,
                        System.currentTimeMillis(),
                        uuid,
                        playerName,
                        uuid,
                        playerName,
                        "CHOOSE_FACTION",
                        null,
                        "{\"faction\":\"" + faction.getSerializedName() + "\","
                                + "\"passport_id\":\"" + generatedPassport[0].passportId() + "\"}"
                );
            });

            // Transaction committed successfully — update attachments and teleport
            player.setData(WpAttachmentTypes.FACTION.get(), Optional.of(faction));
            player.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.CANDIDATE);

            // Note: the Military ID card is intentionally NOT issued here.
            // Per Requirement 4.1, it is issued only when the player transitions
            // from CANDIDATE to ACCEPTED — i.e. inside AcceptCommandHandler. At
            // this stage the player only receives the passport.

            // Notify the player
            player.sendSystemMessage(Component.translatable("wp.faction.chosen",
                    Component.translatable(faction.displayNameKey())));

            // Teleport to faction spawn
            teleportToFactionSpawn(player, faction);

            WarProject.LOGGER.info("[WarProject] Player {} chose faction {} (passport: {})",
                    playerName, faction.getSerializedName(),
                    generatedPassport[0] != null ? generatedPassport[0].passportId() : "?");

        } catch (InventoryFullException e) {
            // Transaction was rolled back — write rejection audit in a separate transaction
            handleInventoryFullRejection(db, uuid, playerName, faction);
        } catch (Exception e) {
            WarProject.LOGGER.error("[WarProject] Failed to process faction choice for {}: {}",
                    playerName, e.getMessage(), e);
            player.sendSystemMessage(Component.translatable("wp.error.internal"));
        }
    }

    /**
     * Handles the case where passport placement failed due to full inventory.
     * Writes a rejection audit entry in a separate transaction and notifies the player.
     */
    private static void handleInventoryFullRejection(Database db, String uuid, String playerName, FactionId faction) {
        try {
            db.transaction(conn -> auditLogDao.insert(conn,
                    System.currentTimeMillis(),
                    uuid,
                    playerName,
                    uuid,
                    playerName,
                    "CHOOSE_FACTION_REJECTED_INVENTORY",
                    "inventory_full",
                    "{\"faction\":\"" + faction.getSerializedName() + "\"}"
            ));
        } catch (Exception auditEx) {
            WarProject.LOGGER.error("[WarProject] Failed to write rejection audit for {}: {}",
                    playerName, auditEx.getMessage());
        }

        // Player remains FACTIONLESS — no attachment changes
        WarProject.LOGGER.warn("[WarProject] Faction choice rejected for {} — inventory full", playerName);
    }

    /**
     * Creates a passport {@link ItemStack} with the appropriate data component.
     * <p>
     * Uses the existing {@code ModItems.PASSPORT} item and sets the legacy
     * {@code PassportData} component for tooltip/display compatibility.
     * The full passport data component (task 11.1) will replace this.
     *
     * @param passport the generated passport record
     * @return an ItemStack representing the passport
     */
    private static ItemStack createPassportStack(PassportsDao.Passport passport) {
        ItemStack stack = new ItemStack(ModItems.PASSPORT.get());
        // Set the existing PassportData component for display compatibility
        PassportData data = new PassportData(
                passport.ownerUuid(),
                passport.rpName() + " " + passport.rpSurname(),
                computeAge(passport.dateOfBirth()),
                passport.faction().equalsIgnoreCase("ZARNAVIA") ? "zarnavia" : "chernogryad",
                passport.faction().toLowerCase(),
                "",  // rankId — not assigned yet for CANDIDATE
                ""   // subdivision — not assigned yet
        );
        stack.set(ModDataComponents.PASSPORT.get(), data);
        return stack;
    }

    /**
     * Computes approximate age from a date of birth string in dd.MM.yyyy format.
     * <p>
     * Defensive: tolerates null, blank, malformed, or future-year inputs by
     * returning a reasonable fallback age. The DOB string is generated by
     * {@code PassportGenerator} and should always be well-formed, but we don't
     * want a passport with a typo'd DOB to crash the faction-choice transaction.
     */
    private static int computeAge(String dob) {
        if (dob == null || dob.isBlank()) {
            return 25;
        }
        String[] parts = dob.split("\\.");
        if (parts.length < 3) {
            return 25;
        }
        try {
            int year = Integer.parseInt(parts[2].trim());
            int currentYear = java.time.LocalDate.now().getYear();
            int age = currentYear - year;
            // Clamp to a sane band — a passport with year=1000 or year=3000
            // shouldn't produce an age of 1026 / -974.
            if (age < 0 || age > 120) {
                return 25;
            }
            return age;
        } catch (NumberFormatException e) {
            return 25;
        }
    }

    /**
     * Places the passport in the player's inventory using the full algorithm from §8.2.
     * <p>
     * Delegates to {@link PassportPlacement#place(ServerPlayer, ItemStack)}.
     *
     * @param player the player receiving the passport
     * @param stack  the passport ItemStack
     * @return Ok if placed successfully, ERR_INVENTORY_FULL if no space
     */
    private static PlaceResult placePassport(ServerPlayer player, ItemStack stack) {
        PassportPlacement.PlaceResult result = PassportPlacement.place(player, stack);
        if (result instanceof PassportPlacement.PlaceResult.Ok) {
            return PlaceResult.OK;
        }
        return PlaceResult.ERR_INVENTORY_FULL;
    }

    /**
     * Teleports the player to the spawn point of their chosen faction.
     */
    private static void teleportToFactionSpawn(ServerPlayer player, FactionId faction) {
        List<? extends Integer> spawnCoords = switch (faction) {
            case ZARNAVIA -> WpConfig.FACTIONS_ZARNAVIA_SPAWN.get();
            case CHERNOGRYAD -> WpConfig.FACTIONS_CHERNOGRYAD_SPAWN.get();
        };

        if (spawnCoords.size() >= 3) {
            double x = spawnCoords.get(0) + 0.5;
            double y = spawnCoords.get(1);
            double z = spawnCoords.get(2) + 0.5;
            // Faction bases are in the overworld — at the time of faction choice
            // the player is typically standing inside multiworld:choicehall, so we
            // must resolve the overworld dimension explicitly before teleporting.
            MinecraftServer server = player.getServer();
            ServerLevel targetLevel = server != null ? server.getLevel(Level.OVERWORLD) : null;
            if (targetLevel == null) {
                targetLevel = player.serverLevel();
            }
            player.teleportTo(targetLevel, x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot());
            WarProject.LOGGER.debug("[WarProject] Teleported {} to faction spawn ({}, {}, {})",
                    player.getGameProfile().getName(), x, y, z);
        } else {
            WarProject.LOGGER.warn("[WarProject] Faction spawn coordinates not configured for {}",
                    faction.getSerializedName());
        }
    }

    /**
     * Checks whether the player is within {@link #MAX_NPC_DISTANCE} blocks of a
     * {@link FactionNpcEntity} that represents the specified faction.
     *
     * @param player  the player to check
     * @param faction the faction whose NPC we're looking for
     * @return true if a matching NPC is within range
     */
    private static boolean isNearFactionNpc(ServerPlayer player, FactionId faction) {
        Vec3 playerPos = player.position();
        AABB searchBox = player.getBoundingBox().inflate(MAX_NPC_DISTANCE);

        List<FactionNpcEntity> nearbyNpcs = player.serverLevel().getEntitiesOfClass(
                FactionNpcEntity.class,
                searchBox,
                npc -> npc.getFactionId() == faction
        );

        for (FactionNpcEntity npc : nearbyNpcs) {
            double distance = playerPos.distanceTo(npc.position());
            if (distance <= MAX_NPC_DISTANCE) {
                return true;
            }
        }

        return false;
    }

    /**
     * Parses a faction ID string into a {@link FactionId} enum value.
     *
     * @param factionIdStr the serialized name (e.g. "zarnavia" or "chernogryad")
     * @return the matching FactionId, or null if invalid
     */
    private static FactionId parseFactionId(String factionIdStr) {
        if (factionIdStr == null || factionIdStr.isEmpty()) {
            return null;
        }
        for (FactionId id : FactionId.values()) {
            if (id.getSerializedName().equals(factionIdStr)) {
                return id;
            }
        }
        return null;
    }

    // --- Internal types ---

    /** Result of passport placement attempt. */
    private enum PlaceResult {
        OK,
        ERR_INVENTORY_FULL
    }

    /**
     * Sentinel exception used to trigger transaction rollback when inventory is full.
     * Not a real error — caught by the handler to execute the rejection flow.
     */
    private static final class InventoryFullException extends RuntimeException {
        InventoryFullException() {
            super("Inventory full — passport placement failed");
        }
    }
}
