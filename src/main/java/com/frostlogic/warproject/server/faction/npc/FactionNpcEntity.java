package com.frostlogic.warproject.server.faction.npc;

import com.frostlogic.warproject.attachment.FactionId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Custom NPC entity representing a faction recruiter in the faction choice hall.
 * <p>
 * This entity has no AI, is invulnerable, persists across chunk unloads, and stores
 * its associated {@link FactionId} in NBT for serialization.
 * <p>
 * Requirements: 6.1, 6.2
 * Design: §3 — server/faction/npc/FactionNpcEntity.java
 */
public class FactionNpcEntity extends Mob {

    private static final String NBT_FACTION_ID = "FactionId";

    /** Synchronizes the faction id (as ordinal) to the client for renderer use. */
    private static final EntityDataAccessor<Integer> DATA_FACTION_ORDINAL =
            SynchedEntityData.defineId(FactionNpcEntity.class, EntityDataSerializers.INT);

    public FactionNpcEntity(EntityType<? extends FactionNpcEntity> entityType, Level level) {
        super(entityType, level);
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        setSilent(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACTION_ORDINAL, FactionId.ZARNAVIA.ordinal());
    }

    /**
     * Returns the faction this NPC represents (resolved from synced data so this
     * works correctly on both server and client).
     */
    public FactionId getFactionId() {
        int ordinal = this.entityData.get(DATA_FACTION_ORDINAL);
        FactionId[] values = FactionId.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return FactionId.ZARNAVIA;
        }
        return values[ordinal];
    }

    /**
     * Sets the faction this NPC represents and updates the custom name display.
     * Must be called on the server; the change is synced to clients automatically.
     */
    public void setFactionId(FactionId factionId) {
        this.entityData.set(DATA_FACTION_ORDINAL, factionId.ordinal());
        updateDisplayName();
    }

    private void updateDisplayName() {
        FactionId fid = getFactionId();
        setCustomName(
                Component.translatable("wp.npc.recruiter", Component.translatable(fid.displayNameKey()))
                        .withStyle(fid.color())
        );
        setCustomNameVisible(true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(NBT_FACTION_ID, getFactionId().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_FACTION_ID)) {
            String name = tag.getString(NBT_FACTION_ID);
            for (FactionId id : FactionId.values()) {
                if (id.getSerializedName().equals(name)) {
                    this.entityData.set(DATA_FACTION_ORDINAL, id.ordinal());
                    break;
                }
            }
        }
        // Re-apply NPC properties after load (safety net)
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        setSilent(true);
        updateDisplayName();
    }

    /**
     * Prevent the NPC from being pushed by other entities.
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Prevent the NPC from being affected by knockback.
     */
    @Override
    public void knockback(double strength, double x, double z) {
        // No-op: NPC should not move
    }
}
