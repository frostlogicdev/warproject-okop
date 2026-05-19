package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Brigadier node for {@code /wp diary} — private, owner-only RP journal.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code /wp diary write <text>} — append a new entry. Trimmed and
 *       capped at {@link WarPlayerProfile#DIARY_ENTRY_MAX_CHARS} chars.
 *       FIFO drop when {@link WarPlayerProfile#DIARY_MAX_ENTRIES} exceeded.</li>
 *   <li>{@code /wp diary list} — show numbered list of own entries with
 *       timestamps and short previews.</li>
 *   <li>{@code /wp diary read <index>} — read full body of entry {@code index}
 *       (1-based, as shown in {@code list}).</li>
 *   <li>{@code /wp diary delete <index>} — remove entry {@code index}.</li>
 * </ul>
 *
 * <p>Privacy: a player's diary is visible <b>only to that player</b>. Unlike
 * {@code /wp bio} there is no way to view another player's diary in-game.
 *
 * <p>Storage: persisted in {@link WarPlayerProfile#getDiary()} via
 * {@link WarPlayerDataStore} ({@code players.json}). No DB schema change.
 *
 * <p>Audit: appended to {@code world/warproject/audit.log} via
 * {@link WarPlayerDataStore#appendAuditLog}. Length and index are logged,
 * content is NOT.
 */
public final class DiaryCommands {

    /** Format used for entry timestamps in {@code /wp diary list} / {@code read}. */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    /** Max preview length shown in {@code /wp diary list}. */
    private static final int LIST_PREVIEW_CHARS = 60;

    private DiaryCommands() {
        // utility class
    }

    /** Registered from {@link com.frostlogic.warproject.server.command.WpCommandRoot}. */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        wpRoot.then(Commands.literal("diary")
                .then(Commands.literal("write")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(DiaryCommands::runWrite)))
                .then(Commands.literal("list")
                        .executes(DiaryCommands::runList))
                .then(Commands.literal("read")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(DiaryCommands::runRead)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(DiaryCommands::runDelete))));
    }

    // ─── /wp diary write ──────────────────────────────────────────────────────

    private static int runWrite(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        String raw = StringArgumentType.getString(context, "text");
        if (raw == null || raw.trim().isEmpty()) {
            source.sendFailure(Component.translatable("wp.diary.empty_text"));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        int sizeBefore = profile.diarySize();
        WarPlayerProfile.DiaryEntry stored = profile.addDiaryEntry(raw);
        if (stored == null) {
            source.sendFailure(Component.translatable("wp.diary.empty_text"));
            return 0;
        }
        WarPlayerDataStore.get().save();

        boolean fifoDropped = sizeBefore >= WarPlayerProfile.DIARY_MAX_ENTRIES;
        boolean truncated = raw.trim().length() > WarPlayerProfile.DIARY_ENTRY_MAX_CHARS;
        WarPlayerDataStore.get().appendAuditLog(source.getServer(),
                "DIARY_WRITE " + player.getGameProfile().getName()
                        + " uuid=" + player.getStringUUID()
                        + " size=" + profile.diarySize()
                        + " len=" + stored.text().length()
                        + (truncated ? " truncated=true" : "")
                        + (fifoDropped ? " fifo_dropped=true" : ""));

        int newIndex = profile.diarySize();
        source.sendSuccess(() -> Component.translatable("wp.diary.added", newIndex)
                .withStyle(ChatFormatting.GREEN), false);
        if (truncated) {
            source.sendSuccess(() -> Component.translatable("wp.diary.entry_truncated",
                    WarPlayerProfile.DIARY_ENTRY_MAX_CHARS).withStyle(ChatFormatting.YELLOW), false);
        }
        if (fifoDropped) {
            source.sendSuccess(() -> Component.translatable("wp.diary.full_warning",
                    WarPlayerProfile.DIARY_MAX_ENTRIES).withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // ─── /wp diary list ───────────────────────────────────────────────────────

    private static int runList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        List<WarPlayerProfile.DiaryEntry> entries = profile.getDiary();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wp.diary.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("wp.diary.list_header",
                entries.size(), WarPlayerProfile.DIARY_MAX_ENTRIES)
                .withStyle(ChatFormatting.GOLD), false);

        for (int i = 0; i < entries.size(); i++) {
            WarPlayerProfile.DiaryEntry entry = entries.get(i);
            int oneBased = i + 1;
            String when = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp()));
            String preview = preview(entry.text(), LIST_PREVIEW_CHARS);
            source.sendSuccess(() -> Component.translatable("wp.diary.list_line",
                    oneBased, when, preview), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // ─── /wp diary read <index> ───────────────────────────────────────────────

    private static int runRead(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        int oneBased = IntegerArgumentType.getInteger(context, "index");
        List<WarPlayerProfile.DiaryEntry> entries = profile.getDiary();
        if (oneBased < 1 || oneBased > entries.size()) {
            source.sendFailure(Component.translatable("wp.diary.invalid_index", oneBased));
            return 0;
        }
        WarPlayerProfile.DiaryEntry entry = entries.get(oneBased - 1);
        String when = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp()));
        source.sendSuccess(() -> Component.translatable("wp.diary.read_header", oneBased, when)
                .withStyle(ChatFormatting.GOLD), false);
        // Plain literal — diary content is user-supplied, not a translation key.
        source.sendSuccess(() -> Component.literal(entry.text()), false);
        return Command.SINGLE_SUCCESS;
    }

    // ─── /wp diary delete <index> ─────────────────────────────────────────────

    private static int runDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        int oneBased = IntegerArgumentType.getInteger(context, "index");
        if (!profile.removeDiaryEntry(oneBased)) {
            source.sendFailure(Component.translatable("wp.diary.invalid_index", oneBased));
            return 0;
        }
        WarPlayerDataStore.get().save();

        WarPlayerDataStore.get().appendAuditLog(source.getServer(),
                "DIARY_DELETE " + player.getGameProfile().getName()
                        + " uuid=" + player.getStringUUID()
                        + " idx=" + oneBased
                        + " size=" + profile.diarySize());

        source.sendSuccess(() -> Component.translatable("wp.diary.deleted", oneBased)
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Trims to {@code max} chars, appending a single ellipsis if truncated. */
    private static String preview(String text, int max) {
        if (text == null) return "";
        // Replace internal newlines so list output stays single-line.
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max - 1) + "…";
    }
}
