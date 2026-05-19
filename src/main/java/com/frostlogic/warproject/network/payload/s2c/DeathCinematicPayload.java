package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Triggers the realistic death cinematic on the client.
 * <p>
 * Sent when the player dies. The client renders a cinematic overlay:
 * darkening, blur, blinking, and eye-close animation before respawn.
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code headshot} — true if the killing blow was to the head (projectile Y > eye height)</li>
 *   <li>{@code respawnDelayTicks} — server-enforced respawn delay (default 1200 = 60s)</li>
 * </ul>
 */
public record DeathCinematicPayload(boolean headshot, int respawnDelayTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeathCinematicPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "death_cinematic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeathCinematicPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DeathCinematicPayload decode(RegistryFriendlyByteBuf buf) {
            boolean headshot = buf.readBoolean();
            int respawnDelay = buf.readVarInt();
            return new DeathCinematicPayload(headshot, respawnDelay);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, DeathCinematicPayload payload) {
            buf.writeBoolean(payload.headshot);
            buf.writeVarInt(payload.respawnDelayTicks);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
