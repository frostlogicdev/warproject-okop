package com.frostlogic.warproject.server.auth;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link PasswordHasher}.
 * <p>
 * Validates BCrypt round-trip, hash format, cost constraint, char[] zeroing,
 * and absence of password in log output.
 * <p>
 * <b>Validates: Requirements 2.5, 3.3, 18.3, 20.4, 21.4</b>
 * <p>
 * Design: §12 Property 3
 */
@PropertyDefaults(tries = 20)
class PasswordHasherProperties {

    private static final int TEST_COST = 10;

    private TestListAppender logAppender;

    @BeforeTry
    void attachLogAppender() {
        logAppender = new TestListAppender("PasswordHasherTestAppender");
        logAppender.start();

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig rootLogger = config.getRootLogger();
        rootLogger.addAppender(logAppender, null, null);
        ctx.updateLoggers();
    }

    @AfterTry
    void detachLogAppender() {
        if (logAppender != null) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig rootLogger = config.getRootLogger();
            rootLogger.removeAppender(logAppender.getName());
            ctx.updateLoggers();
            logAppender.stop();
        }
    }

    // --- Property 3a: round-trip verify(p, hash(p)) returns true ---

    @Property
    void roundTrip_verifyMatchesHash(@ForAll("passwords") String password) {
        char[] hashInput = password.toCharArray();
        String hashed = PasswordHasher.hash(hashInput, TEST_COST);

        char[] verifyInput = password.toCharArray();
        boolean result = PasswordHasher.verify(verifyInput, hashed);

        assertThat(result)
                .as("verify(p, hash(p)) must return true for password of length %d", password.length())
                .isTrue();
    }

    // --- Property 3b: hash format is $2a$<cost>$... with cost >= 10 ---

    @Property
    void hashFormat_startsWithCorrectPrefix(@ForAll("passwords") String password) {
        char[] input = password.toCharArray();
        String hashed = PasswordHasher.hash(input, TEST_COST);

        assertThat(hashed)
                .as("Hash must start with $2a$10$")
                .startsWith("$2a$10$");
    }

    // --- Property 3c: cost parsed from hash is always >= 10 ---

    @Property
    void hashCost_isAtLeastTen(
            @ForAll("passwords") String password,
            @ForAll @IntRange(min = 10, max = 12) int cost
    ) {
        char[] input = password.toCharArray();
        String hashed = PasswordHasher.hash(input, cost);

        // Parse cost from BCrypt hash: $2a$<2-digit-cost>$<22-char-salt><31-char-hash>
        assertThat(hashed).matches("\\$2a\\$\\d{2}\\$.*");
        int parsedCost = Integer.parseInt(hashed.substring(4, 6));

        assertThat(parsedCost)
                .as("Parsed cost from hash must be >= 10")
                .isGreaterThanOrEqualTo(10);
        assertThat(parsedCost)
                .as("Parsed cost must equal the requested cost")
                .isEqualTo(cost);
    }

    // --- Property 3d: char[] is zeroed after hash() ---

    @Property
    void charArrayZeroed_afterHash(@ForAll("passwords") String password) {
        char[] input = password.toCharArray();
        PasswordHasher.hash(input, TEST_COST);

        for (int i = 0; i < input.length; i++) {
            assertThat(input[i])
                    .as("char[%d] must be zeroed after hash()", i)
                    .isEqualTo('\0');
        }
    }

    // --- Property 3e: char[] is zeroed after verify() ---

    @Property
    void charArrayZeroed_afterVerify(@ForAll("passwords") String password) {
        // First hash to get a valid hash string
        String hashed = PasswordHasher.hash(password.toCharArray(), TEST_COST);

        char[] verifyInput = password.toCharArray();
        PasswordHasher.verify(verifyInput, hashed);

        for (int i = 0; i < verifyInput.length; i++) {
            assertThat(verifyInput[i])
                    .as("char[%d] must be zeroed after verify()", i)
                    .isEqualTo('\0');
        }
    }

    // --- Property 3f: password string does not appear in log output ---

    @Property
    void passwordNotInLogs(@ForAll("passwords") String password) {
        logAppender.clear();

        char[] hashInput = password.toCharArray();
        String hashed = PasswordHasher.hash(hashInput, TEST_COST);

        char[] verifyInput = password.toCharArray();
        PasswordHasher.verify(verifyInput, hashed);

        // Check that no log message contains the password
        for (LogEvent event : logAppender.getEvents()) {
            String message = event.getMessage().getFormattedMessage();
            assertThat(message)
                    .as("Log output must not contain the password")
                    .doesNotContain(password);
        }
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> passwords() {
        // Short passwords (6-20 chars) to keep BCrypt fast at cost=10
        return Arbitraries.strings()
                .ofMinLength(6)
                .ofMaxLength(20)
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('!', '@', '#', '$', '%', '^', '&', '*');
    }

    // --- Log4j2 ListAppender for capturing log events ---

    private static class TestListAppender extends AbstractAppender {

        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        TestListAppender(String name) {
            super(name, null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> getEvents() {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }

        void clear() {
            events.clear();
        }
    }
}
