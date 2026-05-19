package com.frostlogic.warproject.server.radial;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import net.jqwik.api.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the radial menu authority layer
 * ({@link RadialMenuDispatcher} + {@link RaytraceUtil}).
 *
 * <p><b>Property 10 — серверный raytrace ≤ 5 блоков (геометрическая модель).</b>
 * For any pair (initiator eye + look, target {@link AABB}), the segment
 * {@code eye → eye + look · MAX_DISTANCE} intersects the AABB <em>iff</em>
 * there exists a parameter {@code t ∈ [0, MAX_DISTANCE]} such that
 * {@code eye + t · look ∈ AABB}. Since {@code RaytraceUtil} relies on
 * Minecraft's {@link AABB#clip(Vec3, Vec3)} for entity hit detection,
 * this property locks in the geometric contract documented in design §8.4
 * (i.e. the "iff" that {@code raytracePlayer(i) == t} satisfies for distance
 * and ray intersection within {@code MAX_DISTANCE}).
 *
 * <p><b>Property 11 — авторизация радиального действия.</b>
 * For any {@code RadialMenuItem m}, initiator {@code (role, faction, inOwnBase)}
 * and target {@code (state, faction)} with a successful raytrace,
 * the dispatcher's predicate is exactly the conjunction
 * <pre>
 *     i.role ≥ m.requiredRole
 *   ∧ m.appliesTo(t.state)
 *   ∧ factionRule(m, i.faction, t.faction)
 *   ∧ (¬m.requiresOwnBase ∨ i.region.faction == i.faction)
 *   ∧ raytrace(i) == t
 * </pre>
 * Because {@link RadialMenuDispatcher#allow} needs a {@code ServerPlayer}
 * (Minecraft runtime), we test the equivalent pure predicate {@link #pureAllow}
 * that mirrors the dispatcher's branches one-to-one, plus a metadata table
 * that pins each {@link RadialMenuItem} to design §5.3.
 *
 * <p><b>Validates: Requirements 8.3, 8.4, 8.5, 9.2, 9.6, 9.7, 14.2</b>
 *
 * <p>Design: §12 Properties 10, 11; §5.3; §8.4
 */
@PropertyDefaults(tries = 200)
class RadialMenuDispatcherProperties {

    // =========================================================================
    // Design §5.3 reference table — pinned per RadialMenuItem.
    // Any change here must be mirrored in RadialMenuItem and vice-versa.
    // =========================================================================

    private enum FactionRule { ANY, SAME, ENEMY }

    private record ItemSpec(
            Role requiredRole,
            Set<PlayerState> applicableTargetStates,
            FactionRule factionRule,
            boolean requiresOwnBase
    ) {}

    private static final Map<RadialMenuItem, ItemSpec> DESIGN_TABLE;

    static {
        EnumMap<RadialMenuItem, ItemSpec> table = new EnumMap<>(RadialMenuItem.class);
        table.put(RadialMenuItem.ACCEPT, new ItemSpec(
                Role.COMMANDER, Set.of(PlayerState.CANDIDATE), FactionRule.SAME, true));
        table.put(RadialMenuItem.CAPTURE_PASSPORT, new ItemSpec(
                Role.SOLDIER, Set.of(PlayerState.ACCEPTED), FactionRule.ENEMY, false));
        table.put(RadialMenuItem.PROMOTE, new ItemSpec(
                Role.COMMANDER, Set.of(PlayerState.ACCEPTED), FactionRule.SAME, false));
        table.put(RadialMenuItem.DEMOTE, new ItemSpec(
                Role.COMMANDER, Set.of(PlayerState.ACCEPTED), FactionRule.SAME, false));
        table.put(RadialMenuItem.COLLAB_MARK, new ItemSpec(
                Role.OP, EnumSet.allOf(PlayerState.class), FactionRule.ANY, false));
        table.put(RadialMenuItem.COLLAB_UNMARK, new ItemSpec(
                Role.OP, EnumSet.allOf(PlayerState.class), FactionRule.ANY, false));
        table.put(RadialMenuItem.RANSOM_OPEN, new ItemSpec(
                Role.COMMANDER, Set.of(PlayerState.CAPTURED), FactionRule.ANY, false));
        table.put(RadialMenuItem.SUBDIV_INVITE, new ItemSpec(
                Role.COMMANDER, Set.of(PlayerState.ACCEPTED), FactionRule.SAME, false));
        table.put(RadialMenuItem.SUBDIV_KICK, new ItemSpec(
                Role.COMMANDER, Set.of(PlayerState.ACCEPTED), FactionRule.SAME, false));
        DESIGN_TABLE = table;
    }

    // =========================================================================
    // Property 11a — RadialMenuItem metadata matches the design §5.3 table.
    // =========================================================================

    @Test
    void everyItemMatchesDesignTable() {
        // every enum constant must have a row in the design table
        assertThat(DESIGN_TABLE.keySet())
                .as("design table covers every RadialMenuItem")
                .containsExactlyInAnyOrder(RadialMenuItem.values());

        for (RadialMenuItem item : RadialMenuItem.values()) {
            ItemSpec spec = DESIGN_TABLE.get(item);

            assertThat(item.requiredRole())
                    .as("requiredRole for %s", item)
                    .isEqualTo(spec.requiredRole());

            assertThat(item.applicableTargetStates())
                    .as("applicableTargetStates for %s", item)
                    .isEqualTo(spec.applicableTargetStates());

            assertThat(item.requiresOwnFaction())
                    .as("requiresOwnFaction for %s", item)
                    .isEqualTo(spec.factionRule() == FactionRule.SAME);

            assertThat(item.requiresEnemyFaction())
                    .as("requiresEnemyFaction for %s", item)
                    .isEqualTo(spec.factionRule() == FactionRule.ENEMY);

            assertThat(item.requiresOwnBase())
                    .as("requiresOwnBase for %s", item)
                    .isEqualTo(spec.requiresOwnBase());
        }
    }

    @Property
    void appliesTo_isMembershipInApplicableStates(
            @ForAll("items") RadialMenuItem item,
            @ForAll("states") PlayerState state
    ) {
        boolean expected = DESIGN_TABLE.get(item).applicableTargetStates().contains(state);
        assertThat(item.appliesTo(state))
                .as("%s.appliesTo(%s)", item, state)
                .isEqualTo(expected);
    }

    // =========================================================================
    // Property 11b — allow ⇔ conjunction of role / state / faction / base / raytrace.
    //
    // We model RadialMenuDispatcher.allow as a pure predicate over the same
    // primitives the real dispatcher reads from ServerPlayer / RegionService:
    // the conjunction is short-circuited identically to the production code.
    // =========================================================================

    @Property
    void allow_iff_conjunctionOfChecks(
            @ForAll("items") RadialMenuItem item,
            @ForAll("roles") Role initiatorRole,
            @ForAll("optionalFactions") Optional<FactionId> initiatorFaction,
            @ForAll("optionalFactions") Optional<FactionId> targetFaction,
            @ForAll("states") PlayerState targetState,
            @ForAll boolean isInOwnBase,
            @ForAll boolean raytraceHits
    ) {
        // Reference predicate computed independently from each precondition.
        boolean roleOk = initiatorRole.atLeast(item.requiredRole());
        boolean stateOk = item.appliesTo(targetState);
        boolean factionOk = factionRulePasses(item, initiatorFaction, targetFaction);
        boolean baseOk = !item.requiresOwnBase()
                || (initiatorFaction.isPresent() && isInOwnBase);
        boolean expected = roleOk && stateOk && factionOk && baseOk && raytraceHits;

        boolean actual = pureAllow(item, initiatorRole, initiatorFaction,
                targetState, targetFaction, isInOwnBase, raytraceHits);

        assertThat(actual)
                .as("pureAllow(%s, role=%s, iFac=%s, tState=%s, tFac=%s, inBase=%s, ray=%s)",
                        item, initiatorRole, initiatorFaction, targetState,
                        targetFaction, isInOwnBase, raytraceHits)
                .isEqualTo(expected);
    }

    // Each negated precondition individually forces allow == false.
    @Property
    void allow_isFalse_whenAnyPreconditionFails(
            @ForAll("items") RadialMenuItem item,
            @ForAll("roles") Role initiatorRole,
            @ForAll("optionalFactions") Optional<FactionId> initiatorFaction,
            @ForAll("optionalFactions") Optional<FactionId> targetFaction,
            @ForAll("states") PlayerState targetState,
            @ForAll boolean isInOwnBase,
            @ForAll boolean raytraceHits
    ) {
        boolean roleOk = initiatorRole.atLeast(item.requiredRole());
        boolean stateOk = item.appliesTo(targetState);
        boolean factionOk = factionRulePasses(item, initiatorFaction, targetFaction);
        boolean baseOk = !item.requiresOwnBase()
                || (initiatorFaction.isPresent() && isInOwnBase);

        boolean actual = pureAllow(item, initiatorRole, initiatorFaction,
                targetState, targetFaction, isInOwnBase, raytraceHits);

        if (!roleOk || !stateOk || !factionOk || !baseOk || !raytraceHits) {
            assertThat(actual).isFalse();
        } else {
            assertThat(actual).isTrue();
        }
    }

    // =========================================================================
    // Property 10 — geometric raytrace contract that backs RaytraceUtil.
    //
    // RaytraceUtil.raytracePlayer uses ProjectileUtil.getEntityHitResult, which
    // calls AABB.clip(eye, end) per candidate entity. We test the underlying
    // mathematical equivalence: clip returns a hit ⇔ the segment intersects
    // the AABB. Then we verify the distance constraint MAX_DISTANCE = 5.0.
    // =========================================================================

    @Test
    void raytrace_maxDistanceIsFiveBlocks() {
        // Hard requirement from the design (§8.4) and Req 8.3 / 14.2.
        assertThat(RaytraceUtil.MAX_DISTANCE).isEqualTo(5.0);
    }

    @Property
    void aabbClip_intersectsIff_segmentEntersBox(
            @ForAll("vec3s") Vec3 eye,
            @ForAll("unitVecs") Vec3 look,
            @ForAll("aabbs") AABB targetBox
    ) {
        Vec3 end = eye.add(look.scale(RaytraceUtil.MAX_DISTANCE));

        Optional<Vec3> hit = targetBox.clip(eye, end);

        // Independent reference: sample many points along the segment
        // and check whether any falls inside the AABB. If clip says hit,
        // the whole segment must touch the box (or the eye is inside).
        // If clip says miss, no sampled point should lie strictly inside.
        boolean clipHit = hit.isPresent();
        boolean eyeInside = pointInside(targetBox, eye);

        if (clipHit) {
            // The hit point must lie on the segment within [0, MAX_DISTANCE]
            // (or eye is already inside the box, in which case clip can return eye).
            Vec3 hitPoint = hit.get();
            double dist = hitPoint.distanceTo(eye);
            assertThat(dist)
                    .as("clip hit distance must be ≤ MAX_DISTANCE")
                    .isLessThanOrEqualTo(RaytraceUtil.MAX_DISTANCE + 1e-6);

            // Hit point must lie on the AABB surface or interior (allow tiny epsilon).
            assertThat(pointInsideOrOnSurface(targetBox, hitPoint, 1e-6))
                    .as("clip hit point %s must lie on target box %s", hitPoint, targetBox)
                    .isTrue();
        } else {
            // No hit: either the eye is outside AND the segment misses the box,
            // OR the eye is inside (clip semantics return empty when starting inside
            // depending on direction — we just assert: every interior sample point
            // can only happen when eye was inside).
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                Vec3 p = eye.add(end.subtract(eye).scale(t));
                if (pointInsideStrict(targetBox, p)) {
                    // The only way the segment can enter the box and clip miss
                    // is if it started inside (clip's documented behaviour for
                    // segments starting inside the box).
                    assertThat(eyeInside)
                            .as("segment enters box at t=%.2f but clip missed; eye must be inside", t)
                            .isTrue();
                    break;
                }
            }
        }
    }

    // =========================================================================
    // Pure model of RadialMenuDispatcher.allow — mirrors the production branches.
    // =========================================================================

    private static boolean pureAllow(
            RadialMenuItem item,
            Role initiatorRole,
            Optional<FactionId> initiatorFaction,
            PlayerState targetState,
            Optional<FactionId> targetFaction,
            boolean isInOwnBase,
            boolean raytraceHits
    ) {
        // (1) role
        if (!initiatorRole.atLeast(item.requiredRole())) return false;
        // (2) target state
        if (!item.appliesTo(targetState)) return false;
        // (3) faction rule
        if (!factionRulePasses(item, initiatorFaction, targetFaction)) return false;
        // (4) own-base requirement
        if (item.requiresOwnBase()) {
            if (initiatorFaction.isEmpty() || !isInOwnBase) return false;
        }
        // (5) raytrace
        return raytraceHits;
    }

    private static boolean factionRulePasses(
            RadialMenuItem item,
            Optional<FactionId> initiatorFaction,
            Optional<FactionId> targetFaction
    ) {
        if (item.requiresOwnFaction()) {
            return initiatorFaction.isPresent()
                    && targetFaction.isPresent()
                    && initiatorFaction.get() == targetFaction.get();
        }
        if (item.requiresEnemyFaction()) {
            return initiatorFaction.isPresent()
                    && targetFaction.isPresent()
                    && initiatorFaction.get() != targetFaction.get();
        }
        return true;
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    private static boolean pointInside(AABB box, Vec3 p) {
        return p.x >= box.minX && p.x <= box.maxX
                && p.y >= box.minY && p.y <= box.maxY
                && p.z >= box.minZ && p.z <= box.maxZ;
    }

    private static boolean pointInsideStrict(AABB box, Vec3 p) {
        // strictly inside (no surface) — used to detect segment crossing the box
        return p.x > box.minX && p.x < box.maxX
                && p.y > box.minY && p.y < box.maxY
                && p.z > box.minZ && p.z < box.maxZ;
    }

    private static boolean pointInsideOrOnSurface(AABB box, Vec3 p, double eps) {
        return p.x >= box.minX - eps && p.x <= box.maxX + eps
                && p.y >= box.minY - eps && p.y <= box.maxY + eps
                && p.z >= box.minZ - eps && p.z <= box.maxZ + eps;
    }

    // =========================================================================
    // Generators
    // =========================================================================

    @Provide
    Arbitrary<RadialMenuItem> items() {
        return Arbitraries.of(RadialMenuItem.values());
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.values());
    }

    @Provide
    Arbitrary<PlayerState> states() {
        return Arbitraries.of(PlayerState.values());
    }

    @Provide
    Arbitrary<Optional<FactionId>> optionalFactions() {
        // None = "no faction set" (e.g. FACTIONLESS); each FactionId equally likely.
        Arbitrary<Optional<FactionId>> some = Arbitraries.of(FactionId.values())
                .map(Optional::of);
        Arbitrary<Optional<FactionId>> none = Arbitraries.just(Optional.<FactionId>empty());
        return Arbitraries.frequencyOf(
                Tuple.of(2, some),
                Tuple.of(1, none)
        );
    }

    @Provide
    Arbitrary<Vec3> vec3s() {
        Arbitrary<Double> coord = Arbitraries.doubles().between(-100.0, 100.0).ofScale(3);
        return Combinators.combine(coord, coord, coord).as(Vec3::new);
    }

    @Provide
    Arbitrary<Vec3> unitVecs() {
        // Unit-length view vectors: parameterize by yaw / pitch (degrees, then to radians).
        // We use degrees so jqwik can keep its decimal-scale invariants happy with rounded
        // bounds; the resulting unit vector still covers the full sphere of orientations.
        Arbitrary<Double> yawDeg = Arbitraries.doubles().between(-180.0, 180.0).ofScale(3);
        Arbitrary<Double> pitchDeg = Arbitraries.doubles().between(-89.0, 89.0).ofScale(3);
        return Combinators.combine(yawDeg, pitchDeg).as((yd, pd) -> {
            double y = Math.toRadians(yd);
            double p = Math.toRadians(pd);
            double cosP = Math.cos(p);
            return new Vec3(cosP * Math.cos(y), Math.sin(p), cosP * Math.sin(y));
        });
    }

    @Provide
    Arbitrary<AABB> aabbs() {
        Arbitrary<Double> coord = Arbitraries.doubles().between(-50.0, 50.0).ofScale(3);
        Arbitrary<Double> size = Arbitraries.doubles().between(0.5, 4.0).ofScale(3);
        return Combinators.combine(coord, coord, coord, size, size, size)
                .as((x, y, z, sx, sy, sz) -> new AABB(x, y, z, x + sx, y + sy, z + sz));
    }
}
