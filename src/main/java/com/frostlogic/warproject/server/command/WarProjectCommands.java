package com.frostlogic.warproject.server.command;

import com.frostlogic.warproject.env.WorldRelocationException;
import com.frostlogic.warproject.env.WorldRelocationTask;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.LegacyAttachmentBridge;
import com.frostlogic.warproject.server.NpcHandler;
import com.frostlogic.warproject.server.Rank;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.frostlogic.warproject.server.WarPrefixManager;
import com.frostlogic.warproject.server.WarServerSettings;
import com.frostlogic.warproject.server.WarpPoint;
import com.frostlogic.warproject.server.CaptchaManager;
import com.frostlogic.warproject.server.Country;
import com.frostlogic.warproject.server.PassportManager;
import com.frostlogic.warproject.server.SpawnTeleporter;
import com.frostlogic.warproject.server.Subdivision;
import com.frostlogic.warproject.server.SubdivisionStore;
import com.frostlogic.warproject.server.WarLoginHandler;
import java.nio.file.Path;
import java.util.UUID;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;
import java.util.regex.Pattern;

public final class WarProjectCommands {
    private static final long COMMANDER_CHAT_COOLDOWN_MS = 30_000L;
    private static final Pattern RP_NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\-']{1,23} [\\p{L}][\\p{L}\\-']{1,23}(?: [\\p{L}][\\p{L}\\-']{1,23})?$", Pattern.UNICODE_CHARACTER_CLASS);

    private WarProjectCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("wp")
                .requires(WarProjectCommands::sourceCanUseWp)
                .executes(WarProjectCommands::help)
                .then(Commands.literal("help").executes(WarProjectCommands::help))
                .then(Commands.literal("side")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Faction.playableIds(), builder))
                                .executes(context -> chooseSide(context.getSource(), StringArgumentType.getString(context, "faction")))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Rank.assignableIds(), builder))
                                        .executes(context -> invite(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "rank"), null))
                                        .then(Commands.argument("faction", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Faction.playableIds(), builder))
                                                .executes(context -> invite(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "rank"), StringArgumentType.getString(context, "faction")))))))
                .then(Commands.literal("uninvite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> uninvite(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "reason"))))))
                .then(Commands.literal("collab")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> collab(context.getSource(), StringArgumentType.getString(context, "player"), null))
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Faction.playableIds(), builder))
                                        .executes(context -> collab(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("chat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> commanderChat(context.getSource(), StringArgumentType.getString(context, "message")))))
                .then(Commands.literal("ao")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> adminAnnouncement(context.getSource(), StringArgumentType.getString(context, "message")))))
                .then(Commands.literal("profile")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> profile(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("setrank")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Rank.assignableIds(), builder))
                                        .executes(context -> setRank(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "rank"))))))
                .then(Commands.literal("setpoint")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("intro")
                                .executes(context -> setIntroPoint(context.getSource()))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> setIntroPointAt(context.getSource(), DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z")))))))
                        .then(Commands.literal("spawn")
                                .executes(context -> setSpawnPoint(context.getSource()))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> setSpawnPointAt(context.getSource(), DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z")))))))
                        .then(Commands.literal("base")
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Faction.playableIds(), builder))
                                        .executes(context -> setBasePoint(context.getSource(), StringArgumentType.getString(context, "faction")))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> setBasePointAt(context.getSource(), StringArgumentType.getString(context, "faction"), DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z")))))))))
                .then(Commands.literal("tp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("intro").executes(context -> teleportToNamed(context.getSource(), "intro", null)))
                        .then(Commands.literal("spawn").executes(context -> teleportToNamed(context.getSource(), "spawn", null)))
                        .then(Commands.literal("base")
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Faction.playableIds(), builder))
                                        .executes(context -> teleportToNamed(context.getSource(), "base", StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("npc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("create")
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Faction.playableIds(), builder))
                                        .executes(context -> createNpc(context.getSource(), StringArgumentType.getString(context, "faction"), null, null, null))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> createNpc(context.getSource(), StringArgumentType.getString(context, "faction"), DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z")))))))) 
                        .then(Commands.literal("remove").executes(context -> removeNpc(context.getSource()))))
                .then(Commands.literal("refresh")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> refreshAllPrefixes(context.getSource())))
                .then(Commands.literal("release")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> releaseCaptive(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("subdiv")
                        .then(Commands.literal("list").executes(context -> listSubdivisions(context.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> createSubdivision(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("disband")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> disbandSubdivision(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> inviteToSubdivision(context.getSource(), StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "name"))))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                        .executes(context -> kickFromSubdivision(context.getSource(), StringArgumentType.getString(context, "player"))))))
                .then(Commands.literal("env")
                        .then(Commands.literal("prepare")
                                .requires(source -> source.getServer() != null && !(source.getEntity() instanceof net.minecraft.world.entity.player.Player))
                                .executes(context -> prepareEnvironment(context.getSource()))))
                .then(Commands.literal("backup")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("now")
                                .executes(context -> backupNow(context.getSource())))));
    }

    private static int backupNow(CommandSourceStack source) {
        if (source.getServer() == null) {
            source.sendFailure(Component.literal("[WP] Сервер недоступен."));
            return 0;
        }
        source.sendSystemMessage(Component.literal("[WP] Запускаю бэкап мира…").withStyle(ChatFormatting.YELLOW));
        com.frostlogic.warproject.server.backup.WorldBackupService.snapshotAsync(source.getServer())
                .thenAccept(result -> {
                    // Fold the user-visible message back onto the server thread —
                    // CommandSourceStack is not thread-safe.
                    source.getServer().execute(() -> {
                        if (result instanceof com.frostlogic.warproject.server.backup.WorldBackupService.Result.Success ok) {
                            String mb = String.format(java.util.Locale.ROOT, "%.1f", ok.bytes() / (1024.0 * 1024.0));
                            source.sendSystemMessage(Component.literal(
                                    "[WP] Бэкап создан: " + ok.archive().getFileName() + " (" + mb + " МБ)")
                                    .withStyle(ChatFormatting.GREEN));
                        } else if (result instanceof com.frostlogic.warproject.server.backup.WorldBackupService.Result.Failure err) {
                            source.sendFailure(Component.literal("[WP] Бэкап не удался: " + err.message()));
                        }
                    });
                });
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean admin = isAdmin(source);
        boolean commander = false;
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            commander = WarPlayerDataStore.get().getOrCreate(player).canUseCommanderTools();
        }

        source.sendSystemMessage(Component.literal("══════ War Project — справка ══════").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        sendHelpSection(source, "ОБЩИЕ КОМАНДЫ", ChatFormatting.AQUA);
        sendHelpLine(source, "/wp help", "показать это меню");
        sendHelpLine(source, "/wp captcha <код>", "пройти капчу при первом входе");
        sendHelpLine(source, "/wp rpname <Имя Фамилия>", "задать ролевое имя (один раз)");
        sendHelpLine(source, "/wp side <сторона>", "выбрать сторону (или через NPC на спавне)");
        sendHelpLine(source, "/wp profile <player>", "посмотреть профиль игрока");
        sendHelpLine(source, "/wp subdiv list", "список всех подразделений");

        sendHelpSection(source, "ПЛЕН", ChatFormatting.LIGHT_PURPLE);
        sendHelpLine(source, "/wp release <player>", "освободить пленного (только сторона-захватчик или админ)");

        sendHelpSection(source, "УПРАВЛЕНИЕ (клавиатура)", ChatFormatting.GREEN);
        sendHelpLine(source, "H", "радиальное меню взаимодействия с игроком в прицеле:");
        sendHelpHint(source, "Документы — показать паспорт цели");
        sendHelpHint(source, "Плен — изъять паспорт и взять в плен");
        sendHelpHint(source, "Обыск — посмотреть инвентарь врага/коллаба/пленного");
        sendHelpHint(source, "Медпомощь — вылечить +4 HP союзнику");

        if (commander || admin) {
            sendHelpSection(source, "КОМАНДИРЫ СТОРОНЫ", ChatFormatting.YELLOW);
            sendHelpLine(source, "/wp invite <player> <rank> [faction]", "принять игрока в сторону с указанным званием");
            sendHelpLine(source, "/wp uninvite <player> <reason>", "уволить игрока со стороны");
            sendHelpLine(source, "/wp setrank <player> <rank>", "изменить звание игрока");
            sendHelpLine(source, "/wp collab <player> [faction]", "объявить коллаборационистом");
            sendHelpLine(source, "/wp chat <message>", "сообщение в командирский чат стороны");
            sendHelpLine(source, "/wp subdiv create <name>", "создать подразделение");
            sendHelpLine(source, "/wp subdiv disband <name>", "расформировать подразделение");
            sendHelpLine(source, "/wp subdiv invite <player> <name>", "зачислить бойца в подразделение");
            sendHelpLine(source, "/wp subdiv kick <player>", "исключить бойца из подразделения");
        }

        if (admin) {
            sendHelpSection(source, "АДМИНИСТРАЦИЯ", ChatFormatting.RED);
            sendHelpLine(source, "/wp ao <message>", "глобальное объявление от администрации");
            sendHelpLine(source, "/wp setpoint intro [x y z]", "установить точку капчи/интро");
            sendHelpLine(source, "/wp setpoint spawn [x y z]", "установить общую точку спавна");
            sendHelpLine(source, "/wp setpoint base <faction> [x y z]", "установить базу стороны");
            sendHelpLine(source, "/wp tp intro|spawn|base <faction>", "телепорт к точке");
            sendHelpLine(source, "/wp npc create <faction> [x y z]", "создать NPC выбора стороны");
            sendHelpLine(source, "/wp npc remove", "удалить ближайший NPC");
            sendHelpLine(source, "/wp refresh", "обновить префиксы у всех онлайн-игроков");
        }

        source.sendSystemMessage(Component.literal("═══════════════════════════════════").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static void sendHelpSection(CommandSourceStack source, String title, ChatFormatting color) {
        source.sendSystemMessage(Component.literal("» " + title).withStyle(color, ChatFormatting.BOLD));
    }

    private static void sendHelpLine(CommandSourceStack source, String command, String description) {
        Component line = Component.literal("  " + command + " ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("— " + description).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(line);
    }

    private static void sendHelpHint(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal("      • " + text).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int setRpName(CommandSourceStack source, String rawName) {
        return setRpNameDirect(source, rawName);
    }

    /**
     * Public entry point for the legacy RP-name flow. Called by both the legacy
     * command tree and the new {@code RpNameCommand} when the new lifecycle
     * services are not yet initialized.
     */
    public static int setRpNameDirect(CommandSourceStack source, String rawName) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        String rpName = normalizeSpaces(rawName);
        if (!RP_NAME_PATTERN.matcher(rpName).matches()) {
            source.sendFailure(Component.literal("[WP] РП имя должно быть формата: Имя Фамилия. Только буквы, 2-24 символа на часть."));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isCaptchaPassed()) {
            source.sendFailure(Component.literal("[WP] Сначала пройди капчу: /wp captcha <код>."));
            return 0;
        }
        profile.setRpName(rpName);
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), player.getGameProfile().getName() + " set RP name: " + rpName);
        WarPlayerDataStore.get().save();
        LegacyAttachmentBridge.sync(player);
        source.sendSystemMessage(Component.literal("[WP] РП имя сохранено: " + rpName).withStyle(ChatFormatting.GREEN));
        // Restore normal abilities (cancel sky-cage flight + invuln) and teleport
        // the player to the choice hall where the faction NPCs are placed.
        CaptchaManager.restoreNormalAbilities(player);
        // Teleport with explicit fallback chain so the player never gets dumped
        // out of the sky-cage with no destination (which used to drop them in
        // the void when admins forgot to /wp setpoint spawn).
        boolean teleported = WarServerSettings.get().getSpawnPoint()
                .map(point -> point.teleport(player))
                .orElse(false);
        if (!teleported) {
            // Fall back to the world spawn so the player ends up on solid ground.
            SpawnTeleporter.toWorldSpawn(player);
            source.sendSystemMessage(Component.literal("[WP] Точка зала выбора фракций не настроена админом — телепортировал на мировой спавн. Сообщи админу: /wp setpoint spawn.").withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(Component.literal("[WP] РП имя сохранено. Выбери фракцию у NPC.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int chooseSide(CommandSourceStack source, String rawFaction) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        Optional<Faction> faction = Faction.fromInput(rawFaction).filter(Faction::isPlayable);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестная сторона. Используй zarnavia или chernogryad."));
            return 0;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isCaptchaPassed()) {
            source.sendFailure(Component.literal("[WP] Сначала пройди капчу: /wp captcha <код>."));
            return 0;
        }
        if (!profile.hasRpName()) {
            source.sendFailure(Component.literal("[WP] Сначала введи РП имя: /wp rpname Имя Фамилия."));
            return 0;
        }
        if (profile.isFactionMember()) {
            source.sendFailure(Component.literal("[WP] Ты уже состоишь в стороне: " + profile.getFaction().displayName()));
            return 0;
        }

        profile.setCandidateFaction(faction.get());
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), player.getGameProfile().getName() + " chose candidate faction: " + faction.get().displayName());
        WarPlayerDataStore.get().save();
        LegacyAttachmentBridge.sync(player);
        PassportManager.issue(player, profile);
        WarPrefixManager.refresh(player);
        WarServerSettings.get().getBasePoint(faction.get()).ifPresent(point -> point.teleport(player));
        source.sendSystemMessage(Component.literal("[WP] Ты выбрал сторону-кандидата: " + faction.get().displayName() + ". Подойди к командиру для принятия.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int invite(CommandSourceStack source, String targetName, String rawRank, String rawFaction) {
        Optional<Rank> rank = Rank.fromInput(rawRank).filter(Rank::isAssignable);
        if (rank.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестное звание."));
            return 0;
        }

        Optional<Faction> requestedFaction = parseOptionalFaction(source, rawFaction);
        if (rawFaction != null && requestedFaction.isEmpty()) {
            return 0;
        }

        Optional<Faction> managingFaction = resolveManagingFaction(source, requestedFaction.orElse(null));
        if (managingFaction.isEmpty()) {
            return 0;
        }

        if (!canAssignRank(source, rank.get())) {
            source.sendFailure(Component.literal("[WP] Нельзя выдать звание выше или равное своему."));
            return 0;
        }

        Optional<WarPlayerProfile> target = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден. Он должен быть онлайн или уже иметь профиль War Project."));
            return 0;
        }

        WarPlayerProfile targetProfile = target.get();
        targetProfile.setFaction(managingFaction.get());
        targetProfile.setCandidateFaction(Faction.NONE);
        targetProfile.setRank(rank.get());
        targetProfile.setCollaborator(false);
        targetProfile.setCollaborationDeclaredBy(Faction.NONE);
        if (targetProfile.getAge() == 0) {
            targetProfile.setAge(Country.generateAge());
        }
        if (targetProfile.getBirthCountry() == Country.UNKNOWN) {
            targetProfile.setBirthCountry(Country.generateFor(managingFaction.get()));
        }
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " invited " + targetProfile.getLastKnownName() + " to " + managingFaction.get().displayName() + " as " + rank.get().displayName());
        WarPlayerDataStore.get().save();

        notifyOnline(source.getServer(), targetProfile, Component.literal("[WP] Ты принят в " + managingFaction.get().displayName() + ". Звание: " + rank.get().displayName()).withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(Component.literal("[WP] Игрок " + targetProfile.getLastKnownName() + " принят в " + managingFaction.get().displayName() + " как " + rank.get().displayName()).withStyle(ChatFormatting.GREEN));
        refreshPrefixIfOnline(source.getServer(), targetProfile);
        bridgeSyncIfOnline(source.getServer(), targetProfile);
        issuePassportIfOnline(source.getServer(), targetProfile);
        return 1;
    }

    private static int uninvite(CommandSourceStack source, String targetName, String reason) {
        Optional<WarPlayerProfile> target = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }

        WarPlayerProfile targetProfile = target.get();
        if (!targetProfile.isFactionMember()) {
            source.sendFailure(Component.literal("[WP] Игрок не состоит во фракции."));
            return 0;
        }

        if (!canManageFaction(source, targetProfile.getFaction())) {
            return 0;
        }

        Faction oldFaction = targetProfile.getFaction();
        targetProfile.setFaction(Faction.NONE);
        targetProfile.setCandidateFaction(Faction.NONE);
        targetProfile.setRank(Rank.NONE);
        String cleanReason = normalizeSpaces(reason);
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " uninvited " + targetProfile.getLastKnownName() + " from " + oldFaction.displayName() + ": " + cleanReason);
        WarPlayerDataStore.get().save();

        notifyOnline(source.getServer(), targetProfile, Component.literal("[WP] Ты уволен из " + oldFaction.displayName() + ". Причина: " + cleanReason).withStyle(ChatFormatting.RED));
        source.sendSystemMessage(Component.literal("[WP] Игрок " + targetProfile.getLastKnownName() + " уволен. Причина: " + cleanReason).withStyle(ChatFormatting.GREEN));
        refreshPrefixIfOnline(source.getServer(), targetProfile);
        bridgeSyncIfOnline(source.getServer(), targetProfile);
        removePassportIfOnline(source.getServer(), targetProfile);
        return 1;
    }

    private static int collab(CommandSourceStack source, String targetName, String rawFaction) {
        Optional<Faction> requestedFaction = parseOptionalFaction(source, rawFaction);
        if (rawFaction != null && requestedFaction.isEmpty()) {
            return 0;
        }

        Optional<Faction> declaringFaction = resolveManagingFaction(source, requestedFaction.orElse(null));
        if (declaringFaction.isEmpty()) {
            return 0;
        }

        Optional<WarPlayerProfile> target = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }

        WarPlayerProfile targetProfile = target.get();
        targetProfile.setCollaborator(true);
        targetProfile.setCollaborationDeclaredBy(declaringFaction.get());
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " declared " + targetProfile.getLastKnownName() + " collaborator by " + declaringFaction.get().displayName());
        WarPlayerDataStore.get().save();

        Component announcement = Component.literal("[WP] " + targetProfile.getLastKnownName() + " объявлен коллаборационистом стороной " + declaringFaction.get().displayName()).withStyle(ChatFormatting.DARK_RED);
        source.getServer().getPlayerList().broadcastSystemMessage(announcement, false);
        refreshPrefixIfOnline(source.getServer(), targetProfile);
        return 1;
    }

    private static int commanderChat(CommandSourceStack source, String message) {
        boolean admin = isAdmin(source);
        WarPlayerProfile senderProfile = null;
        Faction senderFaction = Faction.NONE;

        if (source.isPlayer()) {
            ServerPlayer player = requirePlayer(source);
            if (player == null) {
                return 0;
            }
            senderProfile = WarPlayerDataStore.get().getOrCreate(player);
            senderFaction = senderProfile.getFaction();
        }

        if (!admin && (senderProfile == null || !senderProfile.canUseCommanderTools())) {
            source.sendFailure(Component.literal("[WP] Командирский чат доступен только командирам и администрации."));
            return 0;
        }

        long now = System.currentTimeMillis();
        if (!admin && senderProfile != null) {
            long remainingMs = COMMANDER_CHAT_COOLDOWN_MS - (now - senderProfile.getLastCommanderChatAt());
            if (remainingMs > 0) {
                source.sendFailure(Component.literal("[WP] Подожди " + Math.ceil(remainingMs / 1000.0D) + " сек. перед следующим сообщением."));
                return 0;
            }
            senderProfile.setLastCommanderChatAt(now);
        }

        String prefix = senderFaction.isPlayable() ? senderFaction.displayName() : "Админ";
        Component formatted = Component.literal("[WP Командиры] [" + prefix + "] " + actorName(source) + ": " + normalizeSpaces(message)).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            WarPlayerProfile recipient = WarPlayerDataStore.get().getOrCreate(player);
            if (player.hasPermissions(2) || recipient.canUseCommanderTools()) {
                player.sendSystemMessage(formatted);
            }
        }

        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " commander chat: " + normalizeSpaces(message));
        WarPlayerDataStore.get().save();
        return 1;
    }

    private static int adminAnnouncement(CommandSourceStack source, String message) {
        Component announcement = Component.literal("[WP AO] " + normalizeSpaces(message)).withStyle(ChatFormatting.RED);
        source.getServer().getPlayerList().broadcastSystemMessage(announcement, false);
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " admin announcement: " + normalizeSpaces(message));
        return 1;
    }

    private static int profile(CommandSourceStack source, String targetName) {
        Optional<WarPlayerProfile> target = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }

        WarPlayerProfile profile = target.get();
        source.sendSystemMessage(Component.literal("[WP] Профиль: " + profile.getLastKnownName()).withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal("РП имя: " + (profile.hasRpName() ? profile.getRpName() : "не указано")));
        source.sendSystemMessage(Component.literal("Сторона: " + profile.getFaction().displayName()));
        source.sendSystemMessage(Component.literal("Кандидат: " + profile.getCandidateFaction().displayName()));
        source.sendSystemMessage(Component.literal("Звание: " + profile.getRank().displayName()));
        source.sendSystemMessage(Component.literal("Коллаборационист: " + (profile.isCollaborator() ? "да, объявлено стороной " + profile.getCollaborationDeclaredBy().displayName() : "нет")));
        return 1;
    }

    private static int setRank(CommandSourceStack source, String targetName, String rawRank) {
        Optional<Rank> rank = Rank.fromInput(rawRank).filter(Rank::isAssignable);
        if (rank.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестное звание."));
            return 0;
        }

        Optional<WarPlayerProfile> target = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }

        WarPlayerProfile targetProfile = target.get();
        if (!targetProfile.isFactionMember()) {
            source.sendFailure(Component.literal("[WP] Игрок не состоит во фракции."));
            return 0;
        }

        if (!canManageFaction(source, targetProfile.getFaction()) || !canAssignRank(source, rank.get())) {
            return 0;
        }

        targetProfile.setRank(rank.get());
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " set rank for " + targetProfile.getLastKnownName() + " to " + rank.get().displayName());
        WarPlayerDataStore.get().save();
        notifyOnline(source.getServer(), targetProfile, Component.literal("[WP] Твоё новое звание: " + rank.get().displayName()).withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(Component.literal("[WP] Звание игрока " + targetProfile.getLastKnownName() + " изменено на " + rank.get().displayName()).withStyle(ChatFormatting.GREEN));
        refreshPrefixIfOnline(source.getServer(), targetProfile);
        bridgeSyncIfOnline(source.getServer(), targetProfile);
        updatePassportIfOnline(source.getServer(), targetProfile);
        return 1;
    }

    private static Optional<Faction> parseOptionalFaction(CommandSourceStack source, String rawFaction) {
        if (rawFaction == null) {
            return Optional.empty();
        }
        if (!isAdmin(source)) {
            source.sendFailure(Component.literal("[WP] Указывать сторону вручную может только администратор."));
            return Optional.empty();
        }
        Optional<Faction> faction = Faction.fromInput(rawFaction).filter(Faction::isPlayable);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестная сторона. Используй zarnavia или chernogryad."));
        }
        return faction;
    }

    private static Optional<Faction> resolveManagingFaction(CommandSourceStack source, Faction requestedFaction) {
        if (requestedFaction != null) {
            if (isAdmin(source) && requestedFaction.isPlayable()) {
                return Optional.of(requestedFaction);
            }
            source.sendFailure(Component.literal("[WP] Эту сторону нельзя использовать."));
            return Optional.empty();
        }

        if (source.isPlayer()) {
            ServerPlayer player = requirePlayer(source);
            if (player == null) {
                return Optional.empty();
            }
            WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
            if (profile.canUseCommanderTools()) {
                return Optional.of(profile.getFaction());
            }
            if (isAdmin(source) && profile.isFactionMember()) {
                return Optional.of(profile.getFaction());
            }
        }

        if (isAdmin(source)) {
            source.sendFailure(Component.literal("[WP] Админу без стороны нужно указать сторону: /wp invite <ник> <звание> <сторона>."));
        } else {
            source.sendFailure(Component.literal("[WP] Команда доступна только командирам стороны."));
        }
        return Optional.empty();
    }

    private static boolean canManageFaction(CommandSourceStack source, Faction faction) {
        if (isAdmin(source)) {
            return true;
        }

        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("[WP] Команда доступна только игроку-командиру или администратору."));
            return false;
        }

        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return false;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (profile.canUseCommanderTools() && profile.getFaction() == faction) {
            return true;
        }

        source.sendFailure(Component.literal("[WP] Ты можешь управлять только своей стороной и только будучи командиром."));
        return false;
    }

    private static boolean canAssignRank(CommandSourceStack source, Rank rank) {
        if (isAdmin(source)) {
            return true;
        }

        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("[WP] Звания может выдавать только командир или администратор."));
            return false;
        }

        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return false;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        return profile.canUseCommanderTools() && profile.getRank().weight() > rank.weight();
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static boolean sourceCanUseWp(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        return WarPlayerDataStore.get().getOrCreate(player).isLoggedIn();
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[WP] Эта команда доступна только игроку."));
        }
        return player;
    }

    private static String actorName(CommandSourceStack source) {
        return source.getTextName();
    }

    private static String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static void notifyOnline(MinecraftServer server, WarPlayerProfile profile, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getUuid());
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    private static void refreshPrefixIfOnline(MinecraftServer server, WarPlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getUuid());
        if (player != null) {
            WarPrefixManager.refresh(player);
        }
    }

    private static void issuePassportIfOnline(MinecraftServer server, WarPlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getUuid());
        if (player != null) {
            PassportManager.issue(player, profile);
        }
    }

    private static void removePassportIfOnline(MinecraftServer server, WarPlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getUuid());
        if (player != null) {
            PassportManager.remove(player);
        }
    }

    private static void updatePassportIfOnline(MinecraftServer server, WarPlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getUuid());
        if (player != null) {
            PassportManager.updateAll(player, profile);
        }
    }

    private static void bridgeSyncIfOnline(MinecraftServer server, WarPlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getUuid());
        if (player != null) {
            LegacyAttachmentBridge.sync(player);
        }
    }

    private static int verifyCaptcha(CommandSourceStack source, int code) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (profile.isCaptchaPassed()) {
            source.sendFailure(Component.translatable("wp.captcha.passed_already"));
            return 0;
        }

        CaptchaManager.AttemptResult result = CaptchaManager.attempt(player, code);
        switch (result) {
            case NOT_PENDING -> {
                source.sendFailure(Component.translatable("wp.captcha.not_in_captcha"));
                return 0;
            }
            case WRONG_RETRY -> {
                int used = CaptchaManager.getAttemptsUsed(player.getUUID());
                int left = Math.max(0, CaptchaManager.MAX_ATTEMPTS - used);
                source.sendFailure(Component.translatable("wp.captcha.wrong_code", left)
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            case WRONG_KICK -> {
                WarPlayerDataStore.get().appendAuditLog(source.getServer(),
                        player.getGameProfile().getName() + " failed captcha (kicked, 90s cooldown)");
                CaptchaManager.kickForFailure(player);
                return 0;
            }
            case SUCCESS -> {
                profile.setCaptchaPassed(true);
                CaptchaManager.finishSuccessKeepFlying(player);
                WarPlayerDataStore.get().appendAuditLog(source.getServer(),
                        player.getGameProfile().getName() + " passed captcha");
                WarPlayerDataStore.get().save();
                player.sendSystemMessage(Component.translatable("wp.captcha.success")
                        .withStyle(ChatFormatting.GREEN));
                return 1;
            }
        }
        return 0;
    }

    /**
     * Validates a coordinate triple before saving it as a warp point.
     * Returns null on success, or a localized error message describing the
     * problem (Y out of build limit, X/Z outside the world border, etc.).
     */
    private static Component validateWarpCoords(ServerPlayer player, double x, double y, double z) {
        // Sanity-check the build height. Below -64 or above 320 is outside the
        // overworld build limit (and even more restrictive in the Nether).
        // Even with custom dimensions, refusing absurd Y values prevents an
        // admin typo from sending players into the void.
        if (y < -128 || y > 1024) {
            return Component.literal("[WP] Y координата вне допустимого диапазона (-128..1024).");
        }
        // Reject points outside the world border — Minecraft will silently snap
        // teleports back inside, which means the saved point would diverge
        // from the actual landing spot.
        try {
            net.minecraft.world.level.border.WorldBorder border = player.serverLevel().getWorldBorder();
            if (x < border.getMinX() || x > border.getMaxX()
                    || z < border.getMinZ() || z > border.getMaxZ()) {
                return Component.literal("[WP] Координаты вне world border.");
            }
        } catch (Throwable ignored) {
            // World border lookup failure is non-fatal; the basic Y check still ran.
        }
        return null;
    }

    private static int setIntroPoint(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarServerSettings.get().setIntroPoint(WarpPoint.of(player));
        source.sendSystemMessage(Component.literal("[WP] Точка intro установлена в твоём положении.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setIntroPointAt(CommandSourceStack source, double x, double y, double z) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Component err = validateWarpCoords(player, x, y, z);
        if (err != null) {
            source.sendFailure(err);
            return 0;
        }
        WarpPoint point = new WarpPoint(player.serverLevel().dimension(), x, y, z, player.getYRot(), player.getXRot());
        WarServerSettings.get().setIntroPoint(point);
        source.sendSystemMessage(Component.literal("[WP] Точка intro установлена: " + x + ", " + y + ", " + z).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setSpawnPoint(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarServerSettings.get().setSpawnPoint(WarpPoint.of(player));
        source.sendSystemMessage(Component.literal("[WP] Точка spawn установлена в твоём положении.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setSpawnPointAt(CommandSourceStack source, double x, double y, double z) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Component err = validateWarpCoords(player, x, y, z);
        if (err != null) {
            source.sendFailure(err);
            return 0;
        }
        WarpPoint point = new WarpPoint(player.serverLevel().dimension(), x, y, z, player.getYRot(), player.getXRot());
        WarServerSettings.get().setSpawnPoint(point);
        source.sendSystemMessage(Component.literal("[WP] Точка spawn установлена: " + x + ", " + y + ", " + z).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setBasePoint(CommandSourceStack source, String rawFaction) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Optional<Faction> faction = Faction.fromInput(rawFaction).filter(Faction::isPlayable);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестная сторона."));
            return 0;
        }
        WarServerSettings.get().setBasePoint(faction.get(), WarpPoint.of(player));
        source.sendSystemMessage(Component.literal("[WP] База " + faction.get().displayName() + " установлена в твоём положении.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setBasePointAt(CommandSourceStack source, String rawFaction, double x, double y, double z) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Optional<Faction> faction = Faction.fromInput(rawFaction).filter(Faction::isPlayable);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестная сторона."));
            return 0;
        }
        Component err = validateWarpCoords(player, x, y, z);
        if (err != null) {
            source.sendFailure(err);
            return 0;
        }
        WarpPoint point = new WarpPoint(player.serverLevel().dimension(), x, y, z, player.getYRot(), player.getXRot());
        WarServerSettings.get().setBasePoint(faction.get(), point);
        source.sendSystemMessage(Component.literal("[WP] База " + faction.get().displayName() + " установлена: " + x + ", " + y + ", " + z).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int teleportToNamed(CommandSourceStack source, String name, String rawFaction) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Optional<WarpPoint> point;
        switch (name) {
            case "intro" -> point = WarServerSettings.get().getIntroPoint();
            case "spawn" -> point = WarServerSettings.get().getSpawnPoint();
            case "base" -> {
                Optional<Faction> faction = Faction.fromInput(rawFaction).filter(Faction::isPlayable);
                if (faction.isEmpty()) {
                    source.sendFailure(Component.literal("[WP] Неизвестная сторона."));
                    return 0;
                }
                point = WarServerSettings.get().getBasePoint(faction.get());
            }
            default -> point = Optional.empty();
        }
        if (point.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Точка не установлена. Используй /wp setpoint."));
            return 0;
        }
        point.get().teleport(player);
        return 1;
    }

    private static int createNpc(CommandSourceStack source, String rawFaction, Double x, Double y, Double z) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Optional<Faction> faction = Faction.fromInput(rawFaction).filter(Faction::isPlayable);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Неизвестная сторона."));
            return 0;
        }
        boolean ok;
        if (x == null || y == null || z == null) {
            ok = NpcHandler.spawnSideNpc(player, faction.get());
        } else {
            ok = NpcHandler.spawnSideNpcAt(player, faction.get(), x, y, z);
        }
        if (!ok) {
            source.sendFailure(Component.literal("[WP] Не удалось спавнить NPC."));
            return 0;
        }
        source.sendSystemMessage(Component.literal("[WP] NPC стороны создан: " + faction.get().displayName()).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int removeNpc(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        int removed = NpcHandler.removeNearestSideNpc(player);
        if (removed == 0) {
            source.sendFailure(Component.literal("[WP] Рядом нет WP NPC."));
            return 0;
        }
        source.sendSystemMessage(Component.literal("[WP] Удалено NPC: " + removed).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int refreshAllPrefixes(CommandSourceStack source) {
        WarPrefixManager.refreshAll(source.getServer());
        source.sendSystemMessage(Component.literal("[WP] Префиксы обновлены для всех онлайн-игроков.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int releaseCaptive(CommandSourceStack source, String targetName) {
        ServerPlayer actor = source.getPlayer();
        Optional<WarPlayerProfile> targetOpt = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }
        WarPlayerProfile target = targetOpt.get();
        if (!target.isCaptive()) {
            source.sendFailure(Component.literal("[WP] Игрок не в плену."));
            return 0;
        }
        if (!isAdmin(source)) {
            if (actor == null) {
                source.sendFailure(Component.literal("[WP] Команда доступна только игроку или администратору."));
                return 0;
            }
            WarPlayerProfile actorProfile = WarPlayerDataStore.get().getOrCreate(actor);
            UUID capturedBy = target.getCapturedBy();
            boolean sameSide = capturedBy != null
                    && WarPlayerDataStore.get().findByUuid(capturedBy)
                        .map(p -> p.getFaction() == actorProfile.getFaction())
                        .orElse(false);
            if (!sameSide) {
                source.sendFailure(Component.literal("[WP] Освободить пленного может только сторона, которая его взяла."));
                return 0;
            }
        }
        target.setCaptive(false);
        target.setCapturedBy(null);
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " released " + target.getLastKnownName());
        WarPlayerDataStore.get().save();
        notifyOnline(source.getServer(), target, Component.literal("[WP] Тебя освободили из плена.").withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(Component.literal("[WP] " + target.getLastKnownName() + " освобождён из плена.").withStyle(ChatFormatting.GREEN));
        // Refresh TAB prefix so the [ПЛЕН] tag drops off if the released
        // player is online.
        ServerPlayer onlineTarget = source.getServer() != null
                ? source.getServer().getPlayerList().getPlayer(target.getUuid()) : null;
        if (onlineTarget != null) {
            WarPrefixManager.refresh(onlineTarget);
        }
        return 1;
    }

    private static int listSubdivisions(CommandSourceStack source) {
        var all = SubdivisionStore.get().all();
        if (all.isEmpty()) {
            source.sendSystemMessage(Component.literal("[WP] Подразделений нет.").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        source.sendSystemMessage(Component.literal("[WP] Подразделения:").withStyle(ChatFormatting.GOLD));
        for (Subdivision subdivision : all) {
            String commanderName = "—";
            if (subdivision.getCommanderUuid() != null) {
                Optional<WarPlayerProfile> commanderProfile = WarPlayerDataStore.get().findByUuid(subdivision.getCommanderUuid());
                commanderName = commanderProfile.map(WarPlayerProfile::getRpNameOrUsername).orElse(subdivision.getCommanderUuid().toString().substring(0, 8));
            }
            source.sendSystemMessage(Component.literal("  • " + subdivision.getName() + " [" + subdivision.getFaction().displayName() + "] — " + commanderName + " (" + subdivision.getMembers().size() + " чел.)").withStyle(subdivision.getFaction().color()));
        }
        return 1;
    }

    private static int createSubdivision(CommandSourceStack source, String name) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!isAdmin(source) && !profile.canUseCommanderTools()) {
            source.sendFailure(Component.literal("[WP] Создавать подразделения может только командир стороны."));
            return 0;
        }
        if (!profile.isFactionMember()) {
            source.sendFailure(Component.literal("[WP] Ты должен быть в стороне."));
            return 0;
        }
        String trimmed = normalizeSpaces(name);
        if (trimmed.length() < 2 || trimmed.length() > 32) {
            source.sendFailure(Component.literal("[WP] Название 2–32 символа."));
            return 0;
        }
        Optional<Subdivision> created = SubdivisionStore.get().create(profile.getFaction(), trimmed, player.getUUID());
        if (created.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Подразделение с таким названием уже есть."));
            return 0;
        }
        profile.setSubdivisionId(created.get().getId());
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), player.getGameProfile().getName() + " created subdivision " + trimmed);
        WarPlayerDataStore.get().save();
        SubdivisionStore.get().save();
        source.sendSystemMessage(Component.literal("[WP] Подразделение создано: " + trimmed).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int disbandSubdivision(CommandSourceStack source, String name) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!isAdmin(source) && !profile.canUseCommanderTools()) {
            source.sendFailure(Component.literal("[WP] Расформировывать может только командир стороны."));
            return 0;
        }
        String id = SubdivisionStore.makeId(profile.getFaction(), normalizeSpaces(name));
        Optional<Subdivision> subdivision = SubdivisionStore.get().get(id);
        if (subdivision.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Подразделение не найдено."));
            return 0;
        }
        if (!isAdmin(source) && subdivision.get().getFaction() != profile.getFaction()) {
            source.sendFailure(Component.literal("[WP] Это не твоё подразделение."));
            return 0;
        }
        for (UUID memberUuid : subdivision.get().getMembers()) {
            WarPlayerDataStore.get().findByUuid(memberUuid).ifPresent(p -> {
                if (id.equals(p.getSubdivisionId())) {
                    p.setSubdivisionId("");
                }
            });
        }
        SubdivisionStore.get().disband(id);
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " disbanded subdivision " + subdivision.get().getName());
        WarPlayerDataStore.get().save();
        SubdivisionStore.get().save();
        source.sendSystemMessage(Component.literal("[WP] Подразделение расформировано: " + subdivision.get().getName()).withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int inviteToSubdivision(CommandSourceStack source, String targetName, String name) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!isAdmin(source) && !profile.canUseCommanderTools()) {
            source.sendFailure(Component.literal("[WP] Управлять подразделениями может только командир стороны."));
            return 0;
        }
        Optional<WarPlayerProfile> targetOpt = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }
        WarPlayerProfile target = targetOpt.get();
        if (!isAdmin(source) && target.getFaction() != profile.getFaction()) {
            source.sendFailure(Component.literal("[WP] Игрок не из твоей стороны."));
            return 0;
        }
        String id = SubdivisionStore.makeId(target.getFaction(), normalizeSpaces(name));
        Optional<Subdivision> subdivision = SubdivisionStore.get().get(id);
        if (subdivision.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Подразделение не найдено."));
            return 0;
        }
        if (target.hasSubdivision()) {
            SubdivisionStore.get().removeMember(target.getSubdivisionId(), target.getUuid());
        }
        SubdivisionStore.get().addMember(id, target.getUuid());
        target.setSubdivisionId(id);
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " added " + target.getLastKnownName() + " to subdivision " + subdivision.get().getName());
        WarPlayerDataStore.get().save();
        SubdivisionStore.get().save();
        notifyOnline(source.getServer(), target, Component.literal("[WP] Тебя зачислили в подразделение: " + subdivision.get().getName()).withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(Component.literal("[WP] " + target.getLastKnownName() + " зачислен в " + subdivision.get().getName()).withStyle(ChatFormatting.GREEN));
        updatePassportIfOnline(source.getServer(), target);
        return 1;
    }

    private static int kickFromSubdivision(CommandSourceStack source, String targetName) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!isAdmin(source) && !profile.canUseCommanderTools()) {
            source.sendFailure(Component.literal("[WP] Управлять подразделениями может только командир стороны."));
            return 0;
        }
        Optional<WarPlayerProfile> targetOpt = WarPlayerDataStore.get().findByName(source.getServer(), targetName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.literal("[WP] Игрок не найден."));
            return 0;
        }
        WarPlayerProfile target = targetOpt.get();
        if (!target.hasSubdivision()) {
            source.sendFailure(Component.literal("[WP] Игрок не в подразделении."));
            return 0;
        }
        if (!isAdmin(source) && target.getFaction() != profile.getFaction()) {
            source.sendFailure(Component.literal("[WP] Игрок не из твоей стороны."));
            return 0;
        }
        String id = target.getSubdivisionId();
        SubdivisionStore.get().removeMember(id, target.getUuid());
        target.setSubdivisionId("");
        WarPlayerDataStore.get().appendAuditLog(source.getServer(), actorName(source) + " kicked " + target.getLastKnownName() + " from subdivision " + id);
        WarPlayerDataStore.get().save();
        SubdivisionStore.get().save();
        notifyOnline(source.getServer(), target, Component.literal("[WP] Тебя исключили из подразделения.").withStyle(ChatFormatting.RED));
        source.sendSystemMessage(Component.literal("[WP] " + target.getLastKnownName() + " исключён из подразделения.").withStyle(ChatFormatting.GREEN));
        updatePassportIfOnline(source.getServer(), target);
        return 1;
    }

    /**
     * Server console command: /wp env prepare
     * Runs WorldRelocationTask with the server's root directory.
     * Only accessible from the server console (not from players).
     */
    private static int prepareEnvironment(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        // The server root is the working directory of the server process.
        // getWorldPath(ROOT) gives us the world save directory; its parent is the server root.
        Path serverRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().getParent();
        if (serverRoot == null) {
            serverRoot = Path.of(".").toAbsolutePath();
        }

        source.sendSystemMessage(Component.literal("[WP] Starting environment preparation...").withStyle(ChatFormatting.YELLOW));
        source.sendSystemMessage(Component.literal("[WP] Server root: " + serverRoot).withStyle(ChatFormatting.GRAY));

        WorldRelocationTask task = new WorldRelocationTask();
        try {
            task.execute(serverRoot);
            source.sendSystemMessage(Component.literal("[WP] Environment preparation completed successfully.").withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (WorldRelocationException e) {
            source.sendFailure(Component.literal("[WP] Environment preparation failed: " + e.getMessage()));
            return 0;
        }
    }
}
