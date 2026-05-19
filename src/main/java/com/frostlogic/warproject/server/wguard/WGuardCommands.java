package com.frostlogic.warproject.server.wguard;

import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/**
 * Brigadier nodes for the WGuard admin commands.
 * <p>
 * {@code /wp wguard status [player]} — view violation levels for a player
 * {@code /wp wguard reset <player>} — reset violation levels for a player
 */
public final class WGuardCommands {

    private static WGuardService service;

    private WGuardCommands() {}

    public static void init(WGuardService svc) {
        service = svc;
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("wp")
                .then(Commands.literal("wguard")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(WGuardCommands::selfStatus)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(WGuardCommands::otherStatus)))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(WGuardCommands::resetVl)))));
    }

    private static int selfStatus(CommandContext<CommandSourceStack> context) {
        if (service == null) {
            context.getSource().sendFailure(Component.literal("WGuard service not available"));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        return showStatus(context.getSource(), player);
    }

    private static int otherStatus(CommandContext<CommandSourceStack> context) {
        if (service == null) {
            context.getSource().sendFailure(Component.literal("WGuard service not available"));
            return 0;
        }
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            return showStatus(context.getSource(), target);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer target) {
        UUID uuid = target.getUUID();
        WGuardService.PlayerViolations pv = service.getViolations(target);
        int total = pv.totalVl();

        source.sendSystemMessage(Component.literal("─── WGuard: " + target.getGameProfile().getName() + " ───")
                .withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal("Total VL: " + total)
                .withStyle(total > 30 ? ChatFormatting.RED : total > 10 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));

        for (WGuardService.CheckType check : WGuardService.CheckType.values()) {
            int vl = pv.checkVl(check);
            if (vl > 0) {
                source.sendSystemMessage(Component.literal("  " + check.id() + ": " + vl)
                        .withStyle(ChatFormatting.WHITE));
            }
        }

        return 1;
    }

    private static int resetVl(CommandContext<CommandSourceStack> context) {
        if (service == null) {
            context.getSource().sendFailure(Component.literal("WGuard service not available"));
            return 0;
        }
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            service.removePlayer(target.getUUID());
            context.getSource().sendSuccess(
                    () -> Component.literal("WGuard VL reset for " + target.getGameProfile().getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }
}
