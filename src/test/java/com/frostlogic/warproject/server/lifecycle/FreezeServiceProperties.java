package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.attachment.PlayerState;
import net.jqwik.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link FreezeService#shouldCancel(PlayerState)} and
 * {@link FreezeService#allowsCommand(String, PlayerState)}.
 * <p>
 * Verifies that freeze behavior and command whitelisting match the specification
 * from design §11 SP-8.
 * <p>
 * <b>Validates: Requirements 2.2, 3.2, 5.1, 5.2, 21.2, 21.3</b>
 * <p>
 * Design: §12 Property 5
 */
@PropertyDefaults(tries = 100)
class FreezeServiceProperties {

    /**
     * States that must be frozen (all actions blocked).
     */
    private static final Set<PlayerState> FROZEN_STATES = Set.of(
            PlayerState.NEW,
            PlayerState.REGISTERED_PENDING,
            PlayerState.LOGIN_PENDING,
            PlayerState.CAPTCHA,
            PlayerState.RPNAME_REQUIRED
    );

    /**
     * States that are NOT frozen (player has full freedom).
     */
    private static final Set<PlayerState> NON_FROZEN_STATES = Set.of(
            PlayerState.FACTIONLESS,
            PlayerState.CANDIDATE,
            PlayerState.ACCEPTED,
            PlayerState.CAPTURED
    );

    // --- Property 5a: shouldCancel returns true ⇔ state ∈ FROZEN_STATES ---

    @Property
    void shouldCancel_trueForFrozenStates(@ForAll("frozenStates") PlayerState state) {
        assertThat(FreezeService.shouldCancel(state))
                .as("shouldCancel must return true for frozen state %s", state)
                .isTrue();
    }

    // --- Property 5b: shouldCancel returns false ⇔ state ∈ NON_FROZEN_STATES ---

    @Property
    void shouldCancel_falseForNonFrozenStates(@ForAll("nonFrozenStates") PlayerState state) {
        assertThat(FreezeService.shouldCancel(state))
                .as("shouldCancel must return false for non-frozen state %s", state)
                .isFalse();
    }

    // --- Property 5c: for CAPTCHA state, only "wp captcha" and "wp captcha <anything>" are allowed ---

    @Property
    void captchaState_onlyWpCaptchaAllowed(@ForAll("commandNames") String cmdName) {
        boolean allowed = FreezeService.allowsCommand(cmdName, PlayerState.CAPTCHA);
        boolean expected = cmdName.equals("wp captcha") || cmdName.startsWith("wp captcha ");

        assertThat(allowed)
                .as("In CAPTCHA state, command '%s' should be %s",
                        cmdName, expected ? "allowed" : "denied")
                .isEqualTo(expected);
    }

    // --- Property 5d: for RPNAME_REQUIRED state, only "wp rpname" and "wp rpname <anything>" are allowed ---

    @Property
    void rpnameRequiredState_onlyWpRpnameAllowed(@ForAll("commandNames") String cmdName) {
        boolean allowed = FreezeService.allowsCommand(cmdName, PlayerState.RPNAME_REQUIRED);
        boolean expected = cmdName.equals("wp rpname") || cmdName.startsWith("wp rpname ");

        assertThat(allowed)
                .as("In RPNAME_REQUIRED state, command '%s' should be %s",
                        cmdName, expected ? "allowed" : "denied")
                .isEqualTo(expected);
    }

    // --- Property 5e: for NEW/REGISTERED_PENDING/LOGIN_PENDING, no commands are allowed ---

    @Property
    void noCommandStates_allCommandsDenied(
            @ForAll("noCommandStates") PlayerState state,
            @ForAll("commandNames") String cmdName
    ) {
        assertThat(FreezeService.allowsCommand(cmdName, state))
                .as("In state %s, command '%s' must be denied", state, cmdName)
                .isFalse();
    }

    // --- Property 5f: for non-frozen states, all commands are allowed ---

    @Property
    void nonFrozenStates_allCommandsAllowed(
            @ForAll("nonFrozenStates") PlayerState state,
            @ForAll("commandNames") String cmdName
    ) {
        assertThat(FreezeService.allowsCommand(cmdName, state))
                .as("In non-frozen state %s, command '%s' must be allowed", state, cmdName)
                .isTrue();
    }

    // --- Generators ---

    @Provide
    Arbitrary<PlayerState> frozenStates() {
        return Arbitraries.of(
                PlayerState.NEW,
                PlayerState.REGISTERED_PENDING,
                PlayerState.LOGIN_PENDING,
                PlayerState.CAPTCHA,
                PlayerState.RPNAME_REQUIRED
        );
    }

    @Provide
    Arbitrary<PlayerState> nonFrozenStates() {
        return Arbitraries.of(
                PlayerState.FACTIONLESS,
                PlayerState.CANDIDATE,
                PlayerState.ACCEPTED,
                PlayerState.CAPTURED
        );
    }

    @Provide
    Arbitrary<PlayerState> noCommandStates() {
        return Arbitraries.of(
                PlayerState.NEW,
                PlayerState.REGISTERED_PENDING,
                PlayerState.LOGIN_PENDING
        );
    }

    @Provide
    Arbitrary<String> commandNames() {
        // Mix of whitelisted commands, their subcommands, and arbitrary commands
        return Arbitraries.oneOf(
                // Exact whitelisted commands
                Arbitraries.of("wp captcha", "wp rpname"),
                // Whitelisted commands with arguments
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
                        .alpha().map(arg -> "wp captcha " + arg),
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
                        .alpha().map(arg -> "wp rpname " + arg),
                // Other wp subcommands (should be denied in frozen states)
                Arbitraries.of(
                        "wp ban", "wp kick", "wp mute", "wp accept",
                        "wp reload", "wp history", "wp up", "wp tp",
                        "wp generalchat hello", "wp collab", "wp uncollab"
                ),
                // Non-wp commands
                Arbitraries.of(
                        "help", "gamemode creative", "tp @s 0 64 0",
                        "give @s diamond", "kill", "say hello"
                ),
                // Random arbitrary command strings
                Arbitraries.strings().ofMinLength(1).ofMaxLength(30)
                        .withCharRange('a', 'z')
                        .withChars(' ')
        );
    }
}
