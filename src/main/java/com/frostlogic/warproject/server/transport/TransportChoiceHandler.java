package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.network.payload.c2s.TransportChoicePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for {@link TransportChoicePayload}.
 * <p>
 * Validates the C2S claim end-to-end:
 * <ol>
 *   <li>NPC entity exists in the player's current world.</li>
 *   <li>Player is within {@link #MAX_INTERACT_DISTANCE_SQ} of the NPC (anti-cheat
 *       against teleport-then-claim).</li>
 *   <li>Player still passes rank gating for the requested vehicle (the GUI is
 *       authoritative for *display* but the client can craft a forged payload,
 *       so the server re-checks).</li>
 * </ol>
 * On success: gives the player the item and triggers the {@code ARM_WAVE}
 * animation. On any failure: triggers {@code HEAD_SHAKE} and sends a hint.
 */
public final class TransportChoiceHandler {

    /** Maximum allowed squared distance from player to NPC at claim time (8 blocks). */
    private static final double MAX_INTERACT_DISTANCE_SQ = 8.0D * 8.0D;

    private TransportChoiceHandler() {
    }

    public static void onChoice(TransportChoicePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> handle(player, payload));
    }

    private static void handle(ServerPlayer player, TransportChoicePayload payload) {
        Entity entity = player.serverLevel().getEntity(payload.npcEntityId());
        if (!(entity instanceof TransportNpcEntity npc)) {
            player.sendSystemMessage(Component.translatable("wp.transport.npc_gone"));
            return;
        }

        if (npc.distanceToSqr(player) > MAX_INTERACT_DISTANCE_SQ) {
            // Player drifted away (or tried to abuse). No NPC animation since
            // the NPC may be out of render distance anyway.
            player.sendSystemMessage(Component.translatable("wp.transport.too_far"));
            return;
        }

        FactionId faction = npc.getFactionId();

        ResourceLocation itemId = ResourceLocation.tryParse(payload.itemId());
        if (itemId == null) {
            denyWithShake(player, npc, Component.translatable("wp.transport.bad_item"));
            return;
        }

        if (!TransportVehicleService.canClaim(player, faction, itemId)) {
            // Either rank too low, wrong faction (re-check after caching), or the
            // vehicle isn't in the config anymore. Single error to avoid leaking
            // which gate failed.
            denyWithShake(player, npc, Component.translatable("wp.transport.rank_too_low"));
            return;
        }

        TransportVehicle vehicle = TransportVehicleService.findByItemId(faction, itemId).orElse(null);
        if (vehicle == null) {
            denyWithShake(player, npc, Component.translatable("wp.transport.bad_item"));
            return;
        }

        ItemStack stack = new ItemStack(vehicle.resolveItem());
        if (stack.isEmpty() || stack.is(Items.BARRIER)) {
            // resolveItem() falls back to barrier when the vehicle's mod isn't
            // installed OR when the configured item id is unknown (e.g. stale
            // placeholder ids from an older wp-server.toml). The GUI happily
            // shows the barrier icon so admins can spot bad config, but we
            // must NEVER actually hand a barrier item to the player.
            WarProject.LOGGER.warn("[WP Transport] Refusing to grant '{}' to {} — item not registered "
                    + "(barrier fallback). Update config/wp-server.toml.",
                    itemId, player.getGameProfile().getName());
            denyWithShake(player, npc, Component.translatable("wp.transport.mod_missing"));
            return;
        }

        if (!player.getInventory().add(stack)) {
            // Inventory full — drop at player feet so the gift isn't lost.
            player.drop(stack, false);
        }

        npc.playAnimation(TransportNpcAnimation.ARM_WAVE);
        player.sendSystemMessage(Component.translatable("wp.transport.granted",
                Component.translatable(vehicle.displayKey())));
        WarProject.LOGGER.info("[WP Transport] {} claimed {} from {}",
                player.getGameProfile().getName(), itemId, faction.getSerializedName());
    }

    private static void denyWithShake(ServerPlayer player, TransportNpcEntity npc, Component msg) {
        npc.playAnimation(TransportNpcAnimation.HEAD_SHAKE);
        player.sendSystemMessage(msg);
    }
}
