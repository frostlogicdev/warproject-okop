package com.frostlogic.warproject.server.auth;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link WGuardService#validateRegistration}.
 * <p>
 * Validates the biconditional: {@code validateRegistration(pwd, confirm, cfg)} returns
 * {@link AuthResult.Ok} if and only if {@code pwd.equals(confirm) ∧ meetsComplexity(pwd, cfg)}.
 * <p>
 * <b>Validates: Requirements 2.3, 2.4</b>
 * <p>
 * Design: §12 Property 4
 */
@PropertyDefaults(tries = 100)
class WGuardValidationProperties {

    private static final AuthCfg DEFAULT_CFG = new AuthCfg(6, 32, "[A-Za-z0-9!@#$%^&*()_\\-+=]");

    // --- Property 4a: valid password with matching confirm returns Ok ---

    @Property
    void validPassword_matchingConfirm_returnsOk(@ForAll("validPasswords") String password) {
        char[] pwd = password.toCharArray();
        char[] confirm = password.toCharArray();

        AuthResult result = WGuardService.validateRegistration(pwd, confirm, DEFAULT_CFG);

        assertThat(result)
                .as("validateRegistration must return Ok for valid matching passwords")
                .isInstanceOf(AuthResult.Ok.class);
    }

    // --- Property 4b: mismatched passwords return Error ---

    @Property
    void mismatchedPasswords_returnsError(
            @ForAll("validPasswords") String password,
            @ForAll("validPasswords") String other
    ) {
        Assume.that(!password.equals(other));

        char[] pwd = password.toCharArray();
        char[] confirm = other.toCharArray();

        AuthResult result = WGuardService.validateRegistration(pwd, confirm, DEFAULT_CFG);

        assertThat(result)
                .as("validateRegistration must return Error when passwords don't match")
                .isInstanceOf(AuthResult.Error.class);
    }

    // --- Property 4c: password shorter than minLen returns Error ---

    @Property
    void passwordTooShort_returnsError(
            @ForAll("shortPasswords") String password
    ) {
        char[] pwd = password.toCharArray();
        char[] confirm = password.toCharArray();

        AuthResult result = WGuardService.validateRegistration(pwd, confirm, DEFAULT_CFG);

        assertThat(result)
                .as("validateRegistration must return Error for password shorter than minLen")
                .isInstanceOf(AuthResult.Error.class);
    }

    // --- Property 4d: password longer than maxLen returns Error ---

    @Property
    void passwordTooLong_returnsError(
            @ForAll("longPasswords") String password
    ) {
        char[] pwd = password.toCharArray();
        char[] confirm = password.toCharArray();

        AuthResult result = WGuardService.validateRegistration(pwd, confirm, DEFAULT_CFG);

        assertThat(result)
                .as("validateRegistration must return Error for password longer than maxLen")
                .isInstanceOf(AuthResult.Error.class);
    }

    // --- Property 4e: password with disallowed characters returns Error ---

    @Property
    void passwordWithInvalidChars_returnsError(
            @ForAll("passwordsWithInvalidChars") String password
    ) {
        char[] pwd = password.toCharArray();
        char[] confirm = password.toCharArray();

        AuthResult result = WGuardService.validateRegistration(pwd, confirm, DEFAULT_CFG);

        assertThat(result)
                .as("validateRegistration must return Error for password with disallowed characters")
                .isInstanceOf(AuthResult.Error.class);
    }

    // --- Property 4f (biconditional): result is Ok ⇔ (pwd == confirm ∧ meetsComplexity) ---

    @Property
    void biconditional_okIffMatchAndComplex(
            @ForAll("anyPasswords") String pwdStr,
            @ForAll("anyPasswords") String confirmStr,
            @ForAll("configs") AuthCfg cfg
    ) {
        char[] pwd = pwdStr.toCharArray();
        char[] confirm = confirmStr.toCharArray();

        AuthResult result = WGuardService.validateRegistration(pwd, confirm, cfg);

        boolean passwordsMatch = pwdStr.equals(confirmStr);
        boolean meetsComplexity = meetsComplexity(pwdStr, cfg);
        boolean shouldBeOk = passwordsMatch && meetsComplexity;

        assertThat(result.isOk())
                .as("Result.isOk() must be true iff passwords match AND meet complexity. " +
                    "pwd='%s', confirm='%s', match=%s, complexity=%s",
                    pwdStr, confirmStr, passwordsMatch, meetsComplexity)
                .isEqualTo(shouldBeOk);
    }

    // --- Helper: reference implementation of meetsComplexity ---

    private static boolean meetsComplexity(String password, AuthCfg cfg) {
        if (password.length() < cfg.minLen()) return false;
        if (password.length() > cfg.maxLen()) return false;
        String pattern = "^" + cfg.allowedChars() + "+$";
        return password.matches(pattern);
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .ofMinLength(6)
                .ofMaxLength(32)
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '-', '+', '=');
    }

    @Provide
    Arbitrary<String> shortPasswords() {
        // Passwords with length 1..5 (below minLen=6)
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(5)
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9');
    }

    @Provide
    Arbitrary<String> longPasswords() {
        // Passwords with length 33..50 (above maxLen=32)
        return Arbitraries.strings()
                .ofMinLength(33)
                .ofMaxLength(50)
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9');
    }

    @Provide
    Arbitrary<String> passwordsWithInvalidChars() {
        // Generate passwords of valid length but containing at least one disallowed char
        Arbitrary<String> validPart = Arbitraries.strings()
                .ofMinLength(5)
                .ofMaxLength(30)
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9');

        Arbitrary<Character> invalidChar = Arbitraries.of(
                '~', '`', '{', '}', '[', ']', '|', '\\', ':', ';', '"', '\'', '<', '>', ',', '.', '?', '/', ' '
        );

        return Combinators.combine(validPart, invalidChar).as((base, ch) -> {
            // Insert invalid char at a random position
            int pos = base.length() / 2;
            return base.substring(0, pos) + ch + base.substring(pos);
        });
    }

    @Provide
    Arbitrary<String> anyPasswords() {
        // Mix of valid, short, long, and invalid-char passwords for biconditional test
        return Arbitraries.oneOf(
                validPasswords(),
                shortPasswords(),
                longPasswords(),
                passwordsWithInvalidChars(),
                // Also include some edge-case empty strings
                Arbitraries.just("")
        );
    }

    @Provide
    Arbitrary<AuthCfg> configs() {
        Arbitrary<Integer> minLens = Arbitraries.integers().between(1, 10);
        Arbitrary<Integer> maxLens = Arbitraries.integers().between(10, 50);
        // Use the default allowed chars pattern — varying it would make the reference
        // implementation complex without adding value to the property under test
        return Combinators.combine(minLens, maxLens).as(
                (min, max) -> new AuthCfg(min, max, "[A-Za-z0-9!@#$%^&*()_\\-+=]")
        );
    }
}
