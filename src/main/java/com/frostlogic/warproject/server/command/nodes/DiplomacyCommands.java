package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.server.diplomacy.DiplomacyService;
import com.frostlogic.warproject.server.role.RoleResolver;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Brigadier nodes for the diplomacy subcommands of {@code /wp diplomacy ...}.
 * <p>
 * Implements the following commands:
 * <ul>
 *   <li>{@code /wp diplomacy truce propose <duration_minutes>} — GENERAL only, proposes truce</li>
 *   <li>{@code /wp diplomacy truce accept} — GENERAL only, accepts pending truce proposal</li>
 *   <li>{@code /wp diplomacy truce break} — GENERAL only, breaks active truce</li>
 *   <li>{@code /wp diplomacy exchange propose <own_prisoner> <enemy_prisoner>} — GENERAL only, proposes prisoner exchange</li>
 *   <li>{@code /wp diplomacy exchange accept} — GENERAL only, accepts pending exchange</li>
 *   <li>{@code /wp diplomacy exchange reject} — GENERAL only, rejects pending exchange</li>
 *   <li>{@code /wp diplomacy msg <message>} — GENERAL/OP only, sends diplomacy channel message</li>
 * </ul>
 * <p>
 * Requirements: 10.1, 10.2, 10.7, 11.1, 11.2, 11.5, 12.1, 12.2
 */
public final class DiplomacyCommands {

    private static DiplomacyService diplomacyService;

    private DiplomacyCommands() {
        // utility class — no instantiation
    }

    /**
     * Wires up the static service dependencies. Must be called from
     * {@code WpCommandRoot.onServerStarted} before commands run.
     */
    public static void init(DiplomacyService service) {
        diplomacyService = service;
    }

    /**
     * Registers all diplomacy subcommands onto the given {@code /wp} root node.
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        wpRoot.then(Commands.literal("diplomacy")
                // ── /wp diplomacy truce <propose|accept|break> ──────────────
                .then(Commands.literal("truce")
                        .then(Commands.literal("propose")
                                .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                                .then(Commands.argument("duration_minutes", IntegerArgumentType.integer(1))
                                        .executes(DiplomacyCommands::runTrucePropose)))
                        .then(Commands.literal("accept")
                                .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                                .executes(DiplomacyCommands::runTruceAccept))
                        .then(Commands.literal("break")
                                .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                                .executes(DiplomacyCommands::runTruceBreak)))
                // ── /wp diplomacy exchange <propose|accept|reject> ──────────
                .then(Commands.literal("exchange")
                        .then(Commands.literal("propose")
                                .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                                .then(Commands.argument("own_prisoner", StringArgumentType.string())
                                        .then(Commands.argument("enemy_prisoner", StringArgumentType.string())
                                                .executes(DiplomacyCommands::runExchangePropose))))
                        .then(Commands.literal("accept")
                                .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                                .executes(DiplomacyCommands::runExchangeAccept))
                        .then(Commands.literal("reject")
                                .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                                .executes(DiplomacyCommands::runExchangeReject)))
                // ── /wp diplomacy msg <message> ─────────────────────────────
                .then(Commands.literal("msg")
                        .requires(src -> RoleResolver.atLeast(src, Role.GENERAL))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(DiplomacyCommands::runMsg))));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * {@code /wp diplomacy truce propose <duration_minutes>} (Req. 10.1, 10.2).
     */
    private static int runTrucePropose(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        int durationMinutes = IntegerArgumentType.getInteger(context, "duration_minutes");

        DiplomacyService.Result<Void> result = diplomacyService.proposeTruce(player, durationMinutes);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.diplomacy.truce.propose.success", durationMinutes), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp diplomacy truce accept} (Req. 10.2).
     */
    private static int runTruceAccept(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        DiplomacyService.Result<Void> result = diplomacyService.acceptTruce(player);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.diplomacy.truce.accept.success"), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp diplomacy truce break} (Req. 10.7).
     */
    private static int runTruceBreak(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        DiplomacyService.Result<Void> result = diplomacyService.breakTruce(player);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.diplomacy.truce.break.success"), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp diplomacy exchange propose <own_prisoner> <enemy_prisoner>} (Req. 11.1, 11.2).
     */
    private static int runExchangePropose(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        String ownPrisoner = StringArgumentType.getString(context, "own_prisoner");
        String enemyPrisoner = StringArgumentType.getString(context, "enemy_prisoner");

        DiplomacyService.Result<Void> result = diplomacyService.proposeExchange(player, ownPrisoner, enemyPrisoner);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.diplomacy.exchange.propose.success", ownPrisoner, enemyPrisoner), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp diplomacy exchange accept} (Req. 11.5).
     */
    private static int runExchangeAccept(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        DiplomacyService.Result<Void> result = diplomacyService.acceptExchange(player);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.diplomacy.exchange.accept.success"), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp diplomacy exchange reject} (Req. 11.5).
     */
    private static int runExchangeReject(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        DiplomacyService.Result<Void> result = diplomacyService.rejectExchange(player);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.diplomacy.exchange.reject.success"), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp diplomacy msg <message>} (Req. 12.1, 12.2).
     */
    private static int runMsg(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");

        DiplomacyService.Result<Void> result = diplomacyService.sendDiplomacyMessage(player, message);
        if (result instanceof DiplomacyService.Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        // No explicit success message — the diplomacy channel message itself serves as confirmation
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if the service dependency has been wired.
     * Sends an error to the source if not ready.
     */
    private static boolean servicesReady(CommandSourceStack source) {
        if (diplomacyService == null) {
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return false;
        }
        return true;
    }
}
