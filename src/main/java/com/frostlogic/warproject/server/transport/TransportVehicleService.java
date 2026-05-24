package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the rank → vehicle access matrix from {@link WpConfig}.
 * <p>
 * The matrix is configured via {@link WpConfig#TRANSPORT_VEHICLES_ZARNAVIA} and
 * {@link WpConfig#TRANSPORT_VEHICLES_CHERNOGRYAD}. Each list element has the
 * format:
 * <pre>
 *   "min_rank_id|item_id|display_translation_key"
 * </pre>
 * Example:
 * <pre>
 *   private|iv:tiger_2|wp.transport.vehicle.tiger_2
 *   sergeant|iv:btr_80a|wp.transport.vehicle.btr_80a
 * </pre>
 * The pipe separator was chosen because vehicle item IDs themselves can contain
 * colons (namespace:path) which makes a colon-separated tuple ambiguous to
 * parse.
 * <p>
 * The service is intentionally stateless — every query re-reads the config so
 * that {@code /wp reload} (which re-reads the TOML) takes effect without a
 * restart. The per-call cost is negligible since the lists are tiny (typically
 * &lt; 20 entries per faction).
 */
public final class TransportVehicleService {

    private TransportVehicleService() {
    }

    /**
     * Returns the catalog of all vehicles configured for the given faction,
     * regardless of rank. Used by the GUI to show locked/unlocked entries.
     */
    public static List<TransportVehicle> catalog(FactionId faction) {
        return parseEntries(faction);
    }

    /**
     * Returns the catalog of vehicles the player can actually claim right now.
     * <p>
     * Filters by:
     * <ul>
     *   <li>Player state — must be {@link PlayerState#ACCEPTED}. Other states
     *       (CANDIDATE, CAPTURED, etc.) get an empty list and the NPC head-shakes.</li>
     *   <li>Faction — must match the NPC's faction.</li>
     *   <li>Rank weight — vehicle's {@code minRank.weight()} ≤ player's rank weight.</li>
     * </ul>
     * OPs (permission level ≥ 2) bypass all gates and see the full catalog.
     */
    public static List<TransportVehicle> availableTo(ServerPlayer player, FactionId npcFaction) {
        // OPs always get the full catalog for admin testing.
        if (player.hasPermissions(2)) {
            return catalog(npcFaction);
        }

        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED) {
            return List.of();
        }

        Optional<FactionId> playerFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (playerFaction.isEmpty() || playerFaction.get() != npcFaction) {
            return List.of();
        }

        Rank rank = resolveRank(player);
        int playerWeight = rank.weight();

        List<TransportVehicle> all = catalog(npcFaction);
        List<TransportVehicle> allowed = new ArrayList<>(all.size());
        for (TransportVehicle v : all) {
            if (v.minRank().weight() <= playerWeight) {
                allowed.add(v);
            }
        }
        return allowed;
    }

    /** Resolves the player's rank from the {@code rank} attachment string. */
    public static Rank resolveRank(ServerPlayer player) {
        String raw = player.getData(WpAttachmentTypes.RANK.get());
        if (raw == null || raw.isBlank()) {
            return Rank.NONE;
        }
        return Rank.fromInput(raw).orElse(Rank.NONE);
    }

    /**
     * Returns true if the player is allowed to receive the given vehicle right
     * now. Used by {@link TransportChoiceHandler} to validate the C2S request
     * — the GUI is authoritative for display but the server must re-check
     * (the client can craft a forged payload).
     */
    public static boolean canClaim(ServerPlayer player, FactionId npcFaction, ResourceLocation itemId) {
        for (TransportVehicle v : availableTo(player, npcFaction)) {
            if (v.itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Logs a warning for every configured vehicle entry whose item id does
     * not resolve to a real registered item (i.e. resolves to AIR, which the
     * runtime then displays as a BARRIER icon and would otherwise hand to
     * the player). Called once on {@code ServerStartedEvent} so admins see
     * stale wp-server.toml entries at startup instead of discovering them
     * by getting a barrier in their inventory.
     */
    public static void auditCatalog() {
        int bad = 0;
        for (FactionId faction : FactionId.values()) {
            for (TransportVehicle v : catalog(faction)) {
                if (BuiltInRegistries.ITEM.get(v.itemId()) == Items.AIR) {
                    WarProject.LOGGER.warn(
                            "[WP Transport] Configured vehicle '{}' for faction {} is NOT a registered "
                            + "item — players will see a barrier icon in the menu and the server will "
                            + "refuse to grant the item. Edit config/wp-server.toml.",
                            v.itemId(), faction.getSerializedName());
                    bad++;
                }
            }
        }
        if (bad == 0) {
            WarProject.LOGGER.info("[WP Transport] Vehicle catalog audit OK — all configured entries resolve.");
        } else {
            WarProject.LOGGER.warn("[WP Transport] Vehicle catalog audit found {} stale entries in wp-server.toml.", bad);
        }
    }

    /** Looks up the configured entry for a given vehicle id (without rank filtering). */
    public static Optional<TransportVehicle> findByItemId(FactionId faction, ResourceLocation itemId) {
        for (TransportVehicle v : catalog(faction)) {
            if (v.itemId().equals(itemId)) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    // ─── internal: TOML parsing ──────────────────────────────────────────────

    private static List<TransportVehicle> parseEntries(FactionId faction) {
        List<? extends String> raw = switch (faction) {
            case ZARNAVIA -> WpConfig.TRANSPORT_VEHICLES_ZARNAVIA.get();
            case CHERNOGRYAD -> WpConfig.TRANSPORT_VEHICLES_CHERNOGRYAD.get();
        };
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        Map<Rank, Integer> rankByLower = new EnumMap<>(Rank.class);
        for (Rank r : Rank.values()) {
            rankByLower.put(r, r.weight());
        }

        List<TransportVehicle> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            TransportVehicle entry = parseLine(faction, line);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static TransportVehicle parseLine(FactionId faction, String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 3) {
            WarProject.LOGGER.warn("[WP Transport] Bad config entry (expected 'rank|item_id|display_key'): {}", line);
            return null;
        }
        String rankId = parts[0].trim().toLowerCase(Locale.ROOT);
        String itemIdRaw = parts[1].trim();
        String displayKey = parts[2].trim();

        if (rankId.isEmpty() || itemIdRaw.isEmpty() || displayKey.isEmpty()) {
            WarProject.LOGGER.warn("[WP Transport] Empty field in config entry: {}", line);
            return null;
        }

        Optional<Rank> rank = Rank.fromInput(rankId);
        if (rank.isEmpty()) {
            WarProject.LOGGER.warn("[WP Transport] Unknown rank '{}' in config entry: {}", rankId, line);
            return null;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(itemIdRaw);
        if (itemId == null) {
            WarProject.LOGGER.warn("[WP Transport] Invalid item id '{}' in config entry: {}", itemIdRaw, line);
            return null;
        }

        return new TransportVehicle(itemId, displayKey, rank.get(), faction);
    }
}
