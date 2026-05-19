package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.PublicView;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.UUID;

/**
 * S2C: Sends radial menu data to the client (target UUID, visible items, target public view).
 * <p>
 * Requirements: 4.1, 4.2, 4.3
 * Design: §4.2, §6
 */
public record RadialMenuPayload(UUID targetUuid, EnumSet<RadialMenuItem> visibleItems, PublicView targetView) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RadialMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "radial_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadialMenuPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RadialMenuPayload decode(RegistryFriendlyByteBuf buf) {
            UUID target = buf.readUUID();
            // Encode EnumSet as a bitmask (RadialMenuItem has ≤ 32 values)
            int mask = buf.readVarInt();
            EnumSet<RadialMenuItem> items = EnumSet.noneOf(RadialMenuItem.class);
            for (RadialMenuItem item : RadialMenuItem.values()) {
                if ((mask & (1 << item.ordinal())) != 0) {
                    items.add(item);
                }
            }
            PublicView view = PublicView.STREAM_CODEC.decode(buf);
            return new RadialMenuPayload(target, items, view);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RadialMenuPayload payload) {
            buf.writeUUID(payload.targetUuid);
            int mask = 0;
            for (RadialMenuItem item : payload.visibleItems) {
                mask |= (1 << item.ordinal());
            }
            buf.writeVarInt(mask);
            PublicView.STREAM_CODEC.encode(buf, payload.targetView);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
