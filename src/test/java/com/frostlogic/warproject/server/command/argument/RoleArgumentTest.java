package com.frostlogic.warproject.server.command.argument;

import com.frostlogic.warproject.attachment.Role;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RoleArgument} parsing and suggestion behavior.
 * <p>
 * Validates: Requirements 10.1
 */
class RoleArgumentTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void parsesEveryRoleByItsSerializedName(Role role) throws CommandSyntaxException {
        RoleArgument argument = RoleArgument.role();
        StringReader reader = new StringReader(role.getSerializedName());

        Role parsed = argument.parse(reader);

        assertThat(parsed).isEqualTo(role);
        assertThat(reader.canRead()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void parseIsCaseInsensitive(Role role) throws CommandSyntaxException {
        RoleArgument argument = RoleArgument.role();
        StringReader reader = new StringReader(role.getSerializedName().toUpperCase());

        Role parsed = argument.parse(reader);

        assertThat(parsed).isEqualTo(role);
    }

    @Test
    void parseRejectsUnknownInput() {
        RoleArgument argument = RoleArgument.role();
        StringReader reader = new StringReader("nope");

        assertThatThrownBy(() -> argument.parse(reader))
                .isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void suggestionsListAllRoleNamesWhenInputIsEmpty() throws ExecutionException, InterruptedException {
        RoleArgument argument = RoleArgument.role();
        SuggestionsBuilder builder = new SuggestionsBuilder("", 0);

        Suggestions suggestions = argument.listSuggestions(null, builder).get();
        List<String> texts = suggestions.getList().stream().map(Suggestion::getText).toList();

        assertThat(texts).containsExactlyInAnyOrderElementsOf(RoleArgument.allRoleNames());
    }

    @Test
    void suggestionsFilterByPrefix() throws ExecutionException, InterruptedException {
        RoleArgument argument = RoleArgument.role();
        SuggestionsBuilder builder = new SuggestionsBuilder("co", 0);

        Suggestions suggestions = argument.listSuggestions(null, builder).get();
        List<String> texts = suggestions.getList().stream().map(Suggestion::getText).toList();

        assertThat(texts).containsExactly("commander");
    }
}
