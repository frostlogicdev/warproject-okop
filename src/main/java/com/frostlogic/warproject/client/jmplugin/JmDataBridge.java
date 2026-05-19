package com.frostlogic.warproject.client.jmplugin;

import com.frostlogic.warproject.network.payload.s2c.AllyPositionsPayload;
import com.frostlogic.warproject.network.payload.s2c.EnemyVisiblePayload;
import com.frostlogic.warproject.network.payload.s2c.FactionBasesPayload;
import com.frostlogic.warproject.network.payload.s2c.FactionMarkerActivePayload;
import com.frostlogic.warproject.network.payload.s2c.FactionMarkerClearPayload;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Static bridge that decouples the network handlers from the JourneyMap plugin.
 * <p>
 * The {@code ClientPayloadHandler} forwards ally/enemy/base snapshots through this
 * class without importing any {@code journeymap.api.v2.*} types. The plugin
 * (which IS allowed to import JM API classes — but is only classloaded by JM
 * when the JM mod is present) registers itself as a consumer here on
 * {@code initialize}. If JM is absent, no consumer is registered and the
 * handlers' calls are simple no-ops.
 * <p>
 * This is the seam that lets us keep JM as a pure {@code compileOnly} dependency:
 * {@link com.frostlogic.warproject.network.ClientPayloadHandler} only references
 * this class — never any JM API class — so the JM API is never resolved at
 * runtime if JM isn't installed.
 */
public final class JmDataBridge {

    private static final AtomicReference<Consumer<AllyPositionsPayload>> ALLY_SINK = new AtomicReference<>();
    private static final AtomicReference<Consumer<EnemyVisiblePayload>>  ENEMY_SINK = new AtomicReference<>();
    private static final AtomicReference<Consumer<FactionBasesPayload>>  BASES_SINK = new AtomicReference<>();
    private static final AtomicReference<Consumer<FactionMarkerActivePayload>> MARKER_ACTIVE_SINK = new AtomicReference<>();
    private static final AtomicReference<Consumer<FactionMarkerClearPayload>>  MARKER_CLEAR_SINK  = new AtomicReference<>();

    private JmDataBridge() {}

    /**
     * Registers (or replaces) the consumer that receives ally snapshots.
     * Called by the JM plugin during {@code initialize}.
     */
    public static void setAllySink(Consumer<AllyPositionsPayload> sink) {
        ALLY_SINK.set(sink);
    }

    public static void setEnemySink(Consumer<EnemyVisiblePayload> sink) {
        ENEMY_SINK.set(sink);
    }

    public static void setBasesSink(Consumer<FactionBasesPayload> sink) {
        BASES_SINK.set(sink);
    }

    public static void setMarkerActiveSink(Consumer<FactionMarkerActivePayload> sink) {
        MARKER_ACTIVE_SINK.set(sink);
    }

    public static void setMarkerClearSink(Consumer<FactionMarkerClearPayload> sink) {
        MARKER_CLEAR_SINK.set(sink);
    }

    /** Network handler entry point — safe no-op if no consumer is registered. */
    public static void publishAllies(AllyPositionsPayload payload) {
        Consumer<AllyPositionsPayload> sink = ALLY_SINK.get();
        if (sink != null) {
            sink.accept(payload);
        }
    }

    public static void publishEnemies(EnemyVisiblePayload payload) {
        Consumer<EnemyVisiblePayload> sink = ENEMY_SINK.get();
        if (sink != null) {
            sink.accept(payload);
        }
    }

    public static void publishBases(FactionBasesPayload payload) {
        Consumer<FactionBasesPayload> sink = BASES_SINK.get();
        if (sink != null) {
            sink.accept(payload);
        }
    }

    public static void publishMarkerActive(FactionMarkerActivePayload payload) {
        Consumer<FactionMarkerActivePayload> sink = MARKER_ACTIVE_SINK.get();
        if (sink != null) {
            sink.accept(payload);
        }
    }

    public static void publishMarkerClear(FactionMarkerClearPayload payload) {
        Consumer<FactionMarkerClearPayload> sink = MARKER_CLEAR_SINK.get();
        if (sink != null) {
            sink.accept(payload);
        }
    }
}
