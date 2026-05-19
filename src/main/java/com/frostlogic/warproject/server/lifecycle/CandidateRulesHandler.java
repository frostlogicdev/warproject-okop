package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.region.BaseRegion;
import com.frostlogic.warproject.server.region.RegionCacheHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces all server-side restrictions on players in {@link PlayerState#CANDIDATE}.
 * <p>
 * Subscriptions:
 * <ul>
 *   <li>{@link LivingIncomingDamageEvent} — cancels PvP damage to/from any candidate (Req. 7.4)</li>
 *   <li>{@link PlayerInteractEvent.RightClickBlock} — blocks opening of any container
 *       (chest, barrel, supply crate, faction warehouse, etc.) for candidates (Req. 7.5)</li>
 *   <li>{@link ServerChatEvent} — placeholder for proximity-only chat filtering (Req. 7.2);
 *       the full chat-scope filter is implemented in task 16.1, so for now candidates'
 *       messages pass through unchanged (vanilla broadcast is treated as the local channel
 *       until {@code GeneralChatService} arrives)</li>
 *   <li>{@link PlayerTickEvent.Post}:
 *     <ul>
 *       <li>verifies the candidate is inside their own faction's base region every
 *           {@code cfg.regions.tickIntervalTicks}; otherwise teleports them back to
 *           the configured base spawn and sends the localized message
 *           {@code wp.candidate.cannot_leave_base} (Req. 7.3)</li>
 *       <li>broadcasts the action-bar reminder {@code wp.candidate.actionbar} every
 *           {@code cfg.candidate.actionbarIntervalTicks ± 20} ticks (Req. 7.7)</li>
 *     </ul>
 *   </li>
 *   <li>{@link PlayerEvent.PlayerLoggedOutEvent} — cleans up per-player jitter state</li>
 * </ul>
 * <p>
 * <strong>Note on military-item denial.</strong> Task 14.2 also references blocking
 * candidates from picking / crafting items tagged as {@code MilitaryItemTag}. The tag
 * itself is introduced together with faction equipment in a later task, and there is
 * no such item tag in the codebase yet. Because candidates are physically confined
 * to their own base by the region check below — and the base spawn does not give
 * access to crafting stations — the equipment-denial concern is currently
 * defended-in-depth by the container/region rules. A dedicated handler will be
 * wired in when {@code MilitaryItemTag} lands.
 * <p>
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7
 * Design: §3, §6
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CandidateRulesHandler {

    /**
     * Per-player tick at which the next action-bar reminder is due.
     * Stored as the value of {@link ServerPlayer#tickCount} when the next reminder
     * should fire, computed as {@code currentTick + (interval ± [0,40))} so that
     * reminders are jittered by ±20 ticks (Req. 7.7).
     */
    private static final Map<UUID, Integer> NEXT_ACTIONBAR_TICK = new ConcurrentHashMap<>();

    /**
     * Per-player tick at which the next region-leave teleport message may be sent.
     * Used to throttle the {@code wp.candidate.cannot_leave_base} message so that
     * a candidate who is mid-air on the boundary does not get spammed several
     * times per second.
     */
    private static final Map<UUID, Integer> NEXT_REGION_MESSAGE_TICK = new ConcurrentHashMap<>();

    /** Minimum number of ticks between successive {@code cannot_leave_base} messages. */
    private static final int REGION_MESSAGE_COOLDOWN_TICKS = 40;

    /** Jitter window in ticks applied to the action-bar interval ({@code ±20}). */
    private static final int ACTIONBAR_JITTER_TICKS = 20;

    /** Lower bound for the action-bar interval in ticks (1 second) — defensive. */
    private static final int ACTIONBAR_MIN_INTERVAL_TICKS = 20;

    private static final Random RANDOM = new Random();

    private CandidateRulesHandler() {
        // static event subscriber — no instantiation
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Damage (Req. 7.4)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancels PvP damage where either the attacker or the victim is a CANDIDATE.
     * <p>
     * Non-PvP damage (mobs, environment, fall damage, etc.) is unaffected. The
     * attacker is determined via {@link DamageSource#getEntity()}, which resolves
     * to the actual player even for indirect sources like arrows or thrown
     * tridents.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer attackerPlayer)) {
            // Not a PvP source (mob/environment/projectile-without-owner); ignore.
            return;
        }
        if (attackerPlayer == victim) {
            // Self-damage isn't PvP; let it through (e.g. fire on self).
            return;
        }
        if (isCandidate(victim) || isCandidate(attackerPlayer)) {
            event.setCanceled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Container interaction (Req. 7.5)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blocks candidates from opening any container block (chests, barrels, supply
     * crates, faction warehouses, etc.) by cancelling the right-click interaction
     * before the menu can be opened.
     * <p>
     * The check uses the {@link Container} interface as a broad signal that the
     * targeted block exposes an inventory; this covers vanilla containers and
     * any modded container that follows the standard NeoForge pattern.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isCandidate(player)) {
            return;
        }
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof Container) {
            event.setCanceled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat (Req. 7.2) — placeholder until task 16.1 introduces chat scopes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Placeholder handler for candidate chat filtering.
     * <p>
     * The full proximity-only restriction depends on the chat-scope concept
     * implemented in task 16.1 ({@code GeneralChatService}, {@code ChatScopeFilter}).
     * Until then, vanilla {@link ServerChatEvent} is treated as the local channel
     * and is allowed to pass through for candidates as well; commander/general
     * channels do not exist yet.
     * <p>
     * The empty subscription is kept here so that the task wiring is in place and
     * the future scope filter has a well-known integration point.
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        // No-op: see Javadoc. Implementation expanded in task 16.1.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-tick rules: region containment + action-bar reminder (Req. 7.3, 7.7)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Per-tick rules for candidates: region containment and action-bar reminders.
     * <p>
     * To minimise overhead the region check piggy-backs on the same cadence used
     * by {@link RegionCacheHandler} (default {@code cfg.regions.tickIntervalTicks}
     * = 10 ticks), which is also the cadence at which the cached
     * {@link WpAttachmentTypes#REGION} attachment is refreshed.
     * <p>
     * The action-bar reminder is independent and uses its own per-player schedule
     * with ±20-tick jitter so that reminders for many candidates do not all land
     * on the same tick.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isCandidate(player)) {
            return;
        }

        int regionInterval = WpConfig.REGIONS_TICK_INTERVAL_TICKS.get();
        if (regionInterval <= 0) {
            regionInterval = 10;
        }
        if (player.tickCount % regionInterval == 0) {
            enforceBaseRegion(player);
        }

        maybeShowActionbar(player);
    }

    /**
     * Removes any cached per-player jitter state on logout to prevent leaks.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            NEXT_ACTIONBAR_TICK.remove(id);
            NEXT_REGION_MESSAGE_TICK.remove(id);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the given player has lifecycle state
     * {@link PlayerState#CANDIDATE}.
     */
    private static boolean isCandidate(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        PlayerState state = serverPlayer.getData(WpAttachmentTypes.PLAYER_STATE.get());
        return state == PlayerState.CANDIDATE;
    }

    /**
     * Verifies that the candidate is inside their own faction's base region.
     * If they are not, teleports them back to the configured faction spawn and
     * sends the localized {@code wp.candidate.cannot_leave_base} message
     * (throttled to once every {@link #REGION_MESSAGE_COOLDOWN_TICKS} ticks per
     * player).
     */
    private static void enforceBaseRegion(ServerPlayer player) {
        Optional<FactionId> factionOpt = player.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            // Defensive: a CANDIDATE without a faction is an invalid state.
            // Don't teleport — that would loop forever; just bail.
            return;
        }
        FactionId faction = factionOpt.get();

        @Nullable BaseRegion region = RegionCacheHandler.regionService()
                .regionAt(player.serverLevel().dimension(), player.position());

        boolean inOwnBase = region != null && region.faction() == faction;
        if (inOwnBase) {
            return;
        }

        teleportToFactionSpawn(player, faction);

        UUID id = player.getUUID();
        int next = NEXT_REGION_MESSAGE_TICK.getOrDefault(id, 0);
        if (player.tickCount >= next) {
            player.displayClientMessage(
                    Component.translatable("wp.candidate.cannot_leave_base"),
                    false /* system message line, not action bar */);
            NEXT_REGION_MESSAGE_TICK.put(id, player.tickCount + REGION_MESSAGE_COOLDOWN_TICKS);
        }
    }

    /**
     * Teleports the candidate back to the spawn point of their own faction.
     * Mirrors the same logic used by
     * {@link com.frostlogic.warproject.server.faction.FactionChoiceHandler} so
     * that initial placement and re-placement use identical coordinates.
     */
    private static void teleportToFactionSpawn(ServerPlayer player, FactionId faction) {
        List<? extends Integer> coords = switch (faction) {
            case ZARNAVIA -> WpConfig.FACTIONS_ZARNAVIA_SPAWN.get();
            case CHERNOGRYAD -> WpConfig.FACTIONS_CHERNOGRYAD_SPAWN.get();
        };
        if (coords.size() < 3) {
            WarProject.LOGGER.warn(
                    "[WP Candidate] Faction spawn coordinates not configured for {}; cannot return candidate {} to base.",
                    faction.getSerializedName(), player.getGameProfile().getName());
            return;
        }
        double x = coords.get(0).doubleValue() + 0.5;
        double y = coords.get(1).doubleValue();
        double z = coords.get(2).doubleValue() + 0.5;
        player.teleportTo(player.serverLevel(), x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
    }

    /**
     * Sends the {@code wp.candidate.actionbar} reminder if it is due, then
     * schedules the next reminder at {@code currentTick + interval + jitter}
     * where {@code jitter ∈ [-20, +20]} (Req. 7.7).
     */
    private static void maybeShowActionbar(ServerPlayer player) {
        UUID id = player.getUUID();
        int next = NEXT_ACTIONBAR_TICK.getOrDefault(id, 0);
        if (player.tickCount < next) {
            return;
        }

        player.displayClientMessage(
                Component.translatable("wp.candidate.actionbar"),
                true /* action bar */);

        int interval = WpConfig.CANDIDATE_ACTIONBAR_INTERVAL_TICKS.get();
        // Jitter in the range [-20, +20] ticks (inclusive on both ends).
        int jitter = RANDOM.nextInt(2 * ACTIONBAR_JITTER_TICKS + 1) - ACTIONBAR_JITTER_TICKS;
        int delay = Math.max(ACTIONBAR_MIN_INTERVAL_TICKS, interval + jitter);
        NEXT_ACTIONBAR_TICK.put(id, player.tickCount + delay);
    }
}
