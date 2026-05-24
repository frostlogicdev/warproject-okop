package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.s2c.OpenTransportChoicePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side handler for right-click interactions with
 * {@link TransportNpcEntity}.
 * <p>
 * Three outcomes:
 * <ul>
 *   <li><b>OP</b> — always sees the full vehicle catalog (admin testing).</li>
 *   <li><b>Eligible accepted soldier matching faction</b> — receives an
 *       {@link OpenTransportChoicePayload} with the list of vehicles their
 *       current rank can claim. If the rank allows nothing, the NPC plays
 *       {@link TransportNpcAnimation#HEAD_SHAKE} and the screen does not open.</li>
 *   <li><b>Anyone else</b> (wrong faction, candidate, captured, etc.) — NPC
 *       head-shakes and the player gets a chat hint.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class TransportNpcInteractHandler {

    private TransportNpcInteractHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof TransportNpcEntity npc)) {
            return;
        }

        // Cancel vanilla mob interaction so we don't get the silent "does nothing".
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        FactionId npcFaction = npc.getFactionId();

        // OP shortcut — bypass all gates, always open the screen.
        if (player.hasPermissions(2)) {
            List<TransportVehicle> all = TransportVehicleService.catalog(npcFaction);
            openScreen(player, npc, npcFaction, all);
            return;
        }

        // Lifecycle / faction gates — same "head-shake + hint" treatment for any
        // failure so the NPC behaves consistently.
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED) {
            denyWithShake(player, npc, hintForState(state));
            return;
        }

        Optional<FactionId> playerFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (playerFaction.isEmpty() || playerFaction.get() != npcFaction) {
            denyWithShake(player, npc, Component.translatable("wp.transport.wrong_faction"));
            return;
        }

        List<TransportVehicle> allowed = TransportVehicleService.availableTo(player, npcFaction);
        if (allowed.isEmpty()) {
            denyWithShake(player, npc, Component.translatable("wp.transport.rank_too_low"));
            return;
        }

        openScreen(player, npc, npcFaction, allowed);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (event.getTarget() instanceof TransportNpcEntity) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static void openScreen(ServerPlayer player, TransportNpcEntity npc, FactionId faction,
                                   List<TransportVehicle> entries) {
        List<String> encoded = new ArrayList<>(entries.size());
        for (TransportVehicle v : entries) {
            // Encoded form: "item_id|display_key" — pipe separator avoids collision
            // with the colon inside namespaced item ids.
            encoded.add(v.itemId().toString() + "|" + v.displayKey());
        }
        PacketDistributor.sendToPlayer(
                player,
                new OpenTransportChoicePayload(faction.getSerializedName(), npc.getId(), encoded)
        );
        WarProject.LOGGER.debug("[WP Transport] Sent OpenTransportChoice to {} for {}: {} entries.",
                player.getGameProfile().getName(), faction.getSerializedName(), encoded.size());
    }

    private static void denyWithShake(ServerPlayer player, TransportNpcEntity npc, Component message) {
        npc.playAnimation(TransportNpcAnimation.HEAD_SHAKE);
        player.sendSystemMessage(message);
    }

    private static Component hintForState(PlayerState state) {
        return switch (state) {
            case NEW, REGISTERED_PENDING, LOGIN_PENDING ->
                    Component.translatable("wp.transport.not_logged_in");
            case CAPTCHA -> Component.translatable("wp.transport.captcha_first");
            case RPNAME_REQUIRED -> Component.translatable("wp.transport.rpname_first");
            case FACTIONLESS -> Component.translatable("wp.transport.no_faction");
            case CANDIDATE -> Component.translatable("wp.transport.candidate");
            case CAPTURED -> Component.translatable("wp.transport.captured");
            // ACCEPTED is handled in the caller — this branch is a safety net.
            default -> Component.translatable("wp.transport.unavailable");
        };
    }
}
