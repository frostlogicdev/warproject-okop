package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Brigadier node for {@code /wp bio} — short, free-form biography text per
 * player. Used as RP flavour, visible to other players.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code /wp bio set <text>} — set/overwrite the executing player's bio
 *       (max {@link WarPlayerProfile#BIO_MAX_CHARS} chars). Greedy string so
 *       Cyrillic and multi-word text work without quoting.</li>
 *   <li>{@code /wp bio show} — show your own bio.</li>
 *   <li>{@code /wp bio show <player>} — show another (online or offline)
 *       player's bio.</li>
 *   <li>{@code /wp bio clear} — clear the executing player's bio.</li>
 * </ul>
 *
 * <p>Storage: persisted in {@link WarPlayerProfile#getBio()} via
 * {@link WarPlayerDataStore} ({@code players.json}). No DB schema change.
 *
 * <p>Audit: appended to {@code world/warproject/audit.log} via
 * {@link WarPlayerDataStore#appendAuditLog}. Length is logged, content is not
 * (privacy / log-size).
 *
 * <p>Permissions: open to any player. The bio is the player's own RP text.
 */
public final class BioCommands {

    private BioCommands() {
        // utility class
    }

    /** Registered from {@link com.frostlogic.warproject.server.command.WpCommandRoot}. */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        wpRoot.then(Commands.literal("bio")
                // /wp bio set <text>
                .then(Commands.literal("set")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(BioCommands::runSet)))
                // /wp bio clear
                .then(Commands.literal("clear")
                        .executes(BioCommands::runClear))
                // /wp bio show              -> own bio
                // /wp bio show <player>     -> another player's bio
                .then(Commands.literal("show")
                        .executes(BioCommands::runShowSelf)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(BioCommands::runShowOther))));
    }

    // ─── /wp bio set ──────────────────────────────────────────────────────────

    private static int runSet(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        String raw = StringArgumentType.getString(context, "text");
        if (raw == null || raw.trim().isEmpty()) {
            source.sendFailure(Component.translatable("wp.bio.empty_text"));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        boolean truncated = raw.trim().length() > WarPlayerProfile.BIO_MAX_CHARS;
        profile.setBio(raw);
        WarPlayerDataStore.get().save();

        // Audit (no content — only length, to keep audit.log compact and private).
        MinecraftServer server = source.getServer();
        WarPlayerDataStore.get().appendAuditLog(server,
                "BIO_SET " + player.getGameProfile().getName()
                        + " uuid=" + player.getStringUUID()
                        + " len=" + profile.getBio().length()
                        + (truncated ? " truncated=true" : ""));

        if (truncated) {
            source.sendSuccess(() -> Component.translatable("wp.bio.set_truncated",
                    WarPlayerProfile.BIO_MAX_CHARS).withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.translatable("wp.bio.set_success")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // ─── /wp bio clear ────────────────────────────────────────────────────────

    private static int runClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.hasBio()) {
            source.sendFailure(Component.translatable("wp.bio.already_empty"));
            return 0;
        }
        profile.setBio("");
        WarPlayerDataStore.get().save();

        WarPlayerDataStore.get().appendAuditLog(source.getServer(),
                "BIO_CLEAR " + player.getGameProfile().getName()
                        + " uuid=" + player.getStringUUID());

        source.sendSuccess(() -> Component.translatable("wp.bio.cleared")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    // ─── /wp bio show [self] ──────────────────────────────────────────────────

    private static int runShowSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        sendBio(source, profile.getRpNameOrUsername(), profile.getBio());
        return Command.SINGLE_SUCCESS;
    }

    // ─── /wp bio show <player> ────────────────────────────────────────────────

    private static int runShowOther(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        Optional<WarPlayerProfile> target = WarPlayerDataStore.get().findByName(source.getServer(), name);
        if (target.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        WarPlayerProfile profile = target.get();
        sendBio(source, profile.getRpNameOrUsername(), profile.getBio());
        return Command.SINGLE_SUCCESS;
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Sends a header + body (or empty notice). Always succeeds. */
    private static void sendBio(CommandSourceStack source, String displayName, String bio) {
        source.sendSuccess(() -> Component.translatable("wp.bio.header", displayName)
                .withStyle(ChatFormatting.GOLD), false);
        if (bio == null || bio.isBlank()) {
            source.sendSuccess(() -> Component.translatable("wp.bio.none")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            // Plain literal — biography content is user-supplied, must not be
            // re-translated. ChatFormatting.WHITE is implicit.
            source.sendSuccess(() -> Component.literal(bio), false);
        }
    }
}
