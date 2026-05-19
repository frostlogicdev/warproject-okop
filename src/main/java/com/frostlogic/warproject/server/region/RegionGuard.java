package com.frostlogic.warproject.server.region;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Blocks world-modifying actions inside an opposing faction's base region:
 * <ul>
 *   <li>{@link BlockEvent.BreakEvent} — cannot mine blocks at all.</li>
 *   <li>{@link BlockEvent.EntityPlaceEvent} — cannot place blocks.</li>
 *   <li>{@link PlayerInteractEvent.RightClickBlock} on container block
 *       entities (chests, barrels, furnaces, supply crates, etc.) — cannot
 *       loot enemy storage.</li>
 * </ul>
 * <p>
 * Rules:
 * <ul>
 *   <li>OPs always pass.</li>
 *   <li>Action targets a position OUTSIDE any base region → allowed.</li>
 *   <li>Action targets a position inside a region of the actor's OWN faction
 *       → allowed.</li>
 *   <li>Action targets a position inside a region of an ENEMY faction (or the
 *       actor has no faction) → cancelled with a localized message.</li>
 * </ul>
 * <p>
 * The legacy {@link com.frostlogic.warproject.server.OnboardingGuard} already
 * blocks unauthorized players (not yet onboarded / candidate / factionless)
 * from breaking and placing blocks anywhere. This guard runs at HIGH
 * priority — strictly after OnboardingGuard's NORMAL handlers — so we don't
 * fire a "wrong faction" message to players who shouldn't have been allowed
 * to act in the first place.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class RegionGuard {

    private RegionGuard() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Block break / place
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;
        Vec3 at = Vec3.atCenterOf(event.getPos());
        if (deniedAt(player, at)) {
            event.setCanceled(true);
            notifyForbidden(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;
        Vec3 at = Vec3.atCenterOf(event.getPos());
        if (deniedAt(player, at)) {
            event.setCanceled(true);
            notifyForbidden(player);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Container access
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof BaseContainerBlockEntity)) {
            // Only gate true container BEs. Right-clicking buttons, doors,
            // levers etc. inside a base is fine — those are handled by
            // vanilla mechanics or future per-block gates.
            return;
        }
        Vec3 at = Vec3.atCenterOf(event.getPos());
        if (deniedAt(player, at)) {
            event.setCanceled(true);
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
            notifyForbidden(player);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean deniedAt(ServerPlayer player, Vec3 at) {
        BaseRegion region = RegionCacheHandler.regionService().regionAt(player.level().dimension(), at);
        if (region == null) return false; // Outside any base region — fine.
        FactionId regionFaction = region.faction();

        // Resolve player faction: prefer attachment (new DB pipeline), fall back to legacy profile.
        FactionId playerFactionId = resolveFactionId(player);
        if (playerFactionId == null) return true; // Factionless can't act inside any base region.
        return playerFactionId != regionFaction;
    }

    /**
     * Resolves the player's faction from attachments (new pipeline) first,
     * then falls back to the legacy WarPlayerProfile. Returns null if the
     * player has no playable faction.
     */
    private static FactionId resolveFactionId(ServerPlayer player) {
        // Try new-pipeline attachment first
        java.util.Optional<FactionId> attachmentFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (attachmentFaction != null && attachmentFaction.isPresent()) {
            return attachmentFaction.get();
        }
        // Fall back to legacy profile
        Faction legacyFaction = WarPlayerDataStore.get().getOrCreate(player).getFaction();
        return mapFaction(legacyFaction);
    }

    private static FactionId mapFaction(Faction f) {
        if (f == null || f == Faction.NONE) return null;
        return switch (f) {
            case ZARNAVIA -> FactionId.ZARNAVIA;
            case CHERNOGRYAD -> FactionId.CHERNOGRYAD;
            default -> null;
        };
    }

    private static void notifyForbidden(ServerPlayer player) {
        // Throttle: actionbar feels right for spammy break attempts. For the
        // first message we still send a system one so it's visible in chat
        // history; subsequent denies in the same second show only on the
        // actionbar to avoid wall-of-red.
        long now = System.currentTimeMillis();
        long last = LAST_NOTIFY_AT.getOrDefault(player.getUUID(), 0L);
        if (now - last > 3_000L) {
            player.sendSystemMessage(Component.literal("[WP] На вражеской базе запрещено действовать.")
                    .withStyle(ChatFormatting.RED));
            LAST_NOTIFY_AT.put(player.getUUID(), now);
        } else {
            player.displayClientMessage(Component.literal("На вражеской базе запрещено действовать.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> LAST_NOTIFY_AT =
            new java.util.concurrent.ConcurrentHashMap<>();
}
