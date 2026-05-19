package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.auth.CaptchaService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Brigadier node for {@code /wp captcha <code>}.
 * <p>
 * Only accessible when the player is in {@link PlayerState#CAPTCHA} state.
 * Delegates to {@link CaptchaService#submit(ServerPlayer, String)} and returns
 * localized feedback. The submitted code is never written to logs or audit.
 * <p>
 * Requirements: 5.4, 10.2
 * Design: §7, §11 SP-4
 */
public final class CaptchaCommand {

    private static CaptchaService captchaService;

    private CaptchaCommand() {
        // utility class — no instantiation
    }

    /**
     * Initializes the command with the required service dependency.
     * Must be called before the command is executed (typically during server setup).
     *
     * @param service the CaptchaService instance
     */
    public static void init(CaptchaService service) {
        captchaService = service;
    }

    /**
     * Registers the {@code captcha <code>} subcommand onto the given {@code /wp} root node.
     *
     * @param wpRoot the literal "wp" command builder to attach the captcha subcommand to
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        wpRoot.then(Commands.literal("captcha")
                // Show the command in tab-completion if either pipeline has an
                // active session for this player. The legacy CaptchaManager
                // doesn't touch the new PlayerState attachment, so we OR the
                // two checks.
                .requires(CaptchaCommand::canSubmit)
                .then(Commands.argument("code", StringArgumentType.word())
                        .executes(CaptchaCommand::run)));
    }

    // ─── Execution ────────────────────────────────────────────────────────────────

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        String code = StringArgumentType.getString(context, "code");

        // Pipeline A — new CaptchaService (uses PlayerState attachment + DB).
        // Active when WGuardService route was followed (authoritative DB-backed
        // auth, see ServerEvents.onPlayerLoginAuthFlow).
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state == PlayerState.CAPTCHA && captchaService != null) {
            CaptchaService.SubmitResult result = captchaService.submit(player, code);
            return switch (result) {
                case CaptchaService.SubmitResult.Success ignored -> {
                    player.sendSystemMessage(Component.translatable("wp.captcha.success")
                            .withStyle(net.minecraft.ChatFormatting.GREEN));
                    yield Command.SINGLE_SUCCESS;
                }
                case CaptchaService.SubmitResult.WrongRetry retry -> {
                    source.sendFailure(Component.translatable("wp.captcha.wrong_code", retry.attemptsLeft()));
                    yield 0;
                }
                case CaptchaService.SubmitResult.FailedKick ignored -> 0;
                // Falls through to the legacy pipeline if the new service has no
                // pending session (rare race: legacy login fired captcha first).
                case CaptchaService.SubmitResult.NotPending ignored ->
                        runLegacy(source, player, code);
                default -> 0;
            };
        }

        // Pipeline B — legacy CaptchaManager (in-memory, used by WarLoginHandler).
        // Active when the legacy WarLoginHandler issued a captcha code via
        // CaptchaManager.issue. The two pipelines are exclusive in practice but
        // we never want a player to be locked out because of a routing edge.
        return runLegacy(source, player, code);
    }

    /**
     * Submits the code to the legacy CaptchaManager. Mirrors
     * WarProjectCommands.verifyCaptcha but lives here so the single Brigadier
     * node can dispatch to either backend.
     */
    private static int runLegacy(CommandSourceStack source, ServerPlayer player, String code) {
        com.frostlogic.warproject.server.WarPlayerProfile profile =
                com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(player);
        if (profile.isCaptchaPassed()) {
            source.sendFailure(Component.translatable("wp.captcha.passed_already"));
            return 0;
        }

        // Legacy CaptchaManager generates a 4-digit numeric code. If the user
        // typed something non-numeric, treat it the same as a wrong code (don't
        // surface NumberFormatException as a confusing "?"). Trim because some
        // clients add a trailing space when forwarding from the chat field.
        int parsed;
        try {
            parsed = Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            int used = com.frostlogic.warproject.server.CaptchaManager.getAttemptsUsed(player.getUUID());
            int left = Math.max(0, com.frostlogic.warproject.server.CaptchaManager.MAX_ATTEMPTS - used);
            source.sendFailure(Component.translatable("wp.captcha.wrong_code", left));
            return 0;
        }

        com.frostlogic.warproject.server.CaptchaManager.AttemptResult attempt =
                com.frostlogic.warproject.server.CaptchaManager.attempt(player, parsed);
        switch (attempt) {
            case NOT_PENDING -> {
                source.sendFailure(Component.translatable("wp.captcha.not_in_captcha"));
                return 0;
            }
            case WRONG_RETRY -> {
                int used = com.frostlogic.warproject.server.CaptchaManager.getAttemptsUsed(player.getUUID());
                int left = Math.max(0, com.frostlogic.warproject.server.CaptchaManager.MAX_ATTEMPTS - used);
                source.sendFailure(Component.translatable("wp.captcha.wrong_code", left));
                return 0;
            }
            case WRONG_KICK -> {
                com.frostlogic.warproject.server.WarPlayerDataStore.get().appendAuditLog(
                        source.getServer(),
                        player.getGameProfile().getName() + " failed captcha (kicked, 90s cooldown)");
                com.frostlogic.warproject.server.CaptchaManager.kickForFailure(player);
                return 0;
            }
            case SUCCESS -> {
                profile.setCaptchaPassed(true);
                com.frostlogic.warproject.server.CaptchaManager.finishSuccessKeepFlying(player);
                com.frostlogic.warproject.server.WarPlayerDataStore.get().appendAuditLog(
                        source.getServer(),
                        player.getGameProfile().getName() + " passed captcha");
                com.frostlogic.warproject.server.WarPlayerDataStore.get().save();
                player.sendSystemMessage(Component.translatable("wp.captcha.success")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                return Command.SINGLE_SUCCESS;
            }
        }
        return 0;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Predicate for {@code requires(...)}: lets the player run the command if
     * either the new {@link CaptchaService} pipeline or the legacy
     * {@link com.frostlogic.warproject.server.CaptchaManager} has a pending
     * session for them.
     */
    private static boolean canSubmit(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return false;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return false;
        }
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state == PlayerState.CAPTCHA) {
            return true;
        }
        // Legacy pipeline keeps captcha state on the WarPlayerProfile and the
        // CaptchaManager's in-memory ConcurrentHashMap. Either side is enough.
        com.frostlogic.warproject.server.WarPlayerProfile profile =
                com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(player);
        return !profile.isCaptchaPassed()
                && com.frostlogic.warproject.server.CaptchaManager.isPending(player.getUUID());
    }
}
