package com.frostlogic.warproject.server.radial;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.region.BaseRegion;
import com.frostlogic.warproject.server.region.RegionCacheHandler;
import com.frostlogic.warproject.server.region.RegionService;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Server-side authority for the radial menu.
 * <p>
 * Decides which {@link RadialMenuItem}s are allowed for a given
 * (initiator, target) pair. The check is the conjunction of:
 * <ol>
 *   <li>{@code initiator.role ≥ item.requiredRole};</li>
 *   <li>{@code item.appliesTo(target.status)};</li>
 *   <li>faction rule (own / enemy / any) per the item;</li>
 *   <li>{@code initiator.region.faction == initiator.faction} when the item
 *       {@linkplain RadialMenuItem#requiresOwnBase() requires own base};</li>
 *   <li>raytrace: {@link RaytraceUtil#raytracePlayer(ServerPlayer)} returns the target.</li>
 * </ol>
 * <p>
 * The same predicate is used both to compute {@link #visibleItems(ServerPlayer, ServerPlayer)}
 * (sent in {@code RadialMenuPayload}) and to authorize the action when
 * {@code RadialActionPayload} arrives, so the client cannot bypass the check.
 * <p>
 * Validates: Property 11 (design §12).
 * <p>
 * Requirements: 9.5–9.7, 21.1
 * Design: §5.3, §6, §8.4
 */
public final class RadialMenuDispatcher {

    private final RegionService regionService;

    /**
     * Constructs a dispatcher using the shared {@link RegionService} from
     * {@link RegionCacheHandler}.
     */
    public RadialMenuDispatcher() {
        this(RegionCacheHandler.regionService());
    }

    /**
     * Constructs a dispatcher with an explicit {@link RegionService}.
     * Mainly useful for unit / property tests.
     */
    public RadialMenuDispatcher(RegionService regionService) {
        this.regionService = regionService;
    }

    /**
     * Returns the set of menu items whose every precondition holds for the
     * given (initiator, target) pair.
     *
     * @param initiator the player opening the radial menu
     * @param target    the player the menu is targeting
     * @return an {@link EnumSet} of allowed items (possibly empty)
     */
    public EnumSet<RadialMenuItem> visibleItems(ServerPlayer initiator, ServerPlayer target) {
        EnumSet<RadialMenuItem> allowed = EnumSet.noneOf(RadialMenuItem.class);
        if (initiator == null || target == null) {
            return allowed;
        }
        for (RadialMenuItem item : RadialMenuItem.values()) {
            if (allow(item, initiator, target)) {
                allowed.add(item);
            }
        }
        return allowed;
    }

    /**
     * Returns true if every authority check passes for {@code (item, initiator, target)}.
     * <p>
     * The conjunction is short-circuited: any failed check returns {@code false}
     * without evaluating the remaining ones.
     *
     * @param item      the item being evaluated
     * @param initiator the player invoking the action
     * @param target    the target player
     * @return {@code true} if the action is allowed, {@code false} otherwise
     */
    public boolean allow(RadialMenuItem item, ServerPlayer initiator, ServerPlayer target) {
        if (item == null || initiator == null || target == null) {
            return false;
        }
        if (initiator == target) {
            return false;
        }

        // (1) Role check
        Role initiatorRole = initiator.getData(WpAttachmentTypes.ROLE);
        if (!initiatorRole.atLeast(item.requiredRole())) {
            return false;
        }

        // (2) Target state applicability
        PlayerState targetState = target.getData(WpAttachmentTypes.PLAYER_STATE);
        if (!item.appliesTo(targetState)) {
            return false;
        }

        // (3) Faction rule
        Optional<FactionId> initiatorFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        Optional<FactionId> targetFaction = target.getData(WpAttachmentTypes.FACTION.get());
        if (!checkFactionRule(item, initiatorFaction, targetFaction)) {
            return false;
        }

        // (4) Own-base requirement (initiator is physically inside their own faction's base)
        if (item.requiresOwnBase() && !isInOwnBase(initiator, initiatorFaction)) {
            return false;
        }

        // (5) Raytrace: a fresh server-side raytrace must hit exactly the claimed target
        ServerPlayer rayHit = RaytraceUtil.raytracePlayer(initiator);
        if (rayHit == null || !rayHit.getUUID().equals(target.getUUID())) {
            return false;
        }

        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static boolean checkFactionRule(RadialMenuItem item,
                                            Optional<FactionId> initiatorFaction,
                                            Optional<FactionId> targetFaction) {
        if (item.requiresOwnFaction()) {
            // Both must have a faction and they must match
            return initiatorFaction.isPresent()
                    && targetFaction.isPresent()
                    && initiatorFaction.get() == targetFaction.get();
        }
        if (item.requiresEnemyFaction()) {
            // Both must have a faction and they must differ
            return initiatorFaction.isPresent()
                    && targetFaction.isPresent()
                    && initiatorFaction.get() != targetFaction.get();
        }
        // ANY: no constraint on factions
        return true;
    }

    private boolean isInOwnBase(ServerPlayer initiator, Optional<FactionId> initiatorFaction) {
        if (initiatorFaction.isEmpty()) {
            return false;
        }
        BaseRegion region = regionService.regionAt(initiator.level().dimension(), initiator.position());
        return region != null && region.faction() == initiatorFaction.get();
    }
}
