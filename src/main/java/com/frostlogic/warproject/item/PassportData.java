package com.frostlogic.warproject.item;

import com.frostlogic.warproject.server.Country;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.Rank;
import com.frostlogic.warproject.server.Subdivision;
import com.frostlogic.warproject.server.SubdivisionStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PassportData(
        String ownerUuid,
        String rpName,
        int age,
        String country,
        String faction,
        String rankId,
        String subdivision) {

    public static final PassportData EMPTY = new PassportData("", "", 0, Country.UNKNOWN.id(), Faction.NONE.id(), Rank.NONE.id(), "");

    public static final Codec<PassportData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("ownerUuid", "").forGetter(PassportData::ownerUuid),
            Codec.STRING.optionalFieldOf("rpName", "").forGetter(PassportData::rpName),
            Codec.INT.optionalFieldOf("age", 0).forGetter(PassportData::age),
            Codec.STRING.optionalFieldOf("country", Country.UNKNOWN.id()).forGetter(PassportData::country),
            Codec.STRING.optionalFieldOf("faction", Faction.NONE.id()).forGetter(PassportData::faction),
            Codec.STRING.optionalFieldOf("rankId", Rank.NONE.id()).forGetter(PassportData::rankId),
            Codec.STRING.optionalFieldOf("subdivision", "").forGetter(PassportData::subdivision)
    ).apply(instance, PassportData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PassportData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PassportData decode(RegistryFriendlyByteBuf buf) {
            return new PassportData(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PassportData data) {
            buf.writeUtf(data.ownerUuid);
            buf.writeUtf(data.rpName);
            buf.writeVarInt(data.age);
            buf.writeUtf(data.country);
            buf.writeUtf(data.faction);
            buf.writeUtf(data.rankId);
            buf.writeUtf(data.subdivision);
        }
    };

    public static PassportData fromProfile(WarPlayerProfile profile) {
        if (profile == null) {
            return EMPTY;
        }
        String subdivisionName = "";
        if (profile.hasSubdivision()) {
            Subdivision subdivision = SubdivisionStore.get().get(profile.getSubdivisionId()).orElse(null);
            if (subdivision != null) {
                subdivisionName = subdivision.getName();
            }
        }
        // Candidates (selected a side via NPC but not accepted yet) carry a
        // passport showing the candidate faction and the "Гражданин" label
        // instead of a real military rank.
        Faction faction = profile.getFaction().isPlayable()
                ? profile.getFaction()
                : profile.getCandidateFaction();
        String rankId = profile.getFaction().isPlayable()
                ? profile.getRank().id()
                : (profile.getCandidateFaction().isPlayable() ? "citizen" : profile.getRank().id());
        return new PassportData(
                profile.getUuid().toString(),
                profile.getRpNameOrUsername(),
                profile.getAge(),
                profile.getBirthCountry().id(),
                faction.id(),
                rankId,
                subdivisionName
        );
    }

    public Faction factionEnum() {
        return Faction.fromInput(faction).orElse(Faction.NONE);
    }

    public Rank rankEnum() {
        return Rank.fromInput(rankId).orElse(Rank.NONE);
    }

    public Country countryEnum() {
        return Country.fromIdOrUnknown(country);
    }
}
