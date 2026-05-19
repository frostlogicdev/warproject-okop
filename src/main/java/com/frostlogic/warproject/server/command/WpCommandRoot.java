package com.frostlogic.warproject.server.command;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.BansDao;
import com.frostlogic.warproject.persistence.dao.CooldownsDao;
import com.frostlogic.warproject.persistence.dao.EventParticipantsDao;
import com.frostlogic.warproject.persistence.dao.EventsDao;
import com.frostlogic.warproject.persistence.dao.MutesDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao;
import com.frostlogic.warproject.persistence.dao.TrucesDao;
import com.frostlogic.warproject.persistence.dao.WarnsDao;
import com.frostlogic.warproject.server.ServerEvents;
import com.frostlogic.warproject.server.auth.CaptchaService;
import com.frostlogic.warproject.server.captivity.CaptivityService;
import com.frostlogic.warproject.server.chat.GeneralChatService;
import com.frostlogic.warproject.server.collab.CollaboratorService;
import com.frostlogic.warproject.server.command.nodes.AwardCommands;
import com.frostlogic.warproject.server.command.nodes.BioCommands;
import com.frostlogic.warproject.server.command.nodes.CaptchaCommand;
import com.frostlogic.warproject.server.command.nodes.DiaryCommands;
import com.frostlogic.warproject.server.command.nodes.DiplomacyCommands;
import com.frostlogic.warproject.server.command.nodes.EventCommands;
import com.frostlogic.warproject.server.command.nodes.FactionCommands;
import com.frostlogic.warproject.server.command.nodes.ModerationCommands;
import com.frostlogic.warproject.server.command.nodes.RpNameCommand;
import com.frostlogic.warproject.server.command.nodes.SubdivisionCommands;
import com.frostlogic.warproject.server.diplomacy.DiplomacyService;
import com.frostlogic.warproject.server.diplomacy.DiplomacyPvPHandler;
import com.frostlogic.warproject.server.diplomacy.DiplomacyTickHandler;
import com.frostlogic.warproject.server.event.EventService;
import com.frostlogic.warproject.server.lifecycle.PlayerLifecycleService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Root registration of the {@code /wp ...} Brigadier command tree.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>On {@link ServerStartedEvent} — wire up the per-subcommand service
 *       dependencies (e.g. {@link CaptchaService}, {@link PlayerLifecycleService})
 *       so that subnodes registered later in {@link RegisterCommandsEvent} have
 *       a fully initialised handler.</li>
 *   <li>On {@link RegisterCommandsEvent} — create a single {@code literal("wp")}
 *       node and attach every implemented subcommand to it.</li>
 * </ol>
 * <p>
 * The legacy {@link WarProjectCommands} registrar (older onboarding /
 * moderation flow) continues to register its own {@code /wp} root in parallel
 * during the migration period — Brigadier merges both literal trees so each
 * subcommand path resolves to the matching node. As tasks 15.2–15.6 land,
 * the legacy registrar will be replaced by the corresponding modules under
 * this root.
 * <p>
 * Requirements: 10.1
 * Design: §7
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class WpCommandRoot {

    /** Top-level literal name for every WarProject command. */
    public static final String ROOT_LITERAL = "wp";

    private WpCommandRoot() {
        // event subscriber — no instantiation
    }

    /**
     * Wires up per-command service dependencies once the server (and the
     * {@link Database}) is fully ready. Without this step the static {@code init}
     * setters on the subcommand nodes would be null when commands execute.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Database database = ServerEvents.getDatabase();
        if (database == null) {
            WarProject.LOGGER.warn("[WP Cmd] Database is null at ServerStartedEvent — /wp subcommands will be unavailable.");
            return;
        }

        CooldownsDao cooldownsDao = new CooldownsDao();
        PlayersDao playersDao = new PlayersDao();
        AuditLogDao auditLogDao = new AuditLogDao();
        BansDao bansDao = new BansDao();
        MutesDao mutesDao = new MutesDao();
        WarnsDao warnsDao = new WarnsDao();
        PassportsDao passportsDao = new PassportsDao();
        SubdivisionsDao subdivisionsDao = new SubdivisionsDao();

        CaptchaService captchaService = new CaptchaService(database, cooldownsDao);
        PlayerLifecycleService lifecycleService = new PlayerLifecycleService(database, playersDao);
        GeneralChatService generalChatService = new GeneralChatService(database, cooldownsDao, auditLogDao);
        CollaboratorService collaboratorService = new CollaboratorService(database, playersDao, auditLogDao);
        CollaboratorService.install(collaboratorService);

        // RankService + RankRegistry
        com.frostlogic.warproject.persistence.dao.RanksDao ranksDao = new com.frostlogic.warproject.persistence.dao.RanksDao();
        com.frostlogic.warproject.server.role.RankRegistry rankRegistry = new com.frostlogic.warproject.server.role.RankRegistry(database, ranksDao);
        rankRegistry.reload();
        com.frostlogic.warproject.server.rank.RankService rankService = new com.frostlogic.warproject.server.rank.RankService(database, playersDao, auditLogDao, rankRegistry);
        com.frostlogic.warproject.server.rank.RankService.install(rankService);

        CaptchaCommand.init(captchaService);
        RpNameCommand.init(lifecycleService, database, auditLogDao);
        ModerationCommands.init(database, bansDao, mutesDao, warnsDao, auditLogDao);
        FactionCommands.init(database, playersDao, passportsDao, auditLogDao, generalChatService, collaboratorService);
        SubdivisionCommands.init(database, playersDao, subdivisionsDao, auditLogDao);

        // EventService + EventCommands
        EventsDao eventsDao = new EventsDao();
        EventParticipantsDao eventParticipantsDao = new EventParticipantsDao();
        EventService eventService = new EventService(database, eventsDao, eventParticipantsDao, auditLogDao);
        EventCommands.init(eventService);

        // DiplomacyService + DiplomacyCommands
        TrucesDao trucesDao = new TrucesDao();
        CaptivityService captivityService = new CaptivityService(database, passportsDao, playersDao, auditLogDao);
        DiplomacyService diplomacyService = new DiplomacyService(database, trucesDao, captivityService, passportsDao, auditLogDao);
        DiplomacyCommands.init(diplomacyService);
        DiplomacyPvPHandler.init(diplomacyService);
        DiplomacyTickHandler.init(diplomacyService);

        // AwardsService + AwardCommands
        com.frostlogic.warproject.persistence.dao.AwardDefinitionsDao awardDefinitionsDao = new com.frostlogic.warproject.persistence.dao.AwardDefinitionsDao();
        com.frostlogic.warproject.persistence.dao.PlayerAwardsDao playerAwardsDao = new com.frostlogic.warproject.persistence.dao.PlayerAwardsDao();
        com.frostlogic.warproject.server.award.AwardsService awardsService = new com.frostlogic.warproject.server.award.AwardsService(
                database, awardDefinitionsDao, playerAwardsDao, auditLogDao, playersDao);
        AwardCommands.init(awardsService);

        // MilitaryIdService — wire into AwardsService for card synchronization (Req. 15.1, 15.5)
        com.frostlogic.warproject.server.militaryid.MilitaryIdService militaryIdService =
                new com.frostlogic.warproject.server.militaryid.MilitaryIdService(database, passportsDao);
        awardsService.setMilitaryIdService(militaryIdService);

        WarProject.LOGGER.debug("[WP Cmd] WpCommandRoot wired: CaptchaService + PlayerLifecycleService + ModerationCommands + FactionCommands + SubdivisionCommands + GeneralChatService + CollaboratorService + EventCommands + DiplomacyCommands + AwardCommands ready.");
    }

    /**
     * Builds and registers the {@code /wp} command tree on the Brigadier dispatcher.
     * <p>
     * Subcommands:
     * <ul>
     *   <li>{@code /wp captcha <code>} — see {@link CaptchaCommand}.</li>
     *   <li>{@code /wp rpname ...} — see {@link RpNameCommand}.</li>
     *   <li>{@code /wp ban|kick|mute|warn|...} — see {@link ModerationCommands}.</li>
     *   <li>{@code /wp accept|up|set|collab|...} — see {@link FactionCommands}.</li>
     *   <li>{@code /wp create subdivision|...} — see {@link SubdivisionCommands}.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> wpRoot = Commands.literal(ROOT_LITERAL);

        // Attach implemented subcommands. Each register(...) appends a child
        // node onto the supplied builder — see CaptchaCommand / RpNameCommand /
        // ModerationCommands / FactionCommands for the actual node wiring.
        CaptchaCommand.register(wpRoot);
        RpNameCommand.register(wpRoot);
        ModerationCommands.register(wpRoot);
        FactionCommands.register(wpRoot);
        SubdivisionCommands.register(wpRoot);
        DiplomacyCommands.register(wpRoot);
        EventCommands.register(wpRoot);
        AwardCommands.register(wpRoot);
        BioCommands.register(wpRoot);
        DiaryCommands.register(wpRoot);

        // /wp mapfill <radius> — admin command to preload map tiles
        wpRoot.then(Commands.literal("mapfill")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.frostlogic.warproject.server.map.MapPreloader.stop(player);
                            return 1;
                        })
                )
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(100, 10000))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius");
                            com.frostlogic.warproject.server.map.MapPreloader.start(player, radius);
                            return 1;
                        })
                )
        );

        dispatcher.register(wpRoot);

        WarProject.LOGGER.debug("[WP Cmd] /{} registered with subcommands: captcha, rpname, ban, kick, mute, unban, unmute, warn, tp, tphere, freeze, vanish, history, reload, accept, up, set comand, collab, uncollab, generalchat, create subdivision, delete subdivision, subdivision invite/kick/list/info.", ROOT_LITERAL);
    }
}
