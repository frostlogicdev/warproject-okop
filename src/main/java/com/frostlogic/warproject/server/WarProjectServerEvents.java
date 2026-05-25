package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.command.WarProjectCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class WarProjectServerEvents {
    private WarProjectServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WarProjectCommands.register(event);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        WarPlayerDataStore.get().load(event.getServer());
        WarServerSettings.get().load(event.getServer());
        SubdivisionStore.get().load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try { WarPlayerDataStore.get().save(); }
        catch (Throwable t) { WarProject.LOGGER.warn("[WarProject] WarPlayerDataStore.save failed on stop: {}", t.getMessage()); }
        try { WarServerSettings.get().save(); }
        catch (Throwable t) { WarProject.LOGGER.warn("[WarProject] WarServerSettings.save failed on stop: {}", t.getMessage()); }
        try { SubdivisionStore.get().save(); }
        catch (Throwable t) { WarProject.LOGGER.warn("[WarProject] SubdivisionStore.save failed on stop: {}", t.getMessage()); }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Single-player / integrated server: no auth pipeline at all.
        // ServerEvents.onPlayerLoginAuthFlow (HIGH priority) already forced
        // PLAYER_STATE=ACCEPTED in this case; mirror the early-out so the
        // legacy WarLoginHandler.promptLogin is never invoked and the player
        // does not get hit with the "введи пароль" screen in a SP world.
        if (player.getServer() == null || !player.getServer().isDedicatedServer()) {
            return;
        }

        // ─── Pipeline guard ───────────────────────────────────────────────
        // If the new DB-backed auth pipeline is active for this player, skip
        // the entire legacy login flow. ServerEvents.onPlayerLoginAuthFlow
        // (priority HIGH) already sent the auth screen and set PlayerState.
        // Running both would double-prompt and fight over state.
        com.frostlogic.warproject.persistence.Database db = ServerEvents.getDatabase();
        if (db != null) {
            try {
                boolean newPipeline = db.inTx(conn ->
                        new com.frostlogic.warproject.persistence.dao.AccountsDao()
                                .findByUuid(conn, player.getStringUUID()).isPresent());
                if (newPipeline) {
                    // Still send the welcome message (cosmetic) and refresh prefix.
                    player.sendSystemMessage(Component.translatable("wp.welcome").withStyle(ChatFormatting.GOLD));
                    WarPrefixManager.refresh(player);
                    return;
                }
            } catch (Throwable t) {
                // Pipeline guard failure — log and fall through to legacy flow
                // rather than silently swallowing the error.
                WarProject.LOGGER.warn("[WarProject] Pipeline guard DB check failed for {}: {}",
                        player.getGameProfile().getName(), t.getMessage());
            }
        }
        // ─── End pipeline guard ───────────────────────────────────────────

        // Captcha-failure reconnect cooldown: a player who just failed captcha
        // gets a 90-second hold before they can rejoin.
        if (!player.hasPermissions(2) && CaptchaManager.isOnCooldown(player.getUUID())) {
            long remainingMs = CaptchaManager.getCooldownRemainingMs(player.getUUID());
            long remainingSec = Math.max(1L, (remainingMs + 999L) / 1000L);
            player.connection.disconnect(Component.translatable("wp.captcha.cooldown", remainingSec));
            return;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        profile.setLoggedIn(false); // fresh session — must authenticate again
        player.sendSystemMessage(Component.translatable("wp.welcome").withStyle(ChatFormatting.GOLD));

        // ALWAYS pin a fresh connect to the intro point so chat / movement is contained
        // until the player has cleared registration + captcha + rp-name. The exact next
        // step (login screen vs captcha vs rp-name vs spawn) is decided once auth completes
        // inside WarLoginHandler.continueOnboarding(); from here we only open the gate.
        // Skip the re-teleport when the player has already chosen a candidate side —
        // they're at a base and we don't want to drag them away on every relog.
        if (!profile.isOnboarded()
                || (!profile.isFactionMember() && !profile.getCandidateFaction().isPlayable())) {
            WarServerSettings.get().getIntroPoint().ifPresent(point -> point.teleport(player));
        }

        // Open the registration / login screen on the client. The follow-up onboarding
        // (captcha → rpname → faction) is triggered after a successful submit.
        WarLoginHandler.promptLogin(player);

        WarPrefixManager.refresh(player);
        WarPlayerDataStore.get().save();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                CaptchaManager.clear(player.getUUID());
            } catch (Throwable t) {
                WarProject.LOGGER.warn("[WarProject] CaptchaManager.clear failed on logout: {}", t.getMessage());
            }
            try {
                WarLoginHandler.clear(player.getUUID());
            } catch (Throwable t) {
                WarProject.LOGGER.warn("[WarProject] WarLoginHandler.clear failed on logout: {}", t.getMessage());
            }
            try {
                WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
                profile.setLoggedIn(false);
            } catch (Throwable t) {
                WarProject.LOGGER.warn("[WarProject] Profile logout update failed: {}", t.getMessage());
            }
        }
        try {
            WarPlayerDataStore.get().save();
        } catch (Throwable t) {
            WarProject.LOGGER.warn("[WarProject] WarPlayerDataStore.save failed on logout: {}", t.getMessage());
        }
    }
}
