package com.frostlogic.warproject.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Tracks the number of consecutive ticks a player has spent in an enemy region.
 * Used by the collaborator auto-detection system (Requirement 11.4).
 *
 * @param ticks number of consecutive ticks in enemy territory
 */
public record EnemyTickCounter(int ticks) {

    public static final EnemyTickCounter ZERO = new EnemyTickCounter(0);

    public static final Codec<EnemyTickCounter> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("ticks").forGetter(EnemyTickCounter::ticks)
            ).apply(instance, EnemyTickCounter::new)
    );

    public static final StreamCodec<ByteBuf, EnemyTickCounter> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, EnemyTickCounter::ticks,
            EnemyTickCounter::new
    );

    /**
     * Returns a new counter incremented by one tick.
     */
    public EnemyTickCounter increment() {
        return new EnemyTickCounter(ticks + 1);
    }

    /**
     * Returns the zero counter (reset).
     */
    public EnemyTickCounter reset() {
        return ZERO;
    }
}
