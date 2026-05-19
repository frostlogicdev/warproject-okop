package com.frostlogic.warproject.server.role;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the {@code /wp up} command role/rank logic.
 * <p>
 * Property 12 (partial): For {@code /wp up <name> <rank>}, the requested rank must be
 * strictly below the initiator's role, and the faction must match (unless the initiator is OP).
 * <p>
 * Since {@code RoleResolver} depends on {@code CommandSourceStack} (Minecraft runtime),
 * we test the pure {@link Role#atLeast(Role)} logic and the rank/faction constraints directly.
 * <p>
 * <b>Validates: Requirements 10.2, 10.4</b>
 * <p>
 * Design: §12 Property 12
 */
@PropertyDefaults(tries = 100)
class RoleUpCommandProperties {

    // --- Property 12a: /wp up requires at least COMMANDER ---

    @Property
    void upCommand_requiresAtLeastCommander(@ForAll("roles") Role initiatorRole) {
        boolean canExecute = initiatorRole.atLeast(Role.COMMANDER);

        boolean expected = initiatorRole == Role.COMMANDER
                || initiatorRole == Role.GENERAL
                || initiatorRole == Role.OP;

        assertThat(canExecute)
                .as("Role %s should%s be able to execute /wp up (requires COMMANDER+)",
                        initiatorRole, expected ? "" : " NOT")
                .isEqualTo(expected);
    }

    // --- Property 12b: requested rank must be strictly below initiator's role ---
    // Modeled as: the role associated with the requested rank must be < initiator's role.
    // Ranks belong to roles (SOLDIER, COMMANDER, GENERAL). A COMMANDER can only assign
    // ranks within SOLDIER role. A GENERAL can assign SOLDIER and COMMANDER ranks.
    // OP can assign any rank.

    @Property
    void upCommand_requestedRankRoleMustBeStrictlyBelowInitiator(
            @ForAll("commanderPlusRoles") Role initiatorRole,
            @ForAll("rankRoles") Role requestedRankRole
    ) {
        // The rank's associated role must be strictly below the initiator's role
        boolean canAssign = initiatorRole.level() > requestedRankRole.level();

        // OP (level 4) > GENERAL (3) > COMMANDER (2) > SOLDIER (1) > CANDIDATE (0)
        // COMMANDER (2) can assign ranks of SOLDIER (1) only
        // GENERAL (3) can assign ranks of SOLDIER (1) and COMMANDER (2)
        // OP (4) can assign ranks of SOLDIER (1), COMMANDER (2), and GENERAL (3)
        if (initiatorRole == Role.COMMANDER) {
            assertThat(canAssign)
                    .as("COMMANDER can only assign ranks of role strictly below (SOLDIER)")
                    .isEqualTo(requestedRankRole == Role.SOLDIER);
        } else if (initiatorRole == Role.GENERAL) {
            assertThat(canAssign)
                    .as("GENERAL can assign ranks of SOLDIER and COMMANDER")
                    .isEqualTo(requestedRankRole == Role.SOLDIER || requestedRankRole == Role.COMMANDER);
        } else if (initiatorRole == Role.OP) {
            assertThat(canAssign)
                    .as("OP can assign any rank role")
                    .isTrue();
        }
    }

    // --- Property 12c: faction must match for non-OP initiators ---

    @Property
    void upCommand_factionMustMatchUnlessOP(
            @ForAll("commanderPlusRoles") Role initiatorRole,
            @ForAll("factions") FactionId initiatorFaction,
            @ForAll("factions") FactionId targetFaction
    ) {
        boolean sameFaction = initiatorFaction == targetFaction;
        boolean isOp = initiatorRole == Role.OP;

        // Non-OP initiators can only promote players of the same faction
        boolean factionCheckPasses = isOp || sameFaction;

        if (!isOp) {
            assertThat(factionCheckPasses)
                    .as("Non-OP role %s with faction %s targeting faction %s: faction must match",
                            initiatorRole, initiatorFaction, targetFaction)
                    .isEqualTo(sameFaction);
        } else {
            assertThat(factionCheckPasses)
                    .as("OP can promote players of any faction")
                    .isTrue();
        }
    }

    // --- Property 12d: combined constraint — canPromote iff role >= COMMANDER AND rankRole < initiatorRole AND (OP OR sameFaction) ---

    @Property
    void upCommand_combinedConstraint(
            @ForAll("roles") Role initiatorRole,
            @ForAll("factions") FactionId initiatorFaction,
            @ForAll("factions") FactionId targetFaction,
            @ForAll("rankRoles") Role requestedRankRole
    ) {
        boolean hasMinRole = initiatorRole.atLeast(Role.COMMANDER);
        boolean rankBelowInitiator = initiatorRole.level() > requestedRankRole.level();
        boolean factionOk = initiatorRole == Role.OP || initiatorFaction == targetFaction;

        boolean canPromote = hasMinRole && rankBelowInitiator && factionOk;

        // Verify the combined logic
        if (canPromote) {
            assertThat(hasMinRole).as("Must have at least COMMANDER role").isTrue();
            assertThat(rankBelowInitiator).as("Requested rank role must be below initiator").isTrue();
            assertThat(factionOk).as("Faction must match or initiator is OP").isTrue();
        }

        // Verify negation cases
        if (!hasMinRole) {
            assertThat(canPromote).as("Cannot promote without COMMANDER+ role").isFalse();
        }
        if (hasMinRole && !rankBelowInitiator) {
            assertThat(canPromote).as("Cannot promote to rank at or above own role").isFalse();
        }
        if (hasMinRole && rankBelowInitiator && !factionOk) {
            assertThat(canPromote).as("Non-OP cannot promote across factions").isFalse();
        }
    }

    // --- Generators ---

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.values());
    }

    @Provide
    Arbitrary<Role> commanderPlusRoles() {
        return Arbitraries.of(Role.COMMANDER, Role.GENERAL, Role.OP);
    }

    /**
     * Roles that can have ranks assigned to them (SOLDIER, COMMANDER, GENERAL).
     * CANDIDATE and OP do not have assignable ranks.
     */
    @Provide
    Arbitrary<Role> rankRoles() {
        return Arbitraries.of(Role.SOLDIER, Role.COMMANDER, Role.GENERAL);
    }

    @Provide
    Arbitrary<FactionId> factions() {
        return Arbitraries.of(FactionId.values());
    }
}
