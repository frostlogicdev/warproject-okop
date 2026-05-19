package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player confirms faction choice (after interacting with faction NPC).
 * The factionId is the string name of the FactionId enum (ZARNAVIA or CHERNOGRYAD).
 */
public record FactionChoicePayload(String factionId) implements CustomPacketPayload {
    public static final int MAX_FACTION_LENGTH = 16;

    public static final CustomPacketPayload.Type<FactionChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "faction_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionChoicePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FactionChoicePayload decode(RegistryFriendlyByteBuf buf) {
            return new FactionChoicePayload(buf.readUtf(MAX_FACTION_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FactionChoicePayload payload) {
            buf.writeUtf(payload.factionId, MAX_FACTION_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
