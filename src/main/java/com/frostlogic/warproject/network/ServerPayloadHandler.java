package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.RpName;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.PublicView;
import com.frostlogic.warproject.network.payload.c2s.*;
import com.frostlogic.warproject.network.payload.s2c.AuthErrorPayload;
import com.frostlogic.warproject.network.payload.s2c.RadialMenuPayload;
import com.frostlogic.warproject.server.command.AcceptCommandHandler;
import com.frostlogic.warproject.server.faction.FactionChoiceHandler;
import com.frostlogic.warproject.server.passport.PassportComponentTypes;
import com.frostlogic.warproject.server.radial.RadialMenuDispatcher;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import com.frostlogic.warproject.server.radial.RaytraceUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Server-side handlers for all C2S (client-to-server) payloads.
 * <p>
 * Each handler receives the payload and the context, enqueues work on the server thread,
 * and delegates to the appropriate service. Placeholder implementations log the receipt
 * and will be connected to real services in subsequent tasks.
 * <p>
 * Requirements: 4.1, 4.2
 * Design: §6
 */
public final class ServerPayloadHandler {

    private ServerPayloadHandler() {
        // static handlers only
    }

    public static void onRegister(RegisterRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WarProject.LOGGER.debug("[WP Net] Received RegisterRequestPayload from {}", player.getName().getString());

            com.frostlogic.warproject.server.auth.WGuardService wguard = ServiceRegistry.wguard();
            if (wguard == null) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.error.service_unavailable", List.of()));
                return;
            }

            // Validate registration input
            char[] pwd = payload.password().toCharArray();
            char[] confirm = payload.passwordConfirm().toCharArray();
            com.frostlogic.warproject.server.auth.AuthCfg cfg = com.frostlogic.warproject.server.auth.WGuardService.configFromWpConfig();
            com.frostlogic.warproject.server.auth.AuthResult validation = com.frostlogic.warproject.server.auth.WGuardService.validateRegistration(pwd, confirm, cfg);

            if (validation instanceof com.frostlogic.warproject.server.auth.AuthResult.Error err) {
                java.util.Arrays.fill(pwd, '\0');
                java.util.Arrays.fill(confirm, '\0');
                PacketDistributor.sendToPlayer(player,
                        new com.frostlogic.warproject.network.payload.s2c.AuthScreenStatePayload(
                                com.frostlogic.warproject.network.payload.AuthMode.REGISTER, err.messageKey()));
                return;
            }
            java.util.Arrays.fill(confirm, '\0');

            // Perform registration
            com.frostlogic.warproject.server.auth.AuthResult result = wguard.register(player, pwd);
            if (result instanceof com.frostlogic.warproject.server.auth.AuthResult.Error err) {
                PacketDistributor.sendToPlayer(player,
                        new com.frostlogic.warproject.network.payload.s2c.AuthScreenStatePayload(
                                com.frostlogic.warproject.network.payload.AuthMode.REGISTER, err.messageKey()));
                return;
            }

            // Registration successful → start captcha session
            com.frostlogic.warproject.server.auth.CaptchaService captcha = ServiceRegistry.captcha();
            if (captcha != null) {
                captcha.startSession(player);
            }
        });
    }

    public static void onLogin(LoginRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WarProject.LOGGER.debug("[WP Net] Received LoginRequestPayload from {}", player.getName().getString());

            com.frostlogic.warproject.server.auth.WGuardService wguard = ServiceRegistry.wguard();
            if (wguard == null) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.error.service_unavailable", List.of()));
                return;
            }

            char[] pwd = payload.password().toCharArray();
            com.frostlogic.warproject.server.auth.AuthResult result = wguard.login(player, pwd);

            if (result instanceof com.frostlogic.warproject.server.auth.AuthResult.Error err) {
                PacketDistributor.sendToPlayer(player,
                        new com.frostlogic.warproject.network.payload.s2c.AuthScreenStatePayload(
                                com.frostlogic.warproject.network.payload.AuthMode.LOGIN, err.messageKey()));
                return;
            }

            // Login successful — the WGuardService already set the correct PlayerState.
            // Re-bootstrap any state-specific session/UI that doesn't survive a
            // server restart (the in-memory CaptchaService.sessions map is wiped
            // on every restart, so a player with status=CAPTCHA in DB needs a
            // fresh code and sky-cage on relogin).
            PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
            if (state == PlayerState.CAPTCHA) {
                com.frostlogic.warproject.server.auth.CaptchaService captcha = ServiceRegistry.captcha();
                if (captcha != null) {
                    captcha.startSession(player);
                }
            } else if (state == PlayerState.RPNAME_REQUIRED) {
                // Remind the player how to proceed; the FreezeService keeps them
                // movement-locked until they finish the rpname step.
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[WP] Введи РП имя: /wp rpname Имя Фамилия")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        });
    }

    /**
     * Lazily-initialized dispatcher used by the radial open / radial action handlers.
     * <p>
     * Lives in a static holder so we don't instantiate it (and its dependencies)
     * during class loading — the {@link RadialMenuDispatcher#RadialMenuDispatcher()}
     * default constructor pulls the {@code RegionService} from
     * {@code RegionCacheHandler}, which is only meaningful on a running server.
     */
    private static final class DispatcherHolder {
        static final RadialMenuDispatcher INSTANCE = new RadialMenuDispatcher();
    }

    public static void onRadialOpenRequest(RadialOpenRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer initiator)) {
                return;
            }

            ServerPlayer target = RaytraceUtil.raytracePlayer(initiator);
            if (target == null) {
                PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.no_target", List.of()));
                WarProject.LOGGER.debug("[WP Radial] {} requested radial menu but raytrace found no target",
                        initiator.getGameProfile().getName());
                return;
            }

            EnumSet<RadialMenuItem> visible = DispatcherHolder.INSTANCE.visibleItems(initiator, target);
            PublicView view = buildPublicView(target);

            PacketDistributor.sendToPlayer(initiator,
                    new RadialMenuPayload(target.getUUID(), visible, view));

            WarProject.LOGGER.debug("[WP Radial] {} opened radial on {} → {} item(s)",
                    initiator.getGameProfile().getName(),
                    target.getGameProfile().getName(),
                    visible.size());
        });
    }

    public static void onRadialAction(RadialActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer initiator)) {
                return;
            }

            // Resolve the target player by UUID. Must still be online on this server.
            MinecraftServer server = initiator.getServer();
            ServerPlayer target = server == null ? null : server.getPlayerList().getPlayer(payload.targetUuid());
            if (target == null) {
                PacketDistributor.sendToPlayer(initiator,
                        new AuthErrorPayload("wp.error.target_offline", List.of()));
                WarProject.LOGGER.debug("[WP Radial] {} sent action {} for offline target {}",
                        initiator.getGameProfile().getName(), payload.action(), payload.targetUuid());
                return;
            }

            // Re-validate the action server-side. The client cannot be trusted —
            // raytrace, role, faction, state and region are all checked again here.
            RadialMenuItem item = payload.action();
            if (!DispatcherHolder.INSTANCE.allow(item, initiator, target)) {
                PacketDistributor.sendToPlayer(initiator,
                        new AuthErrorPayload("wp.error.action_denied", List.of()));
                WarProject.LOGGER.debug("[WP Radial] {} denied {} on {}",
                        initiator.getGameProfile().getName(), item, target.getGameProfile().getName());
                return;
            }

            // Dispatch to the concrete service. Some services are still TODO and
            // are wired up in their respective tasks.
            switch (item) {
                case ACCEPT -> {
                    AcceptCommandHandler handler = AcceptCommandHandler.fromServer();
                    if (handler == null) {
                        WarProject.LOGGER.error("[WP Radial] ACCEPT requested but Database is not initialised; ignoring (initiator={}, target={})",
                                initiator.getGameProfile().getName(), target.getGameProfile().getName());
                        PacketDistributor.sendToPlayer(initiator,
                                new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    AcceptCommandHandler.AcceptResult result = handler.acceptCandidate(
                            initiator, target, AcceptCommandHandler.AcceptSource.RADIAL);
                    if (result instanceof AcceptCommandHandler.AcceptResult.Failure failure) {
                        PacketDistributor.sendToPlayer(initiator,
                                new AuthErrorPayload(failure.errorKey(), List.of()));
                    } else if (result instanceof AcceptCommandHandler.AcceptResult.AlreadyAccepted) {
                        PacketDistributor.sendToPlayer(initiator,
                                new AuthErrorPayload("wp.error.already_accepted", List.of()));
                    }
                }
                case CAPTURE_PASSPORT -> {
                    com.frostlogic.warproject.server.captivity.CaptivityService captivityService = ServiceRegistry.captivity();
                    if (captivityService == null) {
                        WarProject.LOGGER.error("[WP Radial] CAPTURE_PASSPORT: CaptivityService not available");
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    var captureResult = captivityService.capture(initiator, target);
                    if (captureResult instanceof com.frostlogic.warproject.server.captivity.CaptivityService.CaptureResult.Failure failure) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload(failure.errorKey(), List.of()));
                    } else if (captureResult instanceof com.frostlogic.warproject.server.captivity.CaptivityService.CaptureResult.Success) {
                        // Trigger automatic award for capture success (Req. 15.1, 15.5)
                        com.frostlogic.warproject.server.award.AwardsService awardsService = ServiceRegistry.awards();
                        if (awardsService != null) {
                            awardsService.onCaptureSuccess(initiator);
                        }
                    }
                }
                case PROMOTE -> {
                    com.frostlogic.warproject.server.rank.RankService rankService = com.frostlogic.warproject.server.rank.RankService.current();
                    if (rankService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    var promoteResult = rankService.promote(initiator, target);
                    if (promoteResult instanceof com.frostlogic.warproject.server.rank.RankService.RankResult.Failure failure) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload(failure.errorKey(), List.of()));
                    }
                }
                case DEMOTE -> {
                    com.frostlogic.warproject.server.rank.RankService rankService = com.frostlogic.warproject.server.rank.RankService.current();
                    if (rankService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    var demoteResult = rankService.demote(initiator, target);
                    if (demoteResult instanceof com.frostlogic.warproject.server.rank.RankService.RankResult.Failure failure) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload(failure.errorKey(), List.of()));
                    }
                }
                case COLLAB_MARK -> {
                    com.frostlogic.warproject.server.collab.CollaboratorService collabService = com.frostlogic.warproject.server.collab.CollaboratorService.current();
                    if (collabService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    collabService.set(target, initiator.getStringUUID(),
                            initiator.getGameProfile().getName(), "manual via radial");
                }
                case COLLAB_UNMARK -> {
                    com.frostlogic.warproject.server.collab.CollaboratorService collabService = com.frostlogic.warproject.server.collab.CollaboratorService.current();
                    if (collabService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    collabService.unset(target, initiator.getStringUUID(),
                            initiator.getGameProfile().getName(), "manual via radial");
                }
                case RANSOM_OPEN -> {
                    // Open the ransom trade menu between initiator and target
                    com.frostlogic.warproject.server.captivity.CaptivityService captivityService = ServiceRegistry.captivity();
                    if (captivityService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    // Find the target's passport in initiator's inventory (they hold the trophy)
                    // and initiate ransom. For now, auto-ransom the first matching passport.
                    boolean ransomed = false;
                    for (int slot = 0; slot < initiator.getInventory().getContainerSize(); slot++) {
                        net.minecraft.world.item.ItemStack stack = initiator.getInventory().getItem(slot);
                        if (!stack.isEmpty() && stack.has(PassportComponentTypes.PASSPORT_DATA.get())) {
                            com.frostlogic.warproject.server.passport.PassportData pData =
                                    stack.get(PassportComponentTypes.PASSPORT_DATA.get());
                            if (pData != null && pData.trophy()
                                    && target.getStringUUID().equals(findPassportOwner(pData))) {
                                var ransomResult = captivityService.ransom(pData.passportId(), initiator, target);
                                if (ransomResult instanceof com.frostlogic.warproject.server.captivity.CaptivityService.RansomResult.Success) {
                                    ransomed = true;
                                } else if (ransomResult instanceof com.frostlogic.warproject.server.captivity.CaptivityService.RansomResult.Failure f) {
                                    PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload(f.errorKey(), List.of()));
                                }
                                break;
                            }
                        }
                    }
                    if (!ransomed) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.captivity.error.passport_not_found", List.of()));
                    }
                }
                case SUBDIV_INVITE -> {
                    com.frostlogic.warproject.server.subdivision.SubdivisionService subdivService = ServiceRegistry.subdivision();
                    if (subdivService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    // Find initiator's subdivision from DB
                    com.frostlogic.warproject.persistence.Database db = com.frostlogic.warproject.server.ServerEvents.getDatabase();
                    if (db == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    Integer subId = db.inTx(conn -> {
                        var row = new com.frostlogic.warproject.persistence.dao.PlayersDao()
                                .findByUuid(conn, initiator.getStringUUID());
                        return row.map(com.frostlogic.warproject.persistence.dao.PlayersDao.Player::subdivisionId).orElse(null);
                    });
                    if (subId == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.command.subdivision.invite.no_subdivision", List.of()));
                        break;
                    }
                    var inviteResult = subdivService.invite(subId,
                            target.getStringUUID(), target.getGameProfile().getName(),
                            initiator.getStringUUID(), initiator.getGameProfile().getName());
                    if (inviteResult instanceof com.frostlogic.warproject.server.subdivision.SubdivisionService.Result.Failure<?> failure) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload(failure.errorKey(), List.of()));
                    } else if (inviteResult instanceof com.frostlogic.warproject.server.subdivision.SubdivisionService.Result.Success<?> success) {
                        // Update Military ID card with new subdivision name (Req. 5.2)
                        com.frostlogic.warproject.server.militaryid.MilitaryIdService midService = ServiceRegistry.militaryId();
                        if (midService != null) {
                            try {
                                var subdivision = (com.frostlogic.warproject.persistence.dao.SubdivisionsDao.Subdivision) success.value();
                                midService.updateSubdivision(target, subdivision.name());
                            } catch (Exception e) {
                                WarProject.LOGGER.error("[WP Radial] Failed to update Military ID subdivision for {}: {}",
                                        target.getGameProfile().getName(), e.getMessage(), e);
                            }
                        }
                    }
                }
                case SUBDIV_KICK -> {
                    com.frostlogic.warproject.server.subdivision.SubdivisionService subdivService = ServiceRegistry.subdivision();
                    if (subdivService == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    // Find target's subdivision from DB
                    com.frostlogic.warproject.persistence.Database db = com.frostlogic.warproject.server.ServerEvents.getDatabase();
                    if (db == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.error.internal", List.of()));
                        break;
                    }
                    Integer targetSubId = db.inTx(conn -> {
                        var row = new com.frostlogic.warproject.persistence.dao.PlayersDao()
                                .findByUuid(conn, target.getStringUUID());
                        return row.map(com.frostlogic.warproject.persistence.dao.PlayersDao.Player::subdivisionId).orElse(null);
                    });
                    if (targetSubId == null) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload("wp.command.subdivision.kick.not_member", List.of()));
                        break;
                    }
                    var kickResult = subdivService.kick(targetSubId,
                            target.getStringUUID(), target.getGameProfile().getName(),
                            initiator.getStringUUID(), initiator.getGameProfile().getName(),
                            "kicked via radial menu");
                    if (kickResult instanceof com.frostlogic.warproject.server.subdivision.SubdivisionService.Result.Failure<?> failure) {
                        PacketDistributor.sendToPlayer(initiator, new AuthErrorPayload(failure.errorKey(), List.of()));
                    } else if (kickResult.isSuccess()) {
                        // Clear Military ID card subdivision field (Req. 5.3)
                        com.frostlogic.warproject.server.militaryid.MilitaryIdService midService = ServiceRegistry.militaryId();
                        if (midService != null) {
                            try {
                                midService.updateSubdivision(target, "");
                            } catch (Exception e) {
                                WarProject.LOGGER.error("[WP Radial] Failed to clear Military ID subdivision for {}: {}",
                                        target.getGameProfile().getName(), e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Builds the safe public projection of {@code target} sent to the initiator
     * inside the {@link RadialMenuPayload}. Mirrors the projection used by
     * {@code PlayerLifecycleService.sendPublicView}.
     */
    private static PublicView buildPublicView(ServerPlayer target) {
        Optional<FactionId> factionOpt = target.getData(WpAttachmentTypes.FACTION.get());
        // Faction may not be set yet (e.g. FACTIONLESS targets). The PublicView record
        // expects a non-null FactionId; default to ZARNAVIA so the wire codec stays
        // symmetric — the client distinguishes "no faction" via the target's status.
        FactionId faction = factionOpt.orElse(FactionId.ZARNAVIA);
        Role role = target.getData(WpAttachmentTypes.ROLE.get());
        PlayerState status = target.getData(WpAttachmentTypes.PLAYER_STATE.get());
        boolean collaborator = target.getData(WpAttachmentTypes.COLLABORATOR.get());
        Optional<RpName> rpNameOpt = target.getData(WpAttachmentTypes.RP_NAME.get());
        String rpFullName = rpNameOpt.map(RpName::fullName).orElse(null);

        return new PublicView(
                target.getUUID(),
                faction,
                role,
                status,
                collaborator,
                rpFullName
        );
    }

    public static void onPassportOpen(PassportOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WarProject.LOGGER.debug("[WP Net] Received PassportOpenPayload from {}: slot={}", player.getName().getString(), payload.slotIndex());

            // Read passport data from the specified inventory slot and send snapshot to client
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(payload.slotIndex());
            if (stack.isEmpty() || !stack.has(PassportComponentTypes.PASSPORT_DATA.get())) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.passport.error.not_found", List.of()));
                return;
            }
            com.frostlogic.warproject.server.passport.PassportData data =
                    stack.get(PassportComponentTypes.PASSPORT_DATA.get());
            if (data == null) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.passport.error.not_found", List.of()));
                return;
            }
            PacketDistributor.sendToPlayer(player,
                    new com.frostlogic.warproject.network.payload.s2c.PassportSnapshotPayload(data));
        });
    }

    public static void onRpNameSubmit(RpNameSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WarProject.LOGGER.debug("[WP Net] Received RpNameSubmitPayload from {}: {} {}", player.getName().getString(), payload.rpName(), payload.rpSurname());

            com.frostlogic.warproject.server.lifecycle.PlayerLifecycleService lifecycle = ServiceRegistry.lifecycle();
            if (lifecycle == null) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.error.service_unavailable", List.of()));
                return;
            }

            // Validate state
            PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
            if (state != PlayerState.RPNAME_REQUIRED) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.rpname.not_in_rpname_state", List.of()));
                return;
            }

            // Basic validation
            String name = payload.rpName().trim();
            String surname = payload.rpSurname().trim();
            if (name.isEmpty() || surname.isEmpty() || name.length() > 32 || surname.length() > 32) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.rpname.invalid", List.of()));
                return;
            }

            try {
                lifecycle.setRpName(player, name, surname);
                // Teleport with the same fallback chain as RpNameCommand:
                // 1) admin-set spawn point, 2) config XYZ if non-default,
                // 3) world spawn so the player never gets stuck in the void.
                boolean teleported = com.frostlogic.warproject.server.WarServerSettings.get()
                        .getSpawnPoint()
                        .map(point -> point.teleport(player))
                        .orElse(false);
                if (!teleported) {
                    List<? extends Integer> coords = com.frostlogic.warproject.WpConfig.FACTIONS_CHOICE_HALL_SPAWN.get();
                    boolean isDefault = coords.size() >= 3
                            && coords.get(0) == 0 && coords.get(1) == 64 && coords.get(2) == 0;
                    if (coords.size() >= 3 && !isDefault) {
                        double x = coords.get(0) + 0.5;
                        double y = coords.get(1);
                        double z = coords.get(2) + 0.5;
                        player.teleportTo(player.serverLevel(), x, y, z, player.getYRot(), player.getXRot());
                    } else {
                        com.frostlogic.warproject.server.SpawnTeleporter.toWorldSpawn(player);
                        WarProject.LOGGER.warn("[WP RpName] No spawnPoint configured and FACTIONS_CHOICE_HALL_SPAWN is default. Falling back to world spawn for {}.",
                                player.getGameProfile().getName());
                    }
                }
            } catch (IllegalStateException e) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.rpname.not_in_rpname_state", List.of()));
            }
        });
    }

    public static void onCaptchaSubmit(CaptchaSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WarProject.LOGGER.debug("[WP Net] Received CaptchaSubmitPayload from {}", player.getName().getString());

            com.frostlogic.warproject.server.auth.CaptchaService captcha = ServiceRegistry.captcha();
            if (captcha == null) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.error.service_unavailable", List.of()));
                return;
            }

            com.frostlogic.warproject.server.auth.CaptchaService.SubmitResult result =
                    captcha.submit(player, payload.code());

            if (result instanceof com.frostlogic.warproject.server.auth.CaptchaService.SubmitResult.WrongRetry retry) {
                PacketDistributor.sendToPlayer(player, new AuthErrorPayload("wp.captcha.wrong_code", List.of()));
            }
            // Success and FailedKick are handled internally by CaptchaService
        });
    }

    public static void onFactionChoice(FactionChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received FactionChoicePayload from {}: faction={}", context.player().getName().getString(), payload.factionId());
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FactionChoiceHandler.handleChoice(serverPlayer, payload.factionId());
            }
        });
    }

    public static void onSetFactionMarker(com.frostlogic.warproject.network.payload.c2s.SetFactionMarkerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            com.frostlogic.warproject.server.map.FactionMarkerService svc =
                    com.frostlogic.warproject.server.map.FactionMarkerService.get();
            if (svc != null) {
                svc.handleSet(player, payload);
            }
        });
    }

    /**
     * Resolves the owner UUID of a passport by looking it up in the database.
     * Returns the owner UUID string, or null if not found.
     */
    private static String findPassportOwner(com.frostlogic.warproject.server.passport.PassportData data) {
        com.frostlogic.warproject.persistence.Database db = com.frostlogic.warproject.server.ServerEvents.getDatabase();
        if (db == null) return null;
        return db.inTx(conn -> {
            var passport = new com.frostlogic.warproject.persistence.dao.PassportsDao()
                    .findById(conn, data.passportId());
            return passport.map(com.frostlogic.warproject.persistence.dao.PassportsDao.Passport::ownerUuid).orElse(null);
        });
    }
}
