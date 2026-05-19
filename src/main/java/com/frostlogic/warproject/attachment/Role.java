package com.frostlogic.warproject.attachment;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Administrative role within the WarProject mod.
 * Hierarchy: OP > GENERAL > COMMANDER > SOLDIER > CANDIDATE.
 * Placeholder enum — will be expanded in the roles/permissions task.
 */
public enum Role implements StringRepresentable {
    CANDIDATE("candidate", 0),
    SOLDIER("soldier", 1),
    COMMANDER("commander", 2),
    GENERAL("general", 3),
    OP("op", 4);

    public static final Codec<Role> CODEC = StringRepresentable.fromEnum(Role::values);
    public static final StreamCodec<ByteBuf, Role> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], Role::ordinal);

    private final String serializedName;
    private final int level;

    Role(String serializedName, int level) {
        this.serializedName = serializedName;
        this.level = level;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public int level() {
        return level;
    }

    /**
     * Returns true if this role is at least as high as the given role.
     */
    public boolean atLeast(Role other) {
        return this.level >= other.level;
    }
}
