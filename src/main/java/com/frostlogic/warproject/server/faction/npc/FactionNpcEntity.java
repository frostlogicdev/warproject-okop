package com.frostlogic.warproject.server.faction.npc;

import com.frostlogic.warproject.attachment.FactionId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    private FactionId factionId;

    public FactionNpcEntity(EntityType<? extends FactionNpcEntity> entityType, Level level) {
        super(entityType, level);
        this.factionId = FactionId.ZARNAVIA; // default, overridden on spawn
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        setSilent(true);
    }

    /**
     * Returns the faction this NPC represents.
     */
    public FactionId getFactionId() {
        return factionId;
    }

    /**
     * Sets the faction this NPC represents and updates the custom name display.
     */
    public void setFactionId(FactionId factionId) {
        this.factionId = factionId;
        updateDisplayName();
    }

    private void updateDisplayName() {
        setCustomName(
                Component.translatable("wp.npc.recruiter", Component.translatable(factionId.displayNameKey()))
                        .withStyle(factionId.color())
        );
        setCustomNameVisible(true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(NBT_FACTION_ID, factionId.getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_FACTION_ID)) {
            String name = tag.getString(NBT_FACTION_ID);
            for (FactionId id : FactionId.values()) {
                if (id.getSerializedName().equals(name)) {
                    this.factionId = id;
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
