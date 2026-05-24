package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: Tells the client to open the transport choice screen for the player's
 * current faction NPC.
 * <p>
 * The server pre-computes the list of vehicles the player is currently
 * allowed to claim (after rank gating) and sends only those entries. This
 * avoids leaking the full vehicle catalog to clients and keeps the GUI logic
 * trivial — every entry in the list is selectable.
 *
 * @param factionId   Serialized faction id of the NPC (so the screen can
 *                    show faction colors / emblem).
 * @param npcEntityId Entity id of the NPC that triggered the open (used by
 *                    the C2S response so the server can validate proximity
 *                    again at claim time).
 * @param entries     Allowed vehicles, in display order. Each entry is
 *                    {@code item_id|display_translation_key}.
 */
public record OpenTransportChoicePayload(
        String factionId,
        int npcEntityId,
        List<String> entries
) implements CustomPacketPayload {

    public static final int MAX_FACTION_LENGTH = 16;
    public static final int MAX_ENTRY_LENGTH = 256;
    public static final int MAX_ENTRIES = 64;

    public static final Type<OpenTransportChoicePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "open_transport_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTransportChoicePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenTransportChoicePayload decode(RegistryFriendlyByteBuf buf) {
            String fid = buf.readUtf(MAX_FACTION_LENGTH);
            int npcId = buf.readVarInt();
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_ENTRIES) {
                throw new IllegalArgumentException("OpenTransportChoicePayload: invalid entry count " + n);
            }
            List<String> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                entries.add(buf.readUtf(MAX_ENTRY_LENGTH));
            }
            return new OpenTransportChoicePayload(fid, npcId, entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OpenTransportChoicePayload payload) {
            buf.writeUtf(payload.factionId, MAX_FACTION_LENGTH);
            buf.writeVarInt(payload.npcEntityId);
            List<String> entries = payload.entries;
            int n = Math.min(entries.size(), MAX_ENTRIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeUtf(entries.get(i), MAX_ENTRY_LENGTH);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
