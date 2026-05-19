package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * S2C: Sends a safe public projection of a player's state to the client.
 * Used for HUD/TAB rendering of faction, role, status, collaborator flag, and RP name.
 * <p>
 * Requirements: 4.1, 4.2, 4.3, 21.4
 * Design: §4.2, §6
 */
public record PlayerPublicViewPayload(
        UUID uuid,
        FactionId faction,
        Role role,
        PlayerState status,
        boolean collaborator,
        @Nullable String rpFullName
) implements CustomPacketPayload {
    public static final int MAX_NAME_LENGTH = 64;

    public static final CustomPacketPayload.Type<PlayerPublicViewPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "player_public_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPublicViewPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerPublicViewPayload decode(RegistryFriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            FactionId faction = FactionId.STREAM_CODEC.decode(buf);
            Role role = Role.STREAM_CODEC.decode(buf);
            PlayerState status = PlayerState.STREAM_CODEC.decode(buf);
            boolean collaborator = buf.readBoolean();
            boolean hasName = buf.readBoolean();
            String rpFullName = hasName ? buf.readUtf(MAX_NAME_LENGTH) : null;
            return new PlayerPublicViewPayload(uuid, faction, role, status, collaborator, rpFullName);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerPublicViewPayload payload) {
            buf.writeUUID(payload.uuid);
            FactionId.STREAM_CODEC.encode(buf, payload.faction);
            Role.STREAM_CODEC.encode(buf, payload.role);
            PlayerState.STREAM_CODEC.encode(buf, payload.status);
            buf.writeBoolean(payload.collaborator);
            buf.writeBoolean(payload.rpFullName != null);
            if (payload.rpFullName != null) {
                buf.writeUtf(payload.rpFullName, MAX_NAME_LENGTH);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
