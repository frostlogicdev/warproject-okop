package com.frostlogic.warproject.server.role;

import com.frostlogic.warproject.attachment.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit parameterized test verifying that for each (command, minRole) pair from the design §7 table,
 * {@link Role#atLeast(Role)} returns the expected result for all roles.
 * <p>
 * <b>Validates: Requirements 10.2</b>
 * <p>
 * Design: §7, §12 Property 12
 */
class RoleCommandTableTest {

    /**
     * Command-role table from design §7 / Req. 10.2.
     * Each entry: (commandName, minimumRole).
     * "captcha" and "rpname" are state-gated (any role), represented as CANDIDATE (lowest).
     */
    static Stream<Arguments> commandMinRoleTable() {
        return Stream.of(
                Arguments.of("/wp captcha", Role.CANDIDATE),
                Arguments.of("/wp rpname", Role.CANDIDATE),
                Arguments.of("/wp accept", Role.COMMANDER),
                Arguments.of("/wp up", Role.COMMANDER),
                Arguments.of("/wp set comand", Role.OP),
                Arguments.of("/wp ban", Role.OP),
                Arguments.of("/wp kick", Role.OP),
                Arguments.of("/wp mute", Role.OP),
                Arguments.of("/wp unban", Role.OP),
                Arguments.of("/wp unmute", Role.OP),
                Arguments.of("/wp warn", Role.COMMANDER),
                Arguments.of("/wp tp", Role.OP),
                Arguments.of("/wp tphere", Role.OP),
                Arguments.of("/wp reload", Role.OP),
                Arguments.of("/wp freeze", Role.OP),
                Arguments.of("/wp vanish", Role.OP),
                Arguments.of("/wp history", Role.OP),
                Arguments.of("/wp generalchat", Role.COMMANDER),
                Arguments.of("/wp collab", Role.OP),
                Arguments.of("/wp uncollab", Role.OP),
                Arguments.of("/wp create subdivision", Role.COMMANDER),
                Arguments.of("/wp delete subdivision", Role.COMMANDER)
        );
    }

    /**
     * For each (command, minRole) pair and for every role in the hierarchy,
     * verifies that role.atLeast(minRole) matches the expected canExecute result.
     */
    @ParameterizedTest(name = "{0} requires {1} — testing role {2}")
    @MethodSource("commandRoleCombinations")
    void canExecute_matchesDesignTable(String command, Role minRole, Role testRole, boolean expectedCanExecute) {
        boolean actual = testRole.atLeast(minRole);

        assertThat(actual)
                .as("Role %s should%s be able to execute '%s' (min: %s)",
                        testRole, expectedCanExecute ? "" : " NOT", command, minRole)
                .isEqualTo(expectedCanExecute);
    }

    /**
     * Generates all combinations of (command, minRole, testRole, expectedResult).
     */
    static Stream<Arguments> commandRoleCombinations() {
        return commandMinRoleTable().flatMap(args -> {
            String command = (String) args.get()[0];
            Role minRole = (Role) args.get()[1];

            return Stream.of(Role.values()).map(testRole -> {
                boolean expected = testRole.atLeast(minRole);
                return Arguments.of(command, minRole, testRole, expected);
            });
        });
    }
}
