package com.frostlogic.warproject.server;

import com.frostlogic.warproject.network.LoginResultPayload;
import com.frostlogic.warproject.network.OpenLoginScreenPayload;
import com.frostlogic.warproject.network.SubmitPasswordPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authentication handler. Owns the failed-attempts counter (so a
 * brute force burst can disconnect the client) and runs the post-authentication
 * onboarding step (issuing the captcha or pushing the player towards rp-name /
 * faction selection).
 */
public final class WarLoginHandler {
    /** Minimum length we'll accept for a brand-new password. */
    public static final int MIN_PASSWORD_LENGTH = 6;
    /** Wrong-password attempts before we kick the connection. */
    public static final int MAX_LOGIN_ATTEMPTS = 5;

    private static final Map<UUID, Integer> ATTEMPTS = new ConcurrentHashMap<>();

    private WarLoginHandler() {
    }

    /** Sends the login (or registration) screen open packet to the given player. */
    public static void promptLogin(ServerPlayer player) {
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        boolean register = !profile.isRegistered();
        PacketDistributor.sendToPlayer(player, new OpenLoginScreenPayload(register));
    }

    /**
     * Called from {@code WarNetworkHandler} when a client submits the form.
     * Must run on the server thread (caller wraps with {@code enqueueWork}).
     */
    public static void handleSubmit(ServerPlayer player, SubmitPasswordPayload payload) {
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);

        if (profile.isLoggedIn()) {
            // Already authenticated this session; ignore stray submissions.
            sendResult(player, true, "Уже выполнен вход.");
            return;
        }

        String password = payload.password() == null ? "" : payload.password();
        if (payload.register()) {
            handleRegister(player, profile, password);
        } else {
            handleLogin(player, profile, password);
        }
    }

    private static void handleRegister(ServerPlayer player, WarPlayerProfile profile, String password) {
        if (profile.isRegistered()) {
            // Stale client — server treats this as a normal login attempt instead.
            handleLogin(player, profile, password);
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            sendResult(player, false, "Пароль слишком короткий (минимум " + MIN_PASSWORD_LENGTH + " символов).");
            return;
        }
        if (password.length() > SubmitPasswordPayload.MAX_PASSWORD_LENGTH) {
            sendResult(player, false, "Пароль слишком длинный.");
            return;
        }

        profile.setPassword(password);
        profile.setLoggedIn(true);
        ATTEMPTS.remove(player.getUUID());
        WarPlayerDataStore.get().appendAuditLog(player.getServer(), player.getGameProfile().getName() + " registered password");
        WarPlayerDataStore.get().save();
        sendResult(player, true, "Регистрация успешна.");
        player.sendSystemMessage(Component.translatable("wp.register.success").withStyle(ChatFormatting.GREEN));
        continueOnboarding(player, profile);
    }

    private static void handleLogin(ServerPlayer player, WarPlayerProfile profile, String password) {
        if (!profile.isRegistered()) {
            // Edge case: client claims to log in but server has no password on file.
            // Re-prompt registration instead of leaking that fact.
            sendResult(player, false, "Профиль не зарегистрирован, регистрируем новый.");
            PacketDistributor.sendToPlayer(player, new OpenLoginScreenPayload(true));
            return;
        }

        if (!profile.verifyPassword(password)) {
            int attempts = ATTEMPTS.merge(player.getUUID(), 1, Integer::sum);
            int remaining = Math.max(0, MAX_LOGIN_ATTEMPTS - attempts);
            if (remaining <= 0) {
                ATTEMPTS.remove(player.getUUID());
                player.connection.disconnect(Component.literal("WarProject: слишком много попыток входа"));
                return;
            }
            sendResult(player, false, "Неверный пароль. Осталось попыток: " + remaining + ".");
            return;
        }

        profile.setLoggedIn(true);
        ATTEMPTS.remove(player.getUUID());
        sendResult(player, true, "Вход выполнен.");
        player.sendSystemMessage(Component.translatable("wp.login.success").withStyle(ChatFormatting.GREEN));
        continueOnboarding(player, profile);
    }

    /** Called once a player has successfully authenticated. Decides the next onboarding step. */
    public static void continueOnboarding(ServerPlayer player, WarPlayerProfile profile) {
        // Re-sync attachments from the legacy profile so any new-system command
        // (RoleResolver, faction checks, etc.) sees the right state immediately.
        LegacyAttachmentBridge.sync(player);
        if (!profile.isCaptchaPassed()) {
            // CaptchaManager.issue handles the sky-cage teleport + flight + invuln
            // itself; we don't dump the player at the intro point first because
            // we'd then yank them up into the sky one tick later.
            CaptchaManager.issue(player);
        } else if (!profile.hasRpName()) {
            WarServerSettings.get().getIntroPoint().ifPresent(point -> point.teleport(player));
            player.sendSystemMessage(Component.literal("[WP] Введи РП имя: /wp rpname Имя Фамилия").withStyle(ChatFormatting.YELLOW));
        } else if (!profile.isFactionMember()) {
            if (profile.getCandidateFaction().isPlayable()) {
                // Player previously chose a candidate side via NPC — keep them
                // at the base they were teleported to instead of yanking them
                // back to spawn on every relog. Re-issue / refresh the passport.
                PassportManager.issue(player, profile);
            } else {
                SpawnTeleporter.toSpawn(player);
                player.sendSystemMessage(Component.literal("[WP] Нажми на NPC выбора стороны на спавне.").withStyle(ChatFormatting.YELLOW));
            }
        } else {
            player.sendSystemMessage(Component.literal("[WP] Твоя сторона: " + profile.getFaction().displayName() + ", звание: " + profile.getRank().displayName()).withStyle(ChatFormatting.GREEN));
            PassportManager.issue(player, profile);
        }
        WarPrefixManager.refresh(player);
    }

    public static void clear(UUID uuid) {
        ATTEMPTS.remove(uuid);
    }

    private static void sendResult(ServerPlayer player, boolean success, String message) {
        PacketDistributor.sendToPlayer(player, new LoginResultPayload(success, message));
    }
}
