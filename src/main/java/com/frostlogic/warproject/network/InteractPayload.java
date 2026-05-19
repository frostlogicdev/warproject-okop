package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record InteractPayload(UUID target, String action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InteractPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "interact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InteractPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public InteractPayload decode(RegistryFriendlyByteBuf buf) {
            return new InteractPayload(buf.readUUID(), buf.readUtf());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, InteractPayload payload) {
            buf.writeUUID(payload.target);
            buf.writeUtf(payload.action);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
