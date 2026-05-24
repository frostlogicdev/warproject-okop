package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.attachment.FactionId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * NPC entity that hands out faction vehicles on right-click.
 * <p>
 * Visually similar to {@link com.frostlogic.warproject.server.faction.npc.FactionNpcEntity}
 * (humanoid model, military skin per faction, invulnerable, no AI) but lives
 * at the faction bases instead of the choice hall and uses an animation state
 * machine to react to player interactions:
 * <ul>
 *   <li>{@link TransportNpcAnimation#HEAD_SHAKE} when the player lacks the
 *       required rank — head oscillates left/right (vanilla {@code yHeadRot}).</li>
 *   <li>{@link TransportNpcAnimation#ARM_WAVE} after a successful vehicle hand-off
 *       — the main hand swings via {@link Mob#swing(InteractionHand)}.</li>
 * </ul>
 * The animation state is synchronised to clients via {@link SynchedEntityData}.
 * After the duration elapses, the server automatically reverts to
 * {@link TransportNpcAnimation#IDLE}.
 */
public class TransportNpcEntity extends Mob {

    private static final String NBT_FACTION_ID = "FactionId";

    /** Synchronises the faction id (as ordinal) to the client for renderer use. */
    private static final EntityDataAccessor<Integer> DATA_FACTION_ORDINAL =
            SynchedEntityData.defineId(TransportNpcEntity.class, EntityDataSerializers.INT);

    /** Synchronises the active animation state (as ordinal of {@link TransportNpcAnimation}). */
    private static final EntityDataAccessor<Integer> DATA_ANIMATION_ORDINAL =
            SynchedEntityData.defineId(TransportNpcEntity.class, EntityDataSerializers.INT);

    /** Server-side counter for the active animation. -1 when IDLE. */
    private int animationTicksRemaining = -1;

    public TransportNpcEntity(EntityType<? extends TransportNpcEntity> entityType, Level level) {
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
        builder.define(DATA_ANIMATION_ORDINAL, TransportNpcAnimation.IDLE.ordinal());
    }

    /** Resolves the faction id from synced data (works on server and client). */
    public FactionId getFactionId() {
        int ordinal = this.entityData.get(DATA_FACTION_ORDINAL);
        FactionId[] values = FactionId.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return FactionId.ZARNAVIA;
        }
        return values[ordinal];
    }

    /** Sets the faction this technician represents and refreshes the display name. */
    public void setFactionId(FactionId factionId) {
        this.entityData.set(DATA_FACTION_ORDINAL, factionId.ordinal());
        updateDisplayName();
    }

    /** Current animation state (synced). */
    public TransportNpcAnimation getAnimation() {
        return TransportNpcAnimation.fromOrdinal(this.entityData.get(DATA_ANIMATION_ORDINAL));
    }

    /**
     * Triggers an animation server-side. Starts the duration countdown and
     * for {@link TransportNpcAnimation#ARM_WAVE} also fires the vanilla swing
     * so the arm visibly moves on clients regardless of the head-shake renderer
     * tweaks.
     */
    public void playAnimation(TransportNpcAnimation animation) {
        this.entityData.set(DATA_ANIMATION_ORDINAL, animation.ordinal());
        this.animationTicksRemaining = animation.durationTicks();
        if (animation == TransportNpcAnimation.ARM_WAVE) {
            this.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Drive the head rotation every tick so the renderer's built-in
        // yHeadRotO → yHeadRot partial-tick interpolation smooths the motion.
        // We deliberately run this on both client and server; the synced
        // animation state stays in lockstep via SynchedEntityData.
        TransportNpcAnimation current = getAnimation();
        if (current == TransportNpcAnimation.HEAD_SHAKE) {
            // ±28° amplitude, ~1 Hz over the 30-tick window → ~3 left/right swings.
            float offset = (float) Math.sin(this.tickCount * 0.9F) * 28.0F;
            this.setYHeadRot(this.getYRot() + offset);
        } else {
            // Pin the head to body yaw so the NPC always faces its spawn direction.
            this.setYHeadRot(this.getYRot());
        }

        // Animation duration countdown is server-authoritative.
        if (this.level().isClientSide) {
            return;
        }
        if (animationTicksRemaining > 0) {
            animationTicksRemaining--;
            if (animationTicksRemaining == 0) {
                animationTicksRemaining = -1;
                this.entityData.set(DATA_ANIMATION_ORDINAL, TransportNpcAnimation.IDLE.ordinal());
            }
        }
    }

    private void updateDisplayName() {
        FactionId fid = getFactionId();
        String key = fid == FactionId.CHERNOGRYAD
                ? "wp.npc.transport.chernogryad"
                : "wp.npc.transport.zarnavia";
        setCustomName(Component.translatable(key).withStyle(fid.color()));
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

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // No-op: NPC should not move.
    }
}
