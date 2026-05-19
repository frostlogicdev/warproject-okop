package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.event.EventData;
import com.frostlogic.warproject.server.event.EventService;
import com.frostlogic.warproject.server.event.EventService.Result;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Brigadier nodes for the event-management subcommands of {@code /wp ...}.
 * <p>
 * Implements the commands listed in design §3 / Event System:
 * <ul>
 *   <li>{@code /wp ivent create '<event_name>'} (Admin/OP) — creates a new event.</li>
 *   <li>{@code /wp ivent setspawn} (Admin/OP) — sets event spawn to player's current position.</li>
 *   <li>{@code /wp ivent start} (Admin/OP) — starts the pending event, broadcasts announcement.</li>
 *   <li>{@code /wp ivent stop} (Admin/OP) — stops the active event, broadcasts completion.</li>
 *   <li>{@code /wp ivent delete '<event_name>'} (Admin/OP) — deletes an event by name.</li>
 *   <li>{@code /wp join ivent} (any player) — joins the currently active event.</li>
 * </ul>
 * <p>
 * <strong>Authorization.</strong> Admin commands require permission level 2 (vanilla OP).
 * The join command is available to any player.
 * <p>
 * Requirements: 7.1, 7.4, 8.1, 8.3, 9.1, 9.6
 * Design: §3 Event System
 */
public final class EventCommands {

    /** Brigadier permission level corresponding to vanilla OP (Admin). */
    private static final int OP_LEVEL = 2;

    private static EventService eventService;

    private EventCommands() {
        // utility class — no instantiation
    }

    /**
     * Wires up the static service dependency. Must be called from
     * {@code WpCommandRoot.onServerStarted} before commands run.
     */
    public static void init(EventService service) {
        eventService = service;
    }

    /**
     * Registers all event subcommands onto the given {@code /wp} root node.
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        // ── /wp ivent create <event_name> ───────────────────────────────────
        // ── /wp ivent setspawn ──────────────────────────────────────────────
        // ── /wp ivent start ─────────────────────────────────────────────────
        // ── /wp ivent stop ──────────────────────────────────────────────────
        // ── /wp ivent delete <event_name> ───────────────────────────────────
        wpRoot.then(Commands.literal("ivent")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.literal("create")
                        .then(Commands.argument("event_name", StringArgumentType.greedyString())
                                .executes(EventCommands::runCreate)))
                .then(Commands.literal("setspawn")
                        .executes(EventCommands::runSetSpawn))
                .then(Commands.literal("start")
                        .executes(EventCommands::runStart))
                .then(Commands.literal("stop")
                        .executes(EventCommands::runStop))
                .then(Commands.literal("delete")
                        .then(Commands.argument("event_name", StringArgumentType.greedyString())
                                .executes(EventCommands::runDelete))));

        // ── /wp join ivent ──────────────────────────────────────────────────
        wpRoot.then(Commands.literal("join")
                .then(Commands.literal("ivent")
                        .executes(EventCommands::runJoin)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * {@code /wp ivent create <event_name>} — creates a new event in PENDING state.
     */
    private static int runCreate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!serviceReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        String eventName = StringArgumentType.getString(context, "event_name");

        Result<EventData> result = eventService.create(eventName, player.getUUID());

        if (result instanceof Result.Failure<EventData> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        EventData created = ((Result.Success<EventData>) result).value();
        source.sendSuccess(() -> Component.literal("Ивент '" + created.name() + "' создан."), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp ivent setspawn} — sets the event spawn to the player's current position.
     */
    private static int runSetSpawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!serviceReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        Result<Void> result = eventService.setSpawn(
                player.getUUID(),
                player.blockPosition(),
                player.level().dimension()
        );

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Спавн ивента установлен на вашу позицию."), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp ivent start} — starts the pending event and broadcasts announcement.
     */
    private static int runStart(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!serviceReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        Result<Void> result = eventService.start(player.getUUID());

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        // Broadcast announcement to all players
        EventData activeEvent = eventService.getActiveEvent();
        if (activeEvent != null) {
            Component announcement = Component.literal(
                    "Ивент запущен '" + activeEvent.name() + "' чтоб попасть на ивент введите /wp join ivent"
            ).withStyle(ChatFormatting.GREEN);
            source.getServer().getPlayerList().broadcastSystemMessage(announcement, false);
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp ivent stop} — stops the active event and broadcasts completion.
     */
    private static int runStop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!serviceReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        // Capture event name before stopping (stop clears activeEvent)
        EventData activeEvent = eventService.getActiveEvent();
        String eventName = activeEvent != null ? activeEvent.name() : "unknown";

        Result<Void> result = eventService.stop(player.getUUID(), source.getServer());

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        // Broadcast completion message
        Component announcement = Component.literal(
                "Ивент '" + eventName + "' завершён!"
        ).withStyle(ChatFormatting.GOLD);
        source.getServer().getPlayerList().broadcastSystemMessage(announcement, false);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp ivent delete <event_name>} — deletes an event by name.
     */
    private static int runDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!serviceReady(source)) return 0;

        String eventName = StringArgumentType.getString(context, "event_name");

        Result<Void> result = eventService.delete(eventName);

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Ивент '" + eventName + "' удалён."), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp join ivent} — joins the currently active event (any player).
     */
    private static int runJoin(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!serviceReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        Result<Void> result = eventService.join(player);

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Вы присоединились к ивенту!"), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks that the EventService has been wired. If not, sends an error to the source.
     */
    private static boolean serviceReady(CommandSourceStack source) {
        if (eventService == null) {
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            WarProject.LOGGER.warn("[WP EventCommands] EventService is null — commands unavailable.");
            return false;
        }
        return true;
    }
}
