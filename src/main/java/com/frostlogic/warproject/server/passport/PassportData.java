package com.frostlogic.warproject.server.passport;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Passport data record matching design §4.4.
 * Contains all passport fields needed for NBT persistence (via DataComponentType)
 * and network synchronization.
 * <p>
 * Requirements: 12.x
 * Design: §4.4
 */
public record PassportData(
        String passportId,
        FactionId faction,
        String rpName,
        String rpSurname,
        String dateOfBirth,
        long signatureSeed,
        PlayerState status,
        @Nullable Long acceptedAt,
        @Nullable String acceptedBy,
        boolean trophy
) {
    public static final int MAX_FIELD_LENGTH = 64;

    /**
     * Codec for NBT persistence via DataComponentType.
     * Uses optionalFieldOf for nullable fields (acceptedAt, acceptedBy)
     * so they are omitted from NBT when null.
     */
    public static final Codec<PassportData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("passport_id").forGetter(PassportData::passportId),
            FactionId.CODEC.fieldOf("faction").forGetter(PassportData::faction),
            Codec.STRING.fieldOf("rp_name").forGetter(PassportData::rpName),
            Codec.STRING.fieldOf("rp_surname").forGetter(PassportData::rpSurname),
            Codec.STRING.fieldOf("date_of_birth").forGetter(PassportData::dateOfBirth),
            Codec.LONG.fieldOf("signature_seed").forGetter(PassportData::signatureSeed),
            PlayerState.CODEC.fieldOf("status").forGetter(PassportData::status),
            Codec.LONG.optionalFieldOf("accepted_at").forGetter(d -> java.util.Optional.ofNullable(d.acceptedAt())),
            Codec.STRING.optionalFieldOf("accepted_by").forGetter(d -> java.util.Optional.ofNullable(d.acceptedBy())),
            Codec.BOOL.fieldOf("trophy").forGetter(PassportData::trophy)
    ).apply(instance, PassportData::create));

    /**
     * Factory method used by the Codec to handle Optional → @Nullable conversion.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static PassportData create(
            String passportId,
            FactionId faction,
            String rpName,
            String rpSurname,
            String dateOfBirth,
            long signatureSeed,
            PlayerState status,
            java.util.Optional<Long> acceptedAt,
            java.util.Optional<String> acceptedBy,
            boolean trophy
    ) {
        return new PassportData(
                passportId, faction, rpName, rpSurname, dateOfBirth,
                signatureSeed, status, acceptedAt.orElse(null), acceptedBy.orElse(null), trophy
        );
    }

    /**
     * StreamCodec for network synchronization.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PassportData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PassportData decode(RegistryFriendlyByteBuf buf) {
            String passportId = buf.readUtf(MAX_FIELD_LENGTH);
            FactionId faction = FactionId.STREAM_CODEC.decode(buf);
            String rpName = buf.readUtf(MAX_FIELD_LENGTH);
            String rpSurname = buf.readUtf(MAX_FIELD_LENGTH);
            String dateOfBirth = buf.readUtf(MAX_FIELD_LENGTH);
            long signatureSeed = buf.readLong();
            PlayerState status = PlayerState.STREAM_CODEC.decode(buf);
            boolean hasAcceptedAt = buf.readBoolean();
            Long acceptedAt = hasAcceptedAt ? buf.readLong() : null;
            boolean hasAcceptedBy = buf.readBoolean();
            String acceptedBy = hasAcceptedBy ? buf.readUtf(MAX_FIELD_LENGTH) : null;
            boolean trophy = buf.readBoolean();
            return new PassportData(passportId, faction, rpName, rpSurname, dateOfBirth, signatureSeed, status, acceptedAt, acceptedBy, trophy);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PassportData data) {
            buf.writeUtf(data.passportId, MAX_FIELD_LENGTH);
            FactionId.STREAM_CODEC.encode(buf, data.faction);
            buf.writeUtf(data.rpName, MAX_FIELD_LENGTH);
            buf.writeUtf(data.rpSurname, MAX_FIELD_LENGTH);
            buf.writeUtf(data.dateOfBirth, MAX_FIELD_LENGTH);
            buf.writeLong(data.signatureSeed);
            PlayerState.STREAM_CODEC.encode(buf, data.status);
            buf.writeBoolean(data.acceptedAt != null);
            if (data.acceptedAt != null) {
                buf.writeLong(data.acceptedAt);
            }
            buf.writeBoolean(data.acceptedBy != null);
            if (data.acceptedBy != null) {
                buf.writeUtf(data.acceptedBy, MAX_FIELD_LENGTH);
            }
            buf.writeBoolean(data.trophy);
        }
    };
}
