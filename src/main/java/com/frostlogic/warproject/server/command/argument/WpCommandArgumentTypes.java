package com.frostlogic.warproject.server.command.argument;

import net.neoforged.bus.api.IEventBus;

/**
 * Placeholder for the (now-removed) custom Brigadier argument type registry.
 * <p>
 * Custom Brigadier {@code ArgumentType} implementations need both a registry
 * binding via {@link net.minecraft.core.registries.Registries#COMMAND_ARGUMENT_TYPE
 * COMMAND_ARGUMENT_TYPE} <em>and</em> a class binding via
 * {@code ArgumentTypeInfos.BY_CLASS} for the vanilla command-tree serializer to
 * round-trip them between server and client during the configuration phase.
 * Wiring only the registry side leaves the {@code BY_CLASS} map without an
 * entry for the argument class, so when the server pushes its command tree to
 * the client every command that uses the custom argument trips an
 * {@code IllegalArgumentException: Unrecognized argument type}, which the
 * client surfaces as the generic {@code multiplayer.disconnect.invalid_player_data}
 * (RU: «Неверные данные игрока») disconnect — not a useful error to debug from.
 * <p>
 * Rather than maintain that tightrope, the {@code /wp up <player> <role>} and
 * {@code /wp set comand <player> <faction>} subcommands now use a vanilla
 * {@link com.mojang.brigadier.arguments.StringArgumentType#word() string}
 * argument with custom suggestions and resolve the enum on the server. The
 * {@link RoleArgument} / {@link FactionArgument} classes are kept around as
 * plain {@code ArgumentType<T>} implementations for tests and any local-only
 * Brigadier wiring (their {@code parse(...)} reuses {@link com.mojang.brigadier.StringReader}
 * directly), but they are no longer registered as command-tree-syncable types.
 * <p>
 * Calling {@link #register(IEventBus)} is a no-op — kept only so older callers
 * that haven't been recompiled don't break.
 */
public final class WpCommandArgumentTypes {

    private WpCommandArgumentTypes() {
        // utility class — no instantiation
    }

    /**
     * No-op. The deferred register that used to live here was removed because
     * its argument types could not be safely synced to the client (see class
     * javadoc). New callers should not depend on this method.
     */
    public static void register(IEventBus modEventBus) {
        // no-op
    }
}
