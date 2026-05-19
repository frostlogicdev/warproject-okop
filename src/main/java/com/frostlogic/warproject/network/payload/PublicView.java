package com.frostlogic.warproject.network.payload;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A safe public projection of a target player's state, sent as part of
 * {@link com.frostlogic.warproject.network.payload.s2c.RadialMenuPayload}.
 * Contains only information that the initiator is allowed to see.
 */
public record PublicView(
        UUID uuid,
        FactionId faction,
        Role role,
        PlayerState status,
        boolean collaborator,
        @Nullable String rpFullName
) {
    public static final int MAX_NAME_LENGTH = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, PublicView> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PublicView decode(RegistryFriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            FactionId faction = FactionId.STREAM_CODEC.decode(buf);
            Role role = Role.STREAM_CODEC.decode(buf);
            PlayerState status = PlayerState.STREAM_CODEC.decode(buf);
            boolean collaborator = buf.readBoolean();
            boolean hasName = buf.readBoolean();
            String rpFullName = hasName ? buf.readUtf(MAX_NAME_LENGTH) : null;
            return new PublicView(uuid, faction, role, status, collaborator, rpFullName);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PublicView view) {
            buf.writeUUID(view.uuid);
            FactionId.STREAM_CODEC.encode(buf, view.faction);
            Role.STREAM_CODEC.encode(buf, view.role);
            PlayerState.STREAM_CODEC.encode(buf, view.status);
            buf.writeBoolean(view.collaborator);
            buf.writeBoolean(view.rpFullName != null);
            if (view.rpFullName != null) {
                buf.writeUtf(view.rpFullName, MAX_NAME_LENGTH);
            }
        }
    };
}
