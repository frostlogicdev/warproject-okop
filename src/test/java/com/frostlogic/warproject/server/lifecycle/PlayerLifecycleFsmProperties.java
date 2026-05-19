package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.attachment.PlayerState;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the {@link PlayerLifecycleService} finite state machine.
 * <p>
 * Tests the pure {@code canTransition(PlayerState, PlayerState)} method which validates
 * transitions against the state diagram from design §5.1.
 * <p>
 * <b>Validates: Requirements 21.1</b>
 * <p>
 * Design: §12 Property 7, 9
 */
@PropertyDefaults(tries = 100)
class PlayerLifecycleFsmProperties {

    private final PlayerLifecycleService service = new PlayerLifecycleService(null, null);

    /**
     * The complete valid transitions map (from design §5.1).
     */
    private static final Map<PlayerState, Set<PlayerState>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(PlayerState.class);
        VALID_TRANSITIONS.put(PlayerState.NEW, EnumSet.of(PlayerState.REGISTERED_PENDING));
        VALID_TRANSITIONS.put(PlayerState.REGISTERED_PENDING, EnumSet.of(PlayerState.CAPTCHA));
        VALID_TRANSITIONS.put(PlayerState.CAPTCHA, EnumSet.of(PlayerState.RPNAME_REQUIRED));
        VALID_TRANSITIONS.put(PlayerState.LOGIN_PENDING, EnumSet.of(
                PlayerState.RPNAME_REQUIRED,
                PlayerState.FACTIONLESS,
                PlayerState.CANDIDATE,
                PlayerState.ACCEPTED,
                PlayerState.CAPTURED
        ));
        VALID_TRANSITIONS.put(PlayerState.RPNAME_REQUIRED, EnumSet.of(PlayerState.FACTIONLESS));
        VALID_TRANSITIONS.put(PlayerState.FACTIONLESS, EnumSet.of(PlayerState.CANDIDATE));
        VALID_TRANSITIONS.put(PlayerState.CANDIDATE, EnumSet.of(PlayerState.ACCEPTED));
        VALID_TRANSITIONS.put(PlayerState.ACCEPTED, EnumSet.of(PlayerState.CAPTURED));
        VALID_TRANSITIONS.put(PlayerState.CAPTURED, EnumSet.of(PlayerState.ACCEPTED));
    }

    // ─── Property 7: Valid transition sequences from NEW are accepted ─────────────

    /**
     * For any valid transition sequence starting from NEW, canTransition returns true
     * for each step in the sequence.
     * <p>
     * This verifies that the FSM correctly accepts all valid paths through the
     * lifecycle state machine.
     */
    @Property
    void validTransitionSequenceFromNew_allStepsAccepted(
            @ForAll("validPathsFromNew") List<PlayerState> path
    ) {
        // path includes the starting state NEW as the first element
        for (int i = 0; i < path.size() - 1; i++) {
            PlayerState from = path.get(i);
            PlayerState to = path.get(i + 1);
            assertThat(service.canTransition(from, to))
                    .as("Transition %s → %s (step %d) in valid path should be accepted", from, to, i)
                    .isTrue();
        }
    }

    // ─── Property 9: Invalid transitions are rejected ─────────────────────────────

    /**
     * For any pair (from, to) where to is NOT in the valid set for from,
     * canTransition returns false.
     * <p>
     * This verifies that the FSM correctly rejects all invalid transitions.
     */
    @Property
    void invalidTransitions_areRejected(
            @ForAll("invalidTransitionPairs") TransitionPair pair
    ) {
        assertThat(service.canTransition(pair.from(), pair.to()))
                .as("Invalid transition %s → %s should be rejected", pair.from(), pair.to())
                .isFalse();
    }

    // ─── Property: Transition graph is acyclic except ACCEPTED ↔ CAPTURED ─────────

    /**
     * The transition graph is acyclic except for the ACCEPTED ↔ CAPTURED cycle.
     * A true cycle means there exists a path from a node back to itself.
     * The only such cycle in the FSM is ACCEPTED → CAPTURED → ACCEPTED.
     * <p>
     * We verify this by checking that removing the edge CAPTURED → ACCEPTED
     * leaves a DAG (no cycles at all).
     */
    @Property
    void transitionGraph_acyclicExceptAcceptedCaptured(
            @ForAll("allStates") PlayerState startState
    ) {
        // DFS cycle detection using recursion stack (on-stack set).
        // We exclude the CAPTURED → ACCEPTED edge to verify the rest is a DAG.
        Set<PlayerState> visited = EnumSet.noneOf(PlayerState.class);
        Set<PlayerState> onStack = EnumSet.noneOf(PlayerState.class);

        boolean hasCycle = hasCycleExcludingCapturedToAccepted(startState, visited, onStack);
        assertThat(hasCycle)
                .as("Graph should be acyclic (excluding CAPTURED→ACCEPTED edge) when starting DFS from %s",
                        startState)
                .isFalse();
    }

    private boolean hasCycleExcludingCapturedToAccepted(
            PlayerState node, Set<PlayerState> visited, Set<PlayerState> onStack) {
        if (onStack.contains(node)) {
            return true; // back-edge found → cycle
        }
        if (visited.contains(node)) {
            return false; // already fully explored
        }
        visited.add(node);
        onStack.add(node);

        Set<PlayerState> successors = VALID_TRANSITIONS.getOrDefault(node, EnumSet.noneOf(PlayerState.class));
        for (PlayerState next : successors) {
            // Skip the CAPTURED → ACCEPTED edge (the allowed cycle)
            if (node == PlayerState.CAPTURED && next == PlayerState.ACCEPTED) {
                continue;
            }
            if (hasCycleExcludingCapturedToAccepted(next, visited, onStack)) {
                return true;
            }
        }

        onStack.remove(node);
        return false;
    }

    // ─── Property: Deterministic progression (at most one forward path) ───────────

    /**
     * From any state except LOGIN_PENDING, there is at most one valid successor state.
     * This ensures deterministic progression through the lifecycle.
     * LOGIN_PENDING is the exception as it can transition to multiple states
     * (restoring the player's previous session state).
     */
    @Property
    void deterministicProgression_atMostOneSuccessor(
            @ForAll("nonLoginPendingStates") PlayerState state
    ) {
        Set<PlayerState> successors = VALID_TRANSITIONS.getOrDefault(state, EnumSet.noneOf(PlayerState.class));
        assertThat(successors.size())
                .as("State %s should have at most 1 successor (deterministic), but has %d: %s",
                        state, successors.size(), successors)
                .isLessThanOrEqualTo(1);
    }

    /**
     * LOGIN_PENDING has multiple valid successors (it can restore to any active state).
     */
    @Property
    void loginPending_hasMultipleSuccessors() {
        Set<PlayerState> successors = VALID_TRANSITIONS.get(PlayerState.LOGIN_PENDING);
        assertThat(successors)
                .as("LOGIN_PENDING should have multiple successors")
                .hasSizeGreaterThan(1);
    }

    // ─── Generators ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<PlayerState>> validPathsFromNew() {
        // Generate valid paths starting from NEW by following valid transitions.
        // We use a recursive approach with bounded depth to avoid infinite loops
        // in the ACCEPTED ↔ CAPTURED cycle.
        return Arbitraries.integers().between(1, 10).flatMap(maxSteps ->
                Arbitraries.lazy(() -> generateValidPath(PlayerState.NEW, maxSteps))
        );
    }

    private Arbitrary<List<PlayerState>> generateValidPath(PlayerState start, int maxSteps) {
        List<List<PlayerState>> allPaths = new ArrayList<>();
        generatePathsDfs(start, maxSteps, new ArrayList<>(), allPaths, EnumSet.noneOf(PlayerState.class));
        if (allPaths.isEmpty()) {
            return Arbitraries.just(List.of(start));
        }
        return Arbitraries.of(allPaths);
    }

    private void generatePathsDfs(PlayerState current, int remainingSteps,
                                   List<PlayerState> currentPath,
                                   List<List<PlayerState>> allPaths,
                                   Set<PlayerState> visited) {
        currentPath.add(current);

        if (remainingSteps <= 0 || !VALID_TRANSITIONS.containsKey(current)) {
            allPaths.add(new ArrayList<>(currentPath));
            currentPath.remove(currentPath.size() - 1);
            return;
        }

        Set<PlayerState> successors = VALID_TRANSITIONS.get(current);
        if (successors == null || successors.isEmpty()) {
            allPaths.add(new ArrayList<>(currentPath));
            currentPath.remove(currentPath.size() - 1);
            return;
        }

        // Also add the current path as a valid (partial) path
        allPaths.add(new ArrayList<>(currentPath));

        for (PlayerState next : successors) {
            // Allow ACCEPTED ↔ CAPTURED cycle once, but prevent infinite recursion
            if (visited.contains(next) && next != PlayerState.ACCEPTED && next != PlayerState.CAPTURED) {
                continue;
            }
            // For the cycle, allow at most one revisit
            Set<PlayerState> newVisited = EnumSet.copyOf(visited);
            newVisited.add(current);
            if (visited.contains(next)) {
                // Already visited this cycle node — add one more step and stop
                List<PlayerState> cyclePath = new ArrayList<>(currentPath);
                cyclePath.add(next);
                allPaths.add(cyclePath);
            } else {
                generatePathsDfs(next, remainingSteps - 1, currentPath, allPaths, newVisited);
            }
        }

        currentPath.remove(currentPath.size() - 1);
    }

    @Provide
    Arbitrary<TransitionPair> invalidTransitionPairs() {
        return Arbitraries.of(PlayerState.values()).flatMap(from -> {
            Set<PlayerState> validTargets = VALID_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PlayerState.class));
            // Collect all states that are NOT valid targets from this state
            List<PlayerState> invalidTargets = new ArrayList<>();
            for (PlayerState to : PlayerState.values()) {
                if (!validTargets.contains(to)) {
                    invalidTargets.add(to);
                }
            }
            if (invalidTargets.isEmpty()) {
                // Should not happen given the FSM, but handle gracefully
                return Arbitraries.just(new TransitionPair(from, from));
            }
            return Arbitraries.of(invalidTargets).map(to -> new TransitionPair(from, to));
        });
    }

    @Provide
    Arbitrary<PlayerState> allStates() {
        return Arbitraries.of(PlayerState.values());
    }

    @Provide
    Arbitrary<PlayerState> nonLoginPendingStates() {
        return Arbitraries.of(
                PlayerState.NEW,
                PlayerState.REGISTERED_PENDING,
                PlayerState.CAPTCHA,
                PlayerState.RPNAME_REQUIRED,
                PlayerState.FACTIONLESS,
                PlayerState.CANDIDATE,
                PlayerState.ACCEPTED,
                PlayerState.CAPTURED
        );
    }

    // ─── Helper record ────────────────────────────────────────────────────────────

    record TransitionPair(PlayerState from, PlayerState to) {}
}
