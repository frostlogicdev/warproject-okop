package com.frostlogic.warproject.server.command.argument;

import com.frostlogic.warproject.attachment.Role;
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
 * Custom Brigadier {@link ArgumentType} that parses a {@link Role} literal from the command line.
 * <p>
 * The role is matched against {@link Role#getSerializedName()} (e.g. {@code op},
 * {@code general}, {@code commander}, {@code soldier}, {@code candidate}) — case-insensitive
 * to ease typing. Suggestions list every available role.
 * <p>
 * The argument is intentionally <strong>not</strong> registered with
 * {@code ArgumentTypeInfos} because it is parsed as a plain string on the
 * server side and never has to round-trip across the network as a typed
 * argument: clients see Brigadier sync the underlying {@link StringReader}
 * read as a regular word argument while the server resolves it to a
 * {@link Role} during {@code parse(...)}. This avoids the additional
 * {@code SingletonArgumentInfo} registration boilerplate while keeping the
 * Brigadier API ergonomic for command builders.
 * <p>
 * Requirements: 10.1
 * Design: §7
 */
public final class RoleArgument implements ArgumentType<Role> {

    private static final Collection<String> EXAMPLES = List.of("commander", "general", "op");

    private static final DynamicCommandExceptionType ERROR_INVALID_ROLE =
            new DynamicCommandExceptionType(value ->
                    Component.translatable("wp.command.argument.role.invalid", String.valueOf(value)));

    private RoleArgument() {
        // factory-only
    }

    /**
     * Returns a fresh {@link RoleArgument} instance for use in command builders.
     */
    public static RoleArgument role() {
        return new RoleArgument();
    }

    /**
     * Retrieves the parsed {@link Role} for the named argument from the command context.
     */
    public static Role getRole(CommandContext<?> context, String name) {
        return context.getArgument(name, Role.class);
    }

    @Override
    public Role parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String input = reader.readUnquotedString();
        String normalized = input.toLowerCase(Locale.ROOT);

        for (Role role : Role.values()) {
            if (role.getSerializedName().equalsIgnoreCase(normalized)) {
                return role;
            }
        }

        // Restore the cursor so the error message points at the offending token.
        reader.setCursor(start);
        throw ERROR_INVALID_ROLE.createWithContext(reader, input);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Role role : Role.values()) {
            String name = role.getSerializedName();
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
     * Returns all role names. Exposed for tests and for {@code SharedSuggestionProvider}-based
     * callers that prefer a flat string list over the Brigadier suggestion mechanism.
     */
    public static List<String> allRoleNames() {
        return Arrays.stream(Role.values()).map(Role::getSerializedName).toList();
    }
}
