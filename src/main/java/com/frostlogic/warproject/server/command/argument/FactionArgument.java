package com.frostlogic.warproject.server.command.argument;

import com.frostlogic.warproject.attachment.FactionId;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Custom Brigadier {@link ArgumentType} that parses a {@link FactionId} literal
 * from the command line.
 * <p>
 * Matched against {@link FactionId#getSerializedName()} (i.e. {@code zarnavia},
 * {@code chernogryad}) — case-insensitive. Suggestions list every faction.
 * <p>
 * Like {@link RoleArgument}, this argument is parsed entirely on the server
 * from a plain word token, so it does not require {@code ArgumentTypeInfo}
 * registration.
 * <p>
 * Requirements: 10.1
 * Design: §7
 */
public final class FactionArgument implements ArgumentType<FactionId> {

    private static final Collection<String> EXAMPLES = List.of("zarnavia", "chernogryad");

    private static final DynamicCommandExceptionType ERROR_INVALID_FACTION =
            new DynamicCommandExceptionType(value ->
                    Component.translatable("wp.command.argument.faction.invalid", String.valueOf(value)));

    private FactionArgument() {
        // factory-only
    }

    /**
     * Returns a fresh {@link FactionArgument} instance for use in command builders.
     */
    public static FactionArgument faction() {
        return new FactionArgument();
    }

    /**
     * Retrieves the parsed {@link FactionId} for the named argument from the command context.
     */
    public static FactionId getFaction(CommandContext<?> context, String name) {
        return context.getArgument(name, FactionId.class);
    }

    @Override
    public FactionId parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String input = reader.readUnquotedString();
        String normalized = input.toLowerCase(Locale.ROOT);

        for (FactionId faction : FactionId.values()) {
            if (faction.getSerializedName().equalsIgnoreCase(normalized)) {
                return faction;
            }
        }

        reader.setCursor(start);
        throw ERROR_INVALID_FACTION.createWithContext(reader, input);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (FactionId faction : FactionId.values()) {
            String name = faction.getSerializedName();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /**
     * Returns all faction names. Exposed for tests and string-based callers.
     */
    public static List<String> allFactionNames() {
        return Arrays.stream(FactionId.values()).map(FactionId::getSerializedName).toList();
    }
}
