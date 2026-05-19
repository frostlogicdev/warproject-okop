package com.frostlogic.warproject.server.faction;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.s2c.OpenFactionChoicePayload;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Server-side event handler that detects right-click (ПКМ) on {@link FactionNpcEntity}
 * and sends {@link OpenFactionChoicePayload} to the client to open the faction choice
 * confirmation screen.
 * <p>
 * Requirements: 6.3, 6.5
 * Design: §3, §8.2
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class FactionNpcInteractHandler {

    private FactionNpcInteractHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Handles {@link PlayerInteractEvent.EntityInteract} for FactionNpcEntity.
     * <p>
     * When a player right-clicks on a FactionNpcEntity:
     * <ul>
     *   <li>If the player is in FACTIONLESS state → sends OpenFactionChoicePayload to open the choice screen</li>
     *   <li>If the player already has a faction → sends error message "Фракция уже выбрана"</li>
     *   <li>If the player is not in a valid state for faction choice → ignores silently</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof FactionNpcEntity npc)) {
            return;
        }

        // Cancel the event to prevent default mob interaction
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        // Check player state
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());

        // If player already has a faction (CANDIDATE, ACCEPTED, CAPTURED) → reject
        Optional<FactionId> existingFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (existingFaction.isPresent()) {
            player.sendSystemMessage(Component.translatable("wp.faction.already_chosen"));
            return;
        }

        // Only allow interaction in FACTIONLESS state
        if (state != PlayerState.FACTIONLESS) {
            // Player is not in a state where they can choose a faction (e.g. still in auth/captcha)
            return;
        }

        // Send S2C payload to open the faction choice confirmation screen
        FactionId factionId = npc.getFactionId();
        PacketDistributor.sendToPlayer(player, new OpenFactionChoicePayload(factionId.getSerializedName()));

        WarProject.LOGGER.debug("[WarProject] Sent OpenFactionChoicePayload to {}: faction={}",
                player.getGameProfile().getName(), factionId.getSerializedName());
    }

    /**
     * Handles {@link PlayerInteractEvent.EntityInteractSpecific} for FactionNpcEntity.
     * <p>
     * Cancels the specific interaction to prevent any default behavior (e.g. opening
     * villager trade UI if the entity were a villager subclass).
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (event.getTarget() instanceof FactionNpcEntity) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
