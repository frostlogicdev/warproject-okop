package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class OnboardingGuard {
    private OnboardingGuard() {
    }

    /**
     * "Hard" restriction: player cannot do anything until they finish basic
     * onboarding (login + captcha + rp-name). Used for chat and incoming damage
     * blocking — we don't want freshly-spawned not-yet-authenticated players to
     * be able to type or take damage.
     */
    private static boolean isRestricted(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return false;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isLoggedIn()) {
            return true;
        }
        return !profile.isOnboarded();
    }

    /**
     * "World action" restriction: player cannot break/place blocks or attack
     * mobs unless they are a fully accepted faction member. Candidates and
     * factionless players are denied. Bypass for OPs.
     */
    private static boolean cannotActInWorld(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return false;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isLoggedIn()) {
            return true;
        }
        if (!profile.isOnboarded()) {
            return true;
        }
        // Candidates and factionless players are NOT allowed to modify the
        // world or attack — only fully accepted faction members can.
        return !profile.isFactionMember();
    }

    private static void notifyRestricted(ServerPlayer player) {
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isRegistered()) {
            player.sendSystemMessage(Component.literal("[WP] Заверши регистрацию в окне входа.").withStyle(ChatFormatting.RED));
        } else if (!profile.isLoggedIn()) {
            player.sendSystemMessage(Component.literal("[WP] Войди через окно входа.").withStyle(ChatFormatting.RED));
        } else if (!profile.isCaptchaPassed()) {
            player.sendSystemMessage(Component.literal("[WP] Сначала пройди капчу: /wp captcha <код>").withStyle(ChatFormatting.RED));
        } else if (!profile.hasRpName()) {
            player.sendSystemMessage(Component.literal("[WP] Введи РП имя: /wp rpname Имя Фамилия").withStyle(ChatFormatting.RED));
        } else if (!profile.isFactionMember()) {
            player.sendSystemMessage(Component.literal("[WP] Дождись принятия командиром во фракцию — только тогда можно ломать/строить и атаковать.").withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && cannotActInWorld(player)) {
            event.setCanceled(true);
            notifyRestricted(player);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && cannotActInWorld(player)) {
            event.setCanceled(true);
            notifyRestricted(player);
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (isRestricted(player)) {
            event.setCanceled(true);
            notifyRestricted(player);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && cannotActInWorld(player)) {
            event.setCanceled(true);
            notifyRestricted(player);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isRestricted(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }

        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);

        // Re-prompt the login screen every 5s as long as the player hasn't authenticated.
        // This is the safety net for any edge case where the client managed to close the
        // screen (e.g. /reload world causing a screen reset).
        if (!profile.isLoggedIn()) {
            if (player.tickCount % 100 == 0) {
                WarLoginHandler.promptLogin(player);
            }
        }

        if (profile.isLoggedIn() && profile.isOnboarded()) {
            return;
        }

        // Captcha timeout enforcement: if the player has had the captcha issued
        // and hasn't entered it within CAPTCHA_TIMEOUT_MS, kick them with the
        // standard 90s cooldown so they can't immediately reconnect.
        long issuedAt = CaptchaManager.getIssuedAt(player.getUUID());
        if (profile.isLoggedIn() && !profile.isCaptchaPassed() && issuedAt > 0L
                && System.currentTimeMillis() - issuedAt > CaptchaManager.CAPTCHA_TIMEOUT_MS) {
            WarPlayerDataStore.get().appendAuditLog(player.getServer(),
                    player.getGameProfile().getName() + " timed out on captcha (kicked, 90s cooldown)");
            CaptchaManager.kickForFailure(player);
            return;
        }

        // While the player is in the sky cage, CaptchaManager owns their position
        // and abilities — we just keep them pinned every tick and skip the
        // intro-radius logic below.
        if (CaptchaManager.isPending(player.getUUID())) {
            CaptchaManager.enforceSkyCage(player);
            return;
        }

        // After successful captcha, keep the player floating in sky-cage until they
        // submit /wp rpname. We do not have an active CaptchaManager session anymore
        // (CODES is empty), so we re-apply the abilities ourselves on every tick.
        if (profile.isCaptchaPassed() && !profile.hasRpName()) {
            keepFloating(player);
            return;
        }

        // Player is still in the gated onboarding zone if they are unauthenticated
        // OR not yet onboarded OR (RestrictNewPlayers is on AND they have not
        // chosen any side, neither full faction nor candidate). Once the player
        // picks a candidate via the spawn NPC we leave them at the base.
        boolean shouldPin = !profile.isLoggedIn()
                || !profile.isOnboarded()
                || (WarServerSettings.get().isRestrictNewPlayers()
                    && !profile.isFactionMember()
                    && !profile.getCandidateFaction().isPlayable());
        if (!shouldPin) {
            return;
        }

        WarServerSettings settings = WarServerSettings.get();
        WarpPoint intro = settings.getIntroPoint().orElse(null);
        if (intro == null) {
            return;
        }

        if (intro.dimension() != null && player.serverLevel().dimension().equals(intro.dimension())) {
            double radius = settings.getIntroRadius();
            double radiusSq = radius * radius;
            double dx = player.getX() - intro.x();
            double dz = player.getZ() - intro.z();
            boolean tooFarHorizontal = (dx * dx + dz * dz) > radiusSq;
            boolean tooLow = player.getY() < intro.y() - 1.0D;
            if (tooFarHorizontal || tooLow) {
                intro.teleport(player);
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.fallDistance = 0.0F;
            } else {
                net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
                if (motion.y < 0.0D) {
                    player.setDeltaMovement(motion.x, 0.0D, motion.z);
                    player.fallDistance = 0.0F;
                    player.hurtMarked = true;
                }
            }
        } else {
            intro.teleport(player);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.fallDistance = 0.0F;
        }
    }

    @SuppressWarnings("deprecation")
    private static void keepFloating(ServerPlayer player) {
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        // Cap downward drift: if the player has fallen below Y=200, snap them back to Y=300.
        if (player.getY() < 200.0D) {
            player.teleportTo(
                    player.serverLevel(),
                    Math.floor(player.getX()) + 0.5D,
                    300.0D,
                    Math.floor(player.getZ()) + 0.5D,
                    java.util.Set.<net.minecraft.world.entity.RelativeMovement>of(),
                    player.getYRot(),
                    player.getXRot()
            );
        }
        if (!player.getAbilities().mayfly || !player.getAbilities().flying || !player.getAbilities().invulnerable) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
        }
    }
}
