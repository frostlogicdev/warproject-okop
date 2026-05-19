package com.frostlogic.warproject.server.auth;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the captcha round finite state machine.
 * <p>
 * Models the captcha FSM in isolation using {@link CaptchaSession} directly,
 * without Minecraft dependencies. The FSM rules:
 * <ul>
 *   <li>Start: code, 0 attempts, startTick</li>
 *   <li>Submit correct code → SUCCESS (advance to RPNAME_REQUIRED)</li>
 *   <li>Submit wrong code → attempts++; if attempts >= maxAttempts → FAILED (kick + cooldown)</li>
 *   <li>Tick where elapsed >= timeoutTicks → TIMEOUT (kick + cooldown)</li>
 *   <li>Case sensitivity configurable (default: false)</li>
 * </ul>
 * <p>
 * <b>Validates: Requirements 5.4, 5.5, 5.6, 5.7, 5.8</b>
 * <p>
 * Design: §12 Property 6
 */
@PropertyDefaults(tries = 100)
class CaptchaFsmProperties {

    // Default config values matching WpConfig defaults
    private static final int MAX_ATTEMPTS = 3;
    private static final int TIMEOUT_TICKS = 3600; // 180 seconds * 20 ticks/sec
    private static final int COOLDOWN_SECONDS = 90;

    // ─── FSM Model ────────────────────────────────────────────────────────────────

    /**
     * Represents an event in the captcha FSM.
     */
    sealed interface CaptchaEvent {
        record Submit(String input) implements CaptchaEvent {}
        record Tick(int elapsedTicks) implements CaptchaEvent {}
    }

    /**
     * Terminal state of the captcha round.
     */
    enum FsmOutcome {
        /** Correct code submitted before timeout/max attempts. */
        SUCCESS,
        /** 3 wrong attempts reached before timeout or success. */
        FAILED_ATTEMPTS,
        /** Timeout reached before success or 3 wrong attempts. */
        TIMEOUT,
        /** Session still running (no terminal state reached). */
        RUNNING
    }

    /**
     * Side effects that should occur on terminal failure states.
     */
    record SideEffects(boolean kicked, boolean cooldownSet, int cooldownSeconds) {
        static final SideEffects NONE = new SideEffects(false, false, 0);
        static final SideEffects KICK_WITH_COOLDOWN = new SideEffects(true, true, COOLDOWN_SECONDS);
    }

    /**
     * Runs the FSM model for a sequence of events and returns the outcome.
     * This is the reference implementation that the property tests validate against.
     */
    private static FsmResult runFsm(String code, boolean caseSensitive,
                                     List<CaptchaEvent> events) {
        int attempts = 0;
        long totalElapsed = 0;

        for (CaptchaEvent event : events) {
            switch (event) {
                case CaptchaEvent.Tick tick -> {
                    totalElapsed += tick.elapsedTicks();
                    if (totalElapsed >= TIMEOUT_TICKS) {
                        return new FsmResult(FsmOutcome.TIMEOUT, SideEffects.KICK_WITH_COOLDOWN,
                                attempts, totalElapsed);
                    }
                }
                case CaptchaEvent.Submit submit -> {
                    // Check timeout first (tick happens before submit in same logical step)
                    boolean matches = caseSensitive
                            ? code.equals(submit.input())
                            : code.equalsIgnoreCase(submit.input());

                    if (matches) {
                        return new FsmResult(FsmOutcome.SUCCESS, SideEffects.NONE,
                                attempts, totalElapsed);
                    }

                    attempts++;
                    if (attempts >= MAX_ATTEMPTS) {
                        return new FsmResult(FsmOutcome.FAILED_ATTEMPTS, SideEffects.KICK_WITH_COOLDOWN,
                                attempts, totalElapsed);
                    }
                }
            }
        }

        // Events exhausted without reaching terminal state
        return new FsmResult(FsmOutcome.RUNNING, SideEffects.NONE, attempts, totalElapsed);
    }

    record FsmResult(FsmOutcome outcome, SideEffects sideEffects, int finalAttempts, long totalElapsed) {}

    // ─── Property 6a: correct code on first attempt → SUCCESS ─────────────────────

    @Property
    void correctCodeFirstAttempt_alwaysSuccess(
            @ForAll("captchaCodes") String code,
            @ForAll("caseSensitivity") boolean caseSensitive
    ) {
        String input = caseSensitive ? code : randomizeCase(code);

        CaptchaSession session = new CaptchaSession(code, 0, 0);

        // Simulate submit with correct code
        boolean matches = caseSensitive
                ? session.code().equals(input)
                : session.code().equalsIgnoreCase(input);

        assertThat(matches)
                .as("Correct code (caseSensitive=%s) must match: code='%s', input='%s'",
                        caseSensitive, code, input)
                .isTrue();

        // Verify FSM model
        List<CaptchaEvent> events = List.of(new CaptchaEvent.Submit(input));
        FsmResult result = runFsm(code, caseSensitive, events);

        assertThat(result.outcome())
                .as("Correct code on first attempt must yield SUCCESS")
                .isEqualTo(FsmOutcome.SUCCESS);
        assertThat(result.sideEffects())
                .as("SUCCESS must not trigger kick or cooldown")
                .isEqualTo(SideEffects.NONE);
    }

    // ─── Property 6b: 3 wrong codes → FAILED_ATTEMPTS ────────────────────────────

    @Property
    void threeWrongCodes_alwaysFailed(
            @ForAll("captchaCodes") String code,
            @ForAll("wrongCodeSequences") List<String> wrongCodes
    ) {
        Assume.that(wrongCodes.size() >= MAX_ATTEMPTS);
        // Ensure none of the wrong codes match
        Assume.that(wrongCodes.stream().noneMatch(w -> w.equalsIgnoreCase(code)));

        List<CaptchaEvent> events = wrongCodes.stream()
                .limit(MAX_ATTEMPTS)
                .<CaptchaEvent>map(CaptchaEvent.Submit::new)
                .toList();

        FsmResult result = runFsm(code, false, events);

        assertThat(result.outcome())
                .as("3 wrong codes must yield FAILED_ATTEMPTS")
                .isEqualTo(FsmOutcome.FAILED_ATTEMPTS);
        assertThat(result.finalAttempts())
                .as("Final attempts count must be %d", MAX_ATTEMPTS)
                .isEqualTo(MAX_ATTEMPTS);
        assertThat(result.sideEffects())
                .as("FAILED_ATTEMPTS must trigger kick + cooldown")
                .isEqualTo(SideEffects.KICK_WITH_COOLDOWN);
    }

    // ─── Property 6c: elapsed >= timeout → TIMEOUT regardless of attempts ────────

    @Property
    void timeoutReached_alwaysTimeout(
            @ForAll("captchaCodes") String code,
            @ForAll @IntRange(min = 0, max = 2) int wrongAttemptsBefore,
            @ForAll @IntRange(min = TIMEOUT_TICKS, max = TIMEOUT_TICKS + 2000) int elapsedTicks
    ) {
        // Build event sequence: some wrong attempts, then a tick that causes timeout
        List<CaptchaEvent> events = new java.util.ArrayList<>();

        // Add wrong attempts (less than MAX to not trigger FAILED_ATTEMPTS first)
        for (int i = 0; i < wrongAttemptsBefore; i++) {
            events.add(new CaptchaEvent.Submit("WRONG_" + i));
        }

        // Add tick that triggers timeout
        events.add(new CaptchaEvent.Tick(elapsedTicks));

        FsmResult result = runFsm(code, false, events);

        assertThat(result.outcome())
                .as("Elapsed >= timeout must yield TIMEOUT regardless of %d prior attempts",
                        wrongAttemptsBefore)
                .isEqualTo(FsmOutcome.TIMEOUT);
        assertThat(result.sideEffects())
                .as("TIMEOUT must trigger kick + cooldown")
                .isEqualTo(SideEffects.KICK_WITH_COOLDOWN);
    }

    // ─── Property 6d: arbitrary event sequences → deterministic outcome ───────────

    @Property
    void arbitraryEventSequence_deterministicOutcome(
            @ForAll("captchaCodes") String code,
            @ForAll("caseSensitivity") boolean caseSensitive,
            @ForAll("eventSequences") List<CaptchaEvent> events
    ) {
        FsmResult result = runFsm(code, caseSensitive, events);

        // Verify determinism: running the same sequence again yields the same result
        FsmResult result2 = runFsm(code, caseSensitive, events);
        assertThat(result).isEqualTo(result2);

        // Verify outcome matches rules
        switch (result.outcome()) {
            case SUCCESS -> {
                // There must exist a submit event that matches the code
                assertThat(result.sideEffects()).isEqualTo(SideEffects.NONE);
                assertThat(result.finalAttempts()).isLessThan(MAX_ATTEMPTS);
                assertThat(result.totalElapsed()).isLessThan(TIMEOUT_TICKS);
            }
            case FAILED_ATTEMPTS -> {
                // Must have exactly MAX_ATTEMPTS wrong submissions
                assertThat(result.finalAttempts()).isEqualTo(MAX_ATTEMPTS);
                assertThat(result.sideEffects()).isEqualTo(SideEffects.KICK_WITH_COOLDOWN);
                assertThat(result.totalElapsed()).isLessThan(TIMEOUT_TICKS);
            }
            case TIMEOUT -> {
                // Elapsed must be >= timeout
                assertThat(result.totalElapsed()).isGreaterThanOrEqualTo(TIMEOUT_TICKS);
                assertThat(result.sideEffects()).isEqualTo(SideEffects.KICK_WITH_COOLDOWN);
            }
            case RUNNING -> {
                // Not terminal: attempts < MAX and elapsed < timeout
                assertThat(result.finalAttempts()).isLessThan(MAX_ATTEMPTS);
                assertThat(result.totalElapsed()).isLessThan(TIMEOUT_TICKS);
                assertThat(result.sideEffects()).isEqualTo(SideEffects.NONE);
            }
        }
    }

    // ─── Property 6e: CaptchaSession state matches FSM model ──────────────────────

    @Property
    void captchaSession_stateMatchesFsmModel(
            @ForAll("captchaCodes") String code,
            @ForAll("eventSequences") List<CaptchaEvent> events
    ) {
        // Run through CaptchaSession (the real class) and compare with model
        CaptchaSession session = new CaptchaSession(code, 0, 0);
        boolean caseSensitive = false;

        int modelAttempts = 0;
        long modelElapsed = 0;
        FsmOutcome modelOutcome = FsmOutcome.RUNNING;

        for (CaptchaEvent event : events) {
            if (modelOutcome != FsmOutcome.RUNNING) break;

            switch (event) {
                case CaptchaEvent.Tick tick -> {
                    modelElapsed += tick.elapsedTicks();
                    if (modelElapsed >= TIMEOUT_TICKS) {
                        modelOutcome = FsmOutcome.TIMEOUT;
                    }
                }
                case CaptchaEvent.Submit submit -> {
                    boolean matches = caseSensitive
                            ? code.equals(submit.input())
                            : code.equalsIgnoreCase(submit.input());

                    if (matches) {
                        modelOutcome = FsmOutcome.SUCCESS;
                    } else {
                        session.incrementAttempts();
                        modelAttempts++;
                        if (modelAttempts >= MAX_ATTEMPTS) {
                            modelOutcome = FsmOutcome.FAILED_ATTEMPTS;
                        }
                    }
                }
            }
        }

        // Verify CaptchaSession state matches model
        assertThat(session.attempts())
                .as("CaptchaSession.attempts() must match model attempts count")
                .isEqualTo(modelAttempts);
        assertThat(session.code())
                .as("CaptchaSession.code() must remain unchanged")
                .isEqualTo(code);
        assertThat(session.startTickServer())
                .as("CaptchaSession.startTickServer() must remain unchanged")
                .isEqualTo(0L);
    }

    // ─── Generators ───────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> captchaCodes() {
        // Generate codes matching the default alphabet: A-Z0-9, length 6
        return Arbitraries.strings()
                .ofLength(6)
                .withCharRange('A', 'Z')
                .withCharRange('0', '9');
    }

    @Provide
    Arbitrary<Boolean> caseSensitivity() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<List<String>> wrongCodeSequences() {
        // Generate 3-5 codes that are unlikely to match any valid captcha code
        return Arbitraries.strings()
                .ofLength(6)
                .withCharRange('a', 'z') // lowercase only — won't match uppercase codes
                .list()
                .ofMinSize(3)
                .ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<CaptchaEvent>> eventSequences() {
        Arbitrary<CaptchaEvent> submitCorrectish = Arbitraries.strings()
                .ofLength(6)
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .map(CaptchaEvent.Submit::new);

        Arbitrary<CaptchaEvent> submitWrong = Arbitraries.strings()
                .ofLength(6)
                .withCharRange('a', 'z')
                .map(CaptchaEvent.Submit::new);

        Arbitrary<CaptchaEvent> tickSmall = Arbitraries.integers()
                .between(1, 600) // up to 30 seconds
                .map(CaptchaEvent.Tick::new);

        Arbitrary<CaptchaEvent> tickLarge = Arbitraries.integers()
                .between(1000, 5000) // 50-250 seconds
                .map(CaptchaEvent.Tick::new);

        Arbitrary<CaptchaEvent> anyEvent = Arbitraries.oneOf(
                submitCorrectish,
                submitWrong,
                tickSmall,
                tickLarge
        );

        return anyEvent.list().ofMinSize(1).ofMaxSize(10);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Randomly changes case of characters in the code (for case-insensitive testing).
     */
    private static String randomizeCase(String code) {
        StringBuilder sb = new StringBuilder(code.length());
        for (char c : code.toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append(Math.random() > 0.5 ? Character.toLowerCase(c) : Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
