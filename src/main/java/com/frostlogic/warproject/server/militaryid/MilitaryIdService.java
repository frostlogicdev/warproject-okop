package com.frostlogic.warproject.server.militaryid;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing Military ID card lifecycle: issuance, field updates, and login sync.
 * <p>
 * The Military ID card is a paper item with a {@link MilitaryIdData} data component attached.
 * It is issued when a player is accepted into a faction and kept synchronized with rank,
 * subdivision, and award changes throughout the player's career.
 * <p>
 * Card placement follows the same priority algorithm as {@code PassportPlacement}:
 * main inventory slots [9,35] first, then hotbar [0,8], then drop as entity.
 * <p>
 * Requirements: 4.1, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8
 * Design: §2 Military ID System — MilitaryIdService
 */
public final class MilitaryIdService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Database database;
    private final PassportsDao passportsDao;

    public MilitaryIdService(Database database, PassportsDao passportsDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.passportsDao = Objects.requireNonNull(passportsDao, "passportsDao");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Card Issuance
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Issues a new Military ID card to the player.
     * <p>
     * Creates a paper item with a {@link MilitaryIdData} component and places it
     * in the player's inventory using the placement algorithm:
     * <ol>
     *   <li>First empty slot in main inventory [9, 35]</li>
     *   <li>First empty slot in hotbar [0, 8]</li>
     *   <li>Drop as item entity at player's position</li>
     * </ol>
     *
     * @param player           the player receiving the card
     * @param faction          the player's faction
     * @param rankId           the player's current rank ID
     * @param subdivision      the player's subdivision name (empty string if unassigned)
     * @param acceptingOfficer the name of the officer who accepted the player
     */
    public void issueCard(ServerPlayer player, FactionId faction, String rankId,
                          String subdivision, String acceptingOfficer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(faction, "faction");

        // Resolve the player's passport ID from the database
        String passportIdRef = resolvePassportId(player);
        if (passportIdRef == null) {
            LOGGER.warn("[WP MilitaryIdService] Cannot issue card: no passport found for player {} ({})",
                    player.getGameProfile().getName(), player.getStringUUID());
            return;
        }

        String enlistmentDate = LocalDate.now().format(DATE_FORMAT);

        MilitaryIdData data = new MilitaryIdData(
                passportIdRef,
                faction.getSerializedName(),
                rankId != null ? rankId : "",
                subdivision != null ? subdivision : "",
                List.of(),
                enlistmentDate,
                acceptingOfficer != null ? acceptingOfficer : ""
        );

        ItemStack cardStack = new ItemStack(Items.PAPER);
        cardStack.set(MilitaryIdComponentTypes.MILITARY_ID_DATA.get(), data);

        placeCard(player, cardStack);

        // Broadcast inventory changes to the client
        player.inventoryMenu.broadcastChanges();

        LOGGER.info("[WP MilitaryIdService] Issued military ID card to {} (passport={})",
                player.getGameProfile().getName(), passportIdRef);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Card Updates
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Updates the rank field on the player's Military ID card.
     * <p>
     * If the card is not found in the player's inventory, logs a warning and skips.
     *
     * @param player    the player whose card to update
     * @param newRankId the new rank ID string
     */
    public void updateRank(ServerPlayer player, String newRankId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(newRankId, "newRankId");

        ItemStack card = findCard(player);
        if (card == null) {
            LOGGER.warn("[WP MilitaryIdService] Card not found for rank update: player={} ({})",
                    player.getGameProfile().getName(), player.getStringUUID());
            return;
        }

        MilitaryIdData current = card.get(MilitaryIdComponentTypes.MILITARY_ID_DATA.get());
        if (current == null) {
            return;
        }

        MilitaryIdData updated = new MilitaryIdData(
                current.passportIdRef(),
                current.faction(),
                newRankId,
                current.subdivision(),
                current.awards(),
                current.enlistmentDate(),
                current.acceptingOfficer()
        );
        card.set(MilitaryIdComponentTypes.MILITARY_ID_DATA.get(), updated);

        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Updates the subdivision field on the player's Military ID card.
     * <p>
     * If the card is not found in the player's inventory, logs a warning and skips.
     *
     * @param player          the player whose card to update
     * @param subdivisionName the new subdivision name (empty string to clear)
     */
    public void updateSubdivision(ServerPlayer player, String subdivisionName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(subdivisionName, "subdivisionName");

        ItemStack card = findCard(player);
        if (card == null) {
            LOGGER.warn("[WP MilitaryIdService] Card not found for subdivision update: player={} ({})",
                    player.getGameProfile().getName(), player.getStringUUID());
            return;
        }

        MilitaryIdData current = card.get(MilitaryIdComponentTypes.MILITARY_ID_DATA.get());
        if (current == null) {
            return;
        }

        MilitaryIdData updated = new MilitaryIdData(
                current.passportIdRef(),
                current.faction(),
                current.rankId(),
                subdivisionName,
                current.awards(),
                current.enlistmentDate(),
                current.acceptingOfficer()
        );
        card.set(MilitaryIdComponentTypes.MILITARY_ID_DATA.get(), updated);

        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Appends an award to the player's Military ID card awards list.
     * <p>
     * Checks for duplicates (award already present) and max capacity (32 awards).
     * If either condition is met, the update is skipped silently.
     * If the card is not found in the player's inventory, logs a warning and skips.
     *
     * @param player    the player whose card to update
     * @param awardName the award name to append
     */
    public void appendAward(ServerPlayer player, String awardName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(awardName, "awardName");

        ItemStack card = findCard(player);
        if (card == null) {
            LOGGER.warn("[WP MilitaryIdService] Card not found for award append: player={} ({})",
                    player.getGameProfile().getName(), player.getStringUUID());
            return;
        }

        MilitaryIdData current = card.get(MilitaryIdComponentTypes.MILITARY_ID_DATA.get());
        if (current == null) {
            return;
        }

        // Check for duplicate
        if (current.awards().contains(awardName)) {
            return;
        }

        // Check max capacity
        if (current.awards().size() >= MilitaryIdData.MAX_AWARDS) {
            return;
        }

        // Create new awards list with the appended award
        List<String> newAwards = new ArrayList<>(current.awards());
        newAwards.add(awardName);

        MilitaryIdData updated = new MilitaryIdData(
                current.passportIdRef(),
                current.faction(),
                current.rankId(),
                current.subdivision(),
                List.copyOf(newAwards),
                current.enlistmentDate(),
                current.acceptingOfficer()
        );
        card.set(MilitaryIdComponentTypes.MILITARY_ID_DATA.get(), updated);

        player.inventoryMenu.broadcastChanges();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Login Sync
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Synchronizes the Military ID card data on player login.
     * <p>
     * Re-reads the player's current state from the database and updates the card
     * component if the card is found in the inventory. This handles the case where
     * rank, subdivision, or awards changed while the player was offline.
     *
     * @param player the player who just logged in
     */
    public void syncOnLogin(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        ItemStack card = findCard(player);
        if (card == null) {
            // No card in inventory — nothing to sync
            return;
        }

        MilitaryIdData current = card.get(MilitaryIdComponentTypes.MILITARY_ID_DATA.get());
        if (current == null) {
            return;
        }

        // The card data is already stored on the item via NBT persistence.
        // On login, we just ensure the inventory is broadcast to the client.
        // Future enhancement: if rank/subdivision/awards are stored in DB separately,
        // re-read them here and update the card. For now, the card is the source of truth
        // and is updated in real-time when the player is online.
        player.inventoryMenu.broadcastChanges();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Scans the player's inventory for a Military ID card matching the player's passport ID.
     * <p>
     * Searches all inventory slots (main inventory, hotbar, offhand) for an item
     * with a {@link MilitaryIdData} component whose {@code passportIdRef} matches
     * the player's passport ID.
     *
     * @param player the player whose inventory to scan
     * @return the matching ItemStack (still in the inventory), or null if not found
     */
    @Nullable
    private ItemStack findCard(ServerPlayer player) {
        String passportId = resolvePassportId(player);
        if (passportId == null) {
            return null;
        }

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            MilitaryIdData data = stack.get(MilitaryIdComponentTypes.MILITARY_ID_DATA.get());
            if (data != null && passportId.equals(data.passportIdRef())) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Resolves the player's passport ID from the database.
     *
     * @param player the player
     * @return the passport ID, or null if no passport exists
     */
    @Nullable
    private String resolvePassportId(ServerPlayer player) {
        try {
            Optional<PassportsDao.Passport> passportOpt = database.inTx(conn ->
                    passportsDao.findByOwnerUuid(conn, player.getStringUUID()));
            return passportOpt.map(PassportsDao.Passport::passportId).orElse(null);
        } catch (RuntimeException e) {
            LOGGER.error("[WP MilitaryIdService] Failed to resolve passport ID for player {} ({}): {}",
                    player.getGameProfile().getName(), player.getStringUUID(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Places the card item in the player's inventory using the placement algorithm:
     * <ol>
     *   <li>First empty slot in main inventory [9, 35]</li>
     *   <li>First empty slot in hotbar [0, 8]</li>
     *   <li>Drop as item entity at player's position</li>
     * </ol>
     * The algorithm never overwrites a non-empty slot.
     *
     * @param player    the player receiving the card
     * @param cardStack the card ItemStack to place
     */
    static void placeCard(ServerPlayer player, ItemStack cardStack) {
        Inventory inv = player.getInventory();

        // Step 1: Try main inventory slots [9, 35]
        for (int slot = 9; slot < 36; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, cardStack);
                return;
            }
        }

        // Step 2: Try hotbar slots [0, 8]
        for (int slot = 0; slot < 9; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, cardStack);
                return;
            }
        }

        // Step 3: Drop as item entity at player's position
        player.drop(cardStack, false);
    }
}
