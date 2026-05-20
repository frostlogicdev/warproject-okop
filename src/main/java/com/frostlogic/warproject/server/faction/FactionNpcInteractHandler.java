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
     * Right-clicking a recruiter NPC always cancels the vanilla interaction so
     * the player never gets the "Mob does nothing" feel. After that:
     * <ul>
     *   <li>OPs (permission level ≥ 2) always get the choice screen — useful for
     *       admin testing regardless of the player's lifecycle state.</li>
     *   <li>{@code FACTIONLESS} players get the choice screen.</li>
     *   <li>Players who already have a faction are told so explicitly.</li>
     *   <li>Players in a pre-faction lifecycle stage (NEW / LOGIN_PENDING /
     *       CAPTCHA / RPNAME_REQUIRED) get a state-specific hint instead of
     *       silently being ignored.</li>
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

        FactionId factionId = npc.getFactionId();

        // Admin override: always open the screen, regardless of state. Lets OPs
        // smoke-test the choice flow without going through full onboarding.
        if (player.hasPermissions(2)) {
            sendOpenChoice(player, factionId);
            return;
        }

        // Already has a faction (CANDIDATE / ACCEPTED / CAPTURED) — explain why.
        Optional<FactionId> existingFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (existingFaction.isPresent()) {
            player.sendSystemMessage(Component.translatable("wp.faction.already_chosen"));
            return;
        }

        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state == PlayerState.FACTIONLESS) {
            sendOpenChoice(player, factionId);
            return;
        }

        // Player is in a pre-faction lifecycle stage. Give them a state-specific
        // hint instead of silent failure — the server-side legacy bridge can map
        // these onto JSON-pipeline players too.
        Component hint = switch (state) {
            case NEW, REGISTERED_PENDING, LOGIN_PENDING ->
                    Component.translatable("wp.faction.not_logged_in");
            case CAPTCHA ->
                    Component.translatable("wp.faction.captcha_first");
            case RPNAME_REQUIRED ->
                    Component.translatable("wp.faction.rpname_first");
            // Any state outside of the above (CANDIDATE / ACCEPTED / CAPTURED)
            // would have been caught by the `existingFaction` branch above; if
            // it somehow falls through, treat it as already chosen.
            default ->
                    Component.translatable("wp.faction.already_chosen");
        };
        player.sendSystemMessage(hint);
    }

    private static void sendOpenChoice(ServerPlayer player, FactionId factionId) {
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
