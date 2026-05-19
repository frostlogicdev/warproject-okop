package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Tells the client to open the faction choice confirmation screen.
 * The factionId is the string name of the FactionId enum.
 */
public record OpenFactionChoicePayload(String factionId) implements CustomPacketPayload {
    public static final int MAX_FACTION_LENGTH = 16;

    public static final CustomPacketPayload.Type<OpenFactionChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "open_faction_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFactionChoicePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenFactionChoicePayload decode(RegistryFriendlyByteBuf buf) {
            return new OpenFactionChoicePayload(buf.readUtf(MAX_FACTION_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OpenFactionChoicePayload payload) {
            buf.writeUtf(payload.factionId, MAX_FACTION_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
