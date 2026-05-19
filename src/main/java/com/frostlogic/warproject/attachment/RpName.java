package com.frostlogic.warproject.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Roleplay name consisting of a first name and surname.
 *
 * @param name    RP first name (Ник)
 * @param surname RP surname (Фамилия)
 */
public record RpName(String name, String surname) {

    public static final Codec<RpName> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("name").forGetter(RpName::name),
                    Codec.STRING.fieldOf("surname").forGetter(RpName::surname)
            ).apply(instance, RpName::new)
    );

    public static final StreamCodec<ByteBuf, RpName> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RpName::name,
            ByteBufCodecs.STRING_UTF8, RpName::surname,
            RpName::new
    );

    /**
     * Returns the full display name "Name Surname".
     */
    public String fullName() {
        return name + " " + surname;
    }
}
