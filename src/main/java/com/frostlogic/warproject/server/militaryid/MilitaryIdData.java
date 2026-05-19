package com.frostlogic.warproject.server.militaryid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Military ID card data record. Contains all fields for a faction member's military identity.
 * Used as a DataComponent on the Military ID card item and transmitted via network payloads.
 * <p>
 * Requirements: 4.2, 4.3, 4.4
 * Design: §2 Military ID System — MilitaryIdData
 */
public record MilitaryIdData(
        String passportIdRef,
        String faction,
        String rankId,
        String subdivision,
        List<String> awards,
        String enlistmentDate,
        String acceptingOfficer
) {
    public static final int MAX_FIELD_LENGTH = 64;
    public static final int MAX_AWARDS = 32;

    public static final MilitaryIdData EMPTY = new MilitaryIdData("", "", "", "", List.of(), "", "");

    public static final Codec<MilitaryIdData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).fieldOf("passport_id_ref").forGetter(MilitaryIdData::passportIdRef),
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).fieldOf("faction").forGetter(MilitaryIdData::faction),
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).fieldOf("rank_id").forGetter(MilitaryIdData::rankId),
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).fieldOf("subdivision").forGetter(MilitaryIdData::subdivision),
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).listOf(0, MAX_AWARDS).fieldOf("awards").forGetter(MilitaryIdData::awards),
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).fieldOf("enlistment_date").forGetter(MilitaryIdData::enlistmentDate),
            Codec.sizeLimitedString(MAX_FIELD_LENGTH).fieldOf("accepting_officer").forGetter(MilitaryIdData::acceptingOfficer)
    ).apply(instance, MilitaryIdData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MilitaryIdData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MilitaryIdData decode(RegistryFriendlyByteBuf buf) {
            String passportIdRef = buf.readUtf(MAX_FIELD_LENGTH);
            String faction = buf.readUtf(MAX_FIELD_LENGTH);
            String rankId = buf.readUtf(MAX_FIELD_LENGTH);
            String subdivision = buf.readUtf(MAX_FIELD_LENGTH);

            int awardsCount = buf.readVarInt();
            List<String> awards = new ArrayList<>(Math.min(awardsCount, MAX_AWARDS));
            for (int i = 0; i < awardsCount; i++) {
                awards.add(buf.readUtf(MAX_FIELD_LENGTH));
            }

            String enlistmentDate = buf.readUtf(MAX_FIELD_LENGTH);
            String acceptingOfficer = buf.readUtf(MAX_FIELD_LENGTH);
            return new MilitaryIdData(passportIdRef, faction, rankId, subdivision, awards, enlistmentDate, acceptingOfficer);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, MilitaryIdData data) {
            buf.writeUtf(data.passportIdRef, MAX_FIELD_LENGTH);
            buf.writeUtf(data.faction, MAX_FIELD_LENGTH);
            buf.writeUtf(data.rankId, MAX_FIELD_LENGTH);
            buf.writeUtf(data.subdivision, MAX_FIELD_LENGTH);

            int count = Math.min(data.awards.size(), MAX_AWARDS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeUtf(data.awards.get(i), MAX_FIELD_LENGTH);
            }

            buf.writeUtf(data.enlistmentDate, MAX_FIELD_LENGTH);
            buf.writeUtf(data.acceptingOfficer, MAX_FIELD_LENGTH);
        }
    };
}
