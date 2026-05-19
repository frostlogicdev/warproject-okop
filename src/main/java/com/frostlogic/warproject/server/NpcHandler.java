package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class NpcHandler {
    public static final String TAG_PREFIX = "wp_npc_side:";

    private NpcHandler() {
    }

    public static boolean spawnSideNpc(ServerPlayer player, Faction faction) {
        return spawnSideNpcAt(player, faction, player.getX(), player.getY(), player.getZ());
    }

    public static boolean spawnSideNpcAt(ServerPlayer player, Faction faction, double x, double y, double z) {
        if (faction == null || !faction.isPlayable()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return false;
        }

        villager.moveTo(x, y, z, player.getYRot(), 0f);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setPersistenceRequired();
        villager.setSilent(true);
        villager.setCustomName(Component.literal("Вербовщик " + faction.displayName()).withStyle(faction.color()));
        villager.setCustomNameVisible(true);
        villager.addTag(TAG_PREFIX + faction.id());

        return level.addFreshEntity(villager);
    }

    public static int removeNearestSideNpc(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(5.0D);
        List<Villager> targets = level.getEntitiesOfClass(Villager.class, box, NpcHandler::hasSideTag);
        for (Villager villager : targets) {
            villager.discard();
        }
        return targets.size();
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }
        Optional<Faction> faction = factionFromTags(villager);
        if (faction.isEmpty()) {
            return;
        }

        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
        handleNpcClick(player, faction.get());
    }

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }
        if (hasSideTag(villager)) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean hasSideTag(Villager villager) {
        for (String tag : villager.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Faction> factionFromTags(Villager villager) {
        for (String tag : villager.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return Faction.fromInput(tag.substring(TAG_PREFIX.length())).filter(Faction::isPlayable);
            }
        }
        return Optional.empty();
    }

    private static void handleNpcClick(ServerPlayer player, Faction faction) {
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);

        if (!player.hasPermissions(2) && !profile.isLoggedIn()) {
            player.sendSystemMessage(Component.literal("[WP] Сначала войди через окно входа.").withStyle(ChatFormatting.RED));
            return;
        }

        if (!profile.isCaptchaPassed()) {
            player.sendSystemMessage(Component.literal("[WP] Сначала пройди капчу: /wp captcha <код>").withStyle(ChatFormatting.RED));
            return;
        }

        if (!profile.hasRpName()) {
            player.sendSystemMessage(Component.literal("[WP] Сначала введи РП имя: /wp rpname Имя Фамилия").withStyle(ChatFormatting.RED));
            return;
        }

        if (profile.isFactionMember()) {
            player.sendSystemMessage(Component.literal("[WP] Ты уже состоишь в стороне " + profile.getFaction().displayName()).withStyle(ChatFormatting.RED));
            return;
        }

        profile.setCandidateFaction(faction);
        WarPlayerDataStore.get().appendAuditLog(player.getServer(), player.getGameProfile().getName() + " chose candidate via NPC: " + faction.displayName());
        WarPlayerDataStore.get().save();
        LegacyAttachmentBridge.sync(player);
        // Issue a "Гражданин" passport for the candidate so they can use it before
        // a commander accepts them. Passport label updates again on accept.
        PassportManager.issue(player, profile);
        WarPrefixManager.refresh(player);

        Optional<WarpPoint> base = WarServerSettings.get().getBasePoint(faction);
        if (base.isPresent()) {
            base.get().teleport(player);
            player.sendSystemMessage(Component.literal("[WP] Ты выбрал сторону: " + faction.displayName() + ". Найди командира на базе.").withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("[WP] Сторона выбрана: " + faction.displayName() + ", но точка базы не настроена. Сообщи администрации.").withStyle(ChatFormatting.YELLOW));
        }
    }

}
