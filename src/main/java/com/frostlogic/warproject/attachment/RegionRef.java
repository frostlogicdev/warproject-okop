package com.frostlogic.warproject.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Reference to the base region the player is currently inside (or null if none).
 * Stores the UUID of the region definition from config.
 *
 * @param regionId UUID identifying the region, or {@code null} if the player is not in any region.
 */
public record RegionRef(UUID regionId) {

    public static final Codec<RegionRef> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("region_id").forGetter(RegionRef::regionId)
            ).apply(instance, RegionRef::new)
    );

    public static final StreamCodec<ByteBuf, RegionRef> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RegionRef::regionId,
            RegionRef::new
    );
}
