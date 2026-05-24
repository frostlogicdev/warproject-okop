package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player picked a vehicle in the transport choice screen.
 *
 * @param npcEntityId Entity id of the {@code TransportNpcEntity} the player is
 *                    interacting with. The server re-validates proximity and
 *                    rank gating before issuing the item — a forged payload
 *                    can't bypass either check.
 * @param itemId      Serialized {@link ResourceLocation} of the chosen vehicle
 *                    item (e.g. {@code "iv:tiger_2"}).
 */
public record TransportChoicePayload(int npcEntityId, String itemId) implements CustomPacketPayload {

    public static final int MAX_ITEM_LENGTH = 128;

    public static final Type<TransportChoicePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "transport_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransportChoicePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TransportChoicePayload decode(RegistryFriendlyByteBuf buf) {
            int npcId = buf.readVarInt();
            String item = buf.readUtf(MAX_ITEM_LENGTH);
            return new TransportChoicePayload(npcId, item);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TransportChoicePayload payload) {
            buf.writeVarInt(payload.npcEntityId);
            buf.writeUtf(payload.itemId, MAX_ITEM_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
