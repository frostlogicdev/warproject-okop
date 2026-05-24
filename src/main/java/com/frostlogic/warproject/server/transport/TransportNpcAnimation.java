package com.frostlogic.warproject.server.transport;

/**
 * Animation states for {@link TransportNpcEntity}.
 * <p>
 * The active state is synchronised to the client via
 * {@link net.minecraft.network.syncher.SynchedEntityData}; the renderer reads
 * the ordinal to drive vanilla head/arm rotations. A short duration is
 * embedded so the server can flip back to {@link #IDLE} after the animation
 * finishes without an extra packet.
 * <p>
 * Vanilla mechanics are used instead of GeckoLib so the mod has no extra
 * runtime dependency: clients do not need to install GeckoLib to see the NPC
 * react to interactions.
 */
public enum TransportNpcAnimation {

    /** Default standing pose. */
    IDLE(0),

    /** "No" — head shakes left/right via {@code yHeadRot} oscillation. */
    HEAD_SHAKE(30),

    /** "Yes / take it" — arm waves via vanilla {@code swing(MAIN_HAND)}. */
    ARM_WAVE(20);

    /** How many server ticks the animation runs before reverting to IDLE. */
    private final int durationTicks;

    TransportNpcAnimation(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public static TransportNpcAnimation fromOrdinal(int ordinal) {
        TransportNpcAnimation[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return IDLE;
        }
        return values[ordinal];
    }
}
