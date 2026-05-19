package com.frostlogic.warproject.server.command.argument;

import com.frostlogic.warproject.attachment.FactionId;
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
 * Unit tests for {@link FactionArgument} parsing and suggestion behavior.
 * <p>
 * Validates: Requirements 10.1
 */
class FactionArgumentTest {

    @ParameterizedTest
    @EnumSource(FactionId.class)
    void parsesEveryFactionByItsSerializedName(FactionId faction) throws CommandSyntaxException {
        FactionArgument argument = FactionArgument.faction();
        StringReader reader = new StringReader(faction.getSerializedName());

        FactionId parsed = argument.parse(reader);

        assertThat(parsed).isEqualTo(faction);
        assertThat(reader.canRead()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(FactionId.class)
    void parseIsCaseInsensitive(FactionId faction) throws CommandSyntaxException {
        FactionArgument argument = FactionArgument.faction();
        StringReader reader = new StringReader(faction.getSerializedName().toUpperCase());

        FactionId parsed = argument.parse(reader);

        assertThat(parsed).isEqualTo(faction);
    }

    @Test
    void parseRejectsUnknownInput() {
        FactionArgument argument = FactionArgument.faction();
        StringReader reader = new StringReader("klingon");

        assertThatThrownBy(() -> argument.parse(reader))
                .isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void suggestionsListAllFactionsWhenInputIsEmpty() throws ExecutionException, InterruptedException {
        FactionArgument argument = FactionArgument.faction();
        SuggestionsBuilder builder = new SuggestionsBuilder("", 0);

        Suggestions suggestions = argument.listSuggestions(null, builder).get();
        List<String> texts = suggestions.getList().stream().map(Suggestion::getText).toList();

        assertThat(texts).containsExactlyInAnyOrderElementsOf(FactionArgument.allFactionNames());
    }

    @Test
    void suggestionsFilterByPrefix() throws ExecutionException, InterruptedException {
        FactionArgument argument = FactionArgument.faction();
        SuggestionsBuilder builder = new SuggestionsBuilder("zar", 0);

        Suggestions suggestions = argument.listSuggestions(null, builder).get();
        List<String> texts = suggestions.getList().stream().map(Suggestion::getText).toList();

        assertThat(texts).containsExactly("zarnavia");
    }
}
