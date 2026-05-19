package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.frostlogic.warproject.server.award.AwardDefinition;
import com.frostlogic.warproject.server.award.AwardsService;
import com.frostlogic.warproject.server.award.AwardsService.PlayerAward;
import com.frostlogic.warproject.server.award.AwardsService.Result;
import com.frostlogic.warproject.server.role.RoleResolver;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Brigadier nodes for the award-management subcommands of {@code /wp ...}.
 * <p>
 * Implements the following commands:
 * <ul>
 *   <li>{@code /wp award create '<award_name>' '<description>'} (Admin/OP) —
 *       creates a new award definition (Req. 13.1).</li>
 *   <li>{@code /wp award delete '<award_name>'} (Admin/OP) —
 *       deletes an award definition (Req. 13.6).</li>
 *   <li>{@code /wp award list} (Admin/OP) —
 *       lists all defined awards (Req. 13.8).</li>
 *   <li>{@code /wp award grant '<award_name>' <player>} (Commander/General) —
 *       grants an award to a player (Req. 14.1).</li>
 *   <li>{@code /wp awards} (any player) —
 *       shows the executing player's own awards (Req. 16.2).</li>
 *   <li>{@code /wp awards <player>} (any player) —
 *       shows the target player's awards (Req. 16.3).</li>
 * </ul>
 * <p>
 * <strong>Authorization.</strong> The Brigadier {@code requires(...)} clause acts
 * as a <em>visibility</em> gate. The actual permission check is repeated inside
 * {@code executes(...)} via {@link RoleResolver} so that a role change between
 * auto-complete and execution still gets rejected.
 * <p>
 * Requirements: 13.1, 13.6, 13.8, 14.1, 16.2, 16.3, 16.4, 16.5
 */
public final class AwardCommands {

    /** Brigadier permission level corresponding to vanilla OP. */
    private static final int OP_LEVEL = 2;

    /** Date format for award display (dd.MM.yyyy). */
    private static final String DATE_FORMAT = "dd.MM.yyyy";

    private static AwardsService awardsService;

    private AwardCommands() {
        // utility class — no instantiation
    }

    /**
     * Wires up the static service dependencies. Must be called from
     * {@code WpCommandRoot.onServerStarted} before commands run.
     */
    public static void init(AwardsService service) {
        awardsService = service;
    }

    /**
     * Registers all award subcommands onto the given {@code /wp} root node.
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        // ── /wp award create '<name>' '<description>' ───────────────────────
        // ── /wp award delete '<name>' ───────────────────────────────────────
        // ── /wp award list ──────────────────────────────────────────────────
        // ── /wp award grant '<name>' <player> ───────────────────────────────
        wpRoot.then(Commands.literal("award")
                .then(Commands.literal("create")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .then(Commands.argument("award_name", StringArgumentType.string())
                                .then(Commands.argument("description", StringArgumentType.string())
                                        .executes(AwardCommands::runCreate))))
                .then(Commands.literal("delete")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .then(Commands.argument("award_name", StringArgumentType.string())
                                .executes(AwardCommands::runDelete)))
                .then(Commands.literal("list")
                        .requires(src -> src.hasPermission(OP_LEVEL))
                        .executes(AwardCommands::runList))
                .then(Commands.literal("grant")
                        .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                        .then(Commands.argument("award_name", StringArgumentType.string())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(AwardCommands::runGrant)))));

        // ── /wp awards ──────────────────────────────────────────────────────
        // ── /wp awards <player> ─────────────────────────────────────────────
        wpRoot.then(Commands.literal("awards")
                .executes(AwardCommands::runAwardsSelf)
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(AwardCommands::runAwardsPlayer)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * {@code /wp award create '<award_name>' '<description>'} (Req. 13.1).
     */
    private static int runCreate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        if (!source.hasPermission(OP_LEVEL)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "award_name");
        String description = StringArgumentType.getString(context, "description");

        Result<AwardDefinition> result = awardsService.createAward(name, description);

        if (result instanceof Result.Failure<AwardDefinition> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        AwardDefinition award = result.orThrow();
        source.sendSuccess(() -> Component.translatable("wp.command.award.create.success", award.name())
                .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp award delete '<award_name>'} (Req. 13.6).
     */
    private static int runDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        if (!source.hasPermission(OP_LEVEL)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "award_name");

        Result<Void> result = awardsService.deleteAward(name);

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.award.delete.success", name)
                .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp award list} (Req. 13.8).
     */
    private static int runList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        if (!source.hasPermission(OP_LEVEL)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        List<AwardDefinition> awards = awardsService.listAwards();

        if (awards.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wp.command.award.list.empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.award.list.header", awards.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (AwardDefinition award : awards) {
            source.sendSuccess(() -> Component.literal("• " + award.name() + " — " + award.description())
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp award grant '<award_name>' <player>} (Req. 14.1).
     */
    private static int runGrant(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        if (!RoleResolver.atLeast(source, Role.COMMANDER)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer initiator = source.getPlayer();
        if (initiator == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        Optional<FactionId> initFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        if (initFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.award.no_faction"));
            return 0;
        }

        String awardName = StringArgumentType.getString(context, "award_name");
        String playerName = StringArgumentType.getString(context, "player");

        // Resolve target player by name
        Optional<WarPlayerProfile> targetProfile = WarPlayerDataStore.get()
                .findByName(source.getServer(), playerName);
        if (targetProfile.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", playerName));
            return 0;
        }

        WarPlayerProfile target = targetProfile.get();
        Result<Void> result = awardsService.grantAward(
                awardName,
                target.getUuid(),
                initiator.getUUID(),
                initFaction.get()
        );

        if (result instanceof Result.Failure<Void> failure) {
            source.sendFailure(Component.translatable(failure.errorKey()));
            return 0;
        }

        // Trigger post-grant side effects (broadcast, Military ID update)
        awardsService.onGrantSuccess(source.getServer(), awardName, target.getUuid(), initFaction.get());

        source.sendSuccess(() -> Component.translatable("wp.command.award.grant.success",
                awardName, target.getLastKnownName()).withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp awards} (Req. 16.2, 16.5).
     * Shows the executing player's own awards.
     */
    private static int runAwardsSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        List<PlayerAward> awards = awardsService.getAllPlayerAwards(player.getUUID());
        displayAwards(source, awards, null);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp awards <player>} (Req. 16.3, 16.4).
     * Shows the target player's awards.
     */
    private static int runAwardsPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String playerName = StringArgumentType.getString(context, "player");

        Optional<WarPlayerProfile> targetProfile = WarPlayerDataStore.get()
                .findByName(source.getServer(), playerName);
        if (targetProfile.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", playerName));
            return 0;
        }

        WarPlayerProfile target = targetProfile.get();
        List<PlayerAward> awards = awardsService.getAllPlayerAwards(target.getUuid());
        displayAwards(source, awards, target.getLastKnownName());
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Displays a list of awards to the command source.
     *
     * @param source     the command source to send messages to
     * @param awards     the list of awards to display
     * @param playerName the target player's name, or null if showing own awards
     */
    private static void displayAwards(CommandSourceStack source, List<PlayerAward> awards, String playerName) {
        if (awards.isEmpty()) {
            if (playerName == null) {
                source.sendSuccess(() -> Component.literal("У вас нет наград")
                        .withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendSuccess(() -> Component.literal("У игрока нет наград")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            return;
        }

        if (playerName == null) {
            source.sendSuccess(() -> Component.literal("Ваши награды:")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            source.sendSuccess(() -> Component.literal("Награды игрока " + playerName + ":")
                    .withStyle(ChatFormatting.GOLD), false);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        for (PlayerAward award : awards) {
            String dateStr = dateFormat.format(new Date(award.grantedAt()));
            source.sendSuccess(() -> Component.literal("• " + award.name() + " — " + award.description() + " (" + dateStr + ")")
                    .withStyle(ChatFormatting.WHITE), false);
        }
    }

    /**
     * Checks whether the AwardsService has been wired. If not, sends an error
     * to the command source and returns false.
     */
    private static boolean servicesReady(CommandSourceStack source) {
        if (awardsService == null) {
            WarProject.LOGGER.warn("[WP AwardCommands] AwardsService not initialized — command rejected.");
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return false;
        }
        return true;
    }
}
