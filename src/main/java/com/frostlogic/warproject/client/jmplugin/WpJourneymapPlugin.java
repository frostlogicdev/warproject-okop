package com.frostlogic.warproject.client.jmplugin;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.network.payload.s2c.AllyPositionsPayload;
import com.frostlogic.warproject.network.payload.s2c.EnemyVisiblePayload;
import com.frostlogic.warproject.network.payload.s2c.FactionBasesPayload;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * WarProject's JourneyMap integration plugin.
 * <p>
 * Discovered automatically by JourneyMap thanks to the {@link journeymap.api.v2.common.JourneyMapPlugin}
 * annotation. JourneyMap calls {@link #initialize(IClientAPI)} during its mod
 * init phase and never directly references this class from anywhere else in
 * WarProject — that way, when JourneyMap is absent, this class is never
 * classloaded and the {@code journeymap.api.v2.*} types are never resolved at
 * runtime. (See {@code JmDataBridge} for the static seam used by network
 * handlers.)
 * <p>
 * What this plugin draws:
 * <ul>
 *     <li><b>Faction base polygons</b> — coloured rectangles around each base
 *         region, sent once on login via {@code FactionBasesPayload}.</li>
 *     <li><b>Ally markers</b> — green dots at every same-faction player's
 *         current position, refreshed at 1 Hz from the server.</li>
 *     <li><b>Visible-enemy markers</b> — red dots at enemy players currently
 *         standing inside the receiver's faction base region.</li>
 * </ul>
 * <p>
 * Important implementation note: in JM API v2, {@link MarkerOverlay} ids are
 * random UUIDs assigned by {@code Displayable}'s constructor — you cannot
 * "replace" an overlay by re-creating it with the same id. To update positions
 * cheaply we therefore keep one {@code MarkerOverlay} instance per player UUID
 * and mutate it via {@code setPoint(...)} + {@code flagForRerender()}.
 */
@journeymap.api.v2.common.JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class WpJourneymapPlugin implements IClientPlugin {

    private static final ResourceLocation MARKER_ICON =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/map/map_icons.png");

    /** Green hex used for Zarnavia (matches {@code FactionId.ZARNAVIA} chat colour). */
    private static final int COLOR_ZARNAVIA = 0x55FF55;
    /** Dark gray hex used for Chernogryad. */
    private static final int COLOR_CHERNOGRYAD = 0x555555;
    /** Red hex used for visible enemy markers. */
    private static final int COLOR_ENEMY = 0xFF3333;
    /** Green hex used for ally markers. */
    private static final int COLOR_ALLY = 0x55FF55;

    private IClientAPI api;

    /** Live overlays per ally / enemy UUID — re-used between ticks to avoid id churn. */
    private final Map<UUID, MarkerOverlay> allyOverlays  = new HashMap<>();
    private final Map<UUID, MarkerOverlay> enemyOverlays = new HashMap<>();
    /** Polygon overlays for faction bases — replaced wholesale on each base snapshot. */
    private final Set<PolygonOverlay> baseOverlays = new HashSet<>();
    /** Squad-style command markers, keyed by "<scope>:<scopeKey>". */
    private final Map<String, MarkerOverlay> commandMarkers = new HashMap<>();

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        // Hook the data sinks so the network handlers can drive the overlays.
        JmDataBridge.setAllySink(this::onAllies);
        JmDataBridge.setEnemySink(this::onEnemies);
        JmDataBridge.setBasesSink(this::onBases);
        JmDataBridge.setMarkerActiveSink(this::onMarkerActive);
        JmDataBridge.setMarkerClearSink(this::onMarkerClear);
        WarProject.LOGGER.info("[WP JM] WarProject JourneyMap plugin initialized");
    }

    @Override
    public String getModId() {
        return WarProject.MOD_ID;
    }

    // ---------- ally markers (green) ----------

    private void onAllies(AllyPositionsPayload payload) {
        if (api == null) return;
        Set<UUID> seen = new HashSet<>(payload.allies().size());

        for (AllyPositionsPayload.Ally a : payload.allies()) {
            seen.add(a.uuid());
            BlockPos pos = new BlockPos(a.x(), a.y(), a.z());
            MarkerOverlay marker = allyOverlays.get(a.uuid());
            if (marker == null) {
                marker = createMarker(pos, a.displayName(), COLOR_ALLY);
                marker.setDimension(toDimension(a.dimensionId()));
                try {
                    api.show(marker);
                    allyOverlays.put(a.uuid(), marker);
                } catch (Throwable t) {
                    WarProject.LOGGER.error("[WP JM] Failed to show ally marker for {}: {}",
                            a.displayName(), t.getMessage());
                }
            } else {
                marker.setPoint(pos);
                marker.setDimension(toDimension(a.dimensionId()));
                marker.setLabel(a.displayName());
                marker.setTitle(a.displayName());
                marker.flagForRerender();
            }
        }

        // Drop overlays for allies that disappeared from the snapshot.
        Iterator<Map.Entry<UUID, MarkerOverlay>> it = allyOverlays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MarkerOverlay> e = it.next();
            if (!seen.contains(e.getKey())) {
                try { api.remove(e.getValue()); } catch (Throwable ignored) {}
                it.remove();
            }
        }
    }

    // ---------- visible enemy markers (red) ----------

    private void onEnemies(EnemyVisiblePayload payload) {
        if (api == null) return;
        Set<UUID> seen = new HashSet<>(payload.enemies().size());

        for (EnemyVisiblePayload.Enemy e : payload.enemies()) {
            seen.add(e.uuid());
            BlockPos pos = new BlockPos(e.x(), e.y(), e.z());
            MarkerOverlay marker = enemyOverlays.get(e.uuid());
            if (marker == null) {
                marker = createMarker(pos, e.displayName(), COLOR_ENEMY);
                marker.setDimension(toDimension(e.dimensionId()));
                try {
                    api.show(marker);
                    enemyOverlays.put(e.uuid(), marker);
                } catch (Throwable t) {
                    WarProject.LOGGER.error("[WP JM] Failed to show enemy marker for {}: {}",
                            e.displayName(), t.getMessage());
                }
            } else {
                marker.setPoint(pos);
                marker.setDimension(toDimension(e.dimensionId()));
                marker.setLabel(e.displayName());
                marker.setTitle(e.displayName());
                marker.flagForRerender();
            }
        }

        Iterator<Map.Entry<UUID, MarkerOverlay>> it = enemyOverlays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MarkerOverlay> en = it.next();
            if (!seen.contains(en.getKey())) {
                try { api.remove(en.getValue()); } catch (Throwable ignored) {}
                it.remove();
            }
        }
    }

    // ---------- faction-base polygons (refreshed on payload arrival) ----------

    private void onBases(FactionBasesPayload payload) {
        if (api == null) return;

        // Tear down the previous set wholesale — bases are rare and sent once per login.
        for (PolygonOverlay overlay : baseOverlays) {
            try { api.remove(overlay); } catch (Throwable ignored) {}
        }
        baseOverlays.clear();

        for (FactionBasesPayload.Base b : payload.bases()) {
            try {
                int color = b.faction() == FactionId.ZARNAVIA ? COLOR_ZARNAVIA : COLOR_CHERNOGRYAD;

                ShapeProperties shape = new ShapeProperties()
                        .setStrokeWidth(2)
                        .setStrokeColor(color).setStrokeOpacity(0.85f)
                        .setFillColor(color).setFillOpacity(0.18f);

                TextProperties text = new TextProperties()
                        .setBackgroundOpacity(0.4f)
                        .setColor(0xFFFFFF)
                        .setOpacity(1f)
                        .setMinZoom(1)
                        .setFontShadow(true);

                int midY = (b.minY() + b.maxY()) / 2;
                MapPolygon polygon = new MapPolygon(
                        new BlockPos(b.minX(), midY, b.minZ()),
                        new BlockPos(b.maxX(), midY, b.minZ()),
                        new BlockPos(b.maxX(), midY, b.maxZ()),
                        new BlockPos(b.minX(), midY, b.maxZ())
                );

                PolygonOverlay overlay = new PolygonOverlay(
                        getModId(),
                        toDimension(b.dimensionId()),
                        shape,
                        polygon
                );
                overlay.setOverlayGroupName("WarProject Bases")
                        .setLabel(b.faction().getSerializedName())
                        .setTitle(b.faction().getSerializedName())
                        .setTextProperties(text);

                api.show(overlay);
                baseOverlays.add(overlay);
            } catch (Throwable t) {
                WarProject.LOGGER.error("[WP JM] Failed to show base polygon: {}", t.getMessage());
            }
        }
        WarProject.LOGGER.info("[WP JM] Drew {} faction-base polygon(s)", baseOverlays.size());
    }

    // ---------- helpers ----------

    private MarkerOverlay createMarker(BlockPos pos, String label, int color) {
        MapImage icon = new MapImage(MARKER_ICON, 0, 0, 16, 16, color, 1f);
        icon.centerAnchors();
        MarkerOverlay marker = new MarkerOverlay(getModId(), pos, icon);
        marker.setLabel(label).setTitle(label);
        return marker;
    }

    private static ResourceKey<Level> toDimension(String id) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(id));
    }

    // ---------- squad command markers (yellow / orange) ----------

    /** Yellow hex used for COMMANDER+ faction-wide markers. */
    private static final int COLOR_FACTION_MARKER = 0xFFD700;
    /** Orange hex used for SOLDIER subdivision markers. */
    private static final int COLOR_SUBDIVISION_MARKER = 0xFFA500;

    private void onMarkerActive(com.frostlogic.warproject.network.payload.s2c.FactionMarkerActivePayload payload) {
        if (api == null) return;
        String key = payload.scope().name() + ":" + payload.scopeKey();

        // Remove the previous marker for this exact scope key — JM API instances are
        // tied to a random id so a stale overlay would otherwise stay forever.
        MarkerOverlay prev = commandMarkers.remove(key);
        if (prev != null) {
            try { api.remove(prev); } catch (Throwable ignored) {}
        }

        int color = payload.scope() == com.frostlogic.warproject.network.payload.s2c.FactionMarkerActivePayload.Scope.FACTION
                ? COLOR_FACTION_MARKER
                : COLOR_SUBDIVISION_MARKER;
        String label = payload.scope() == com.frostlogic.warproject.network.payload.s2c.FactionMarkerActivePayload.Scope.FACTION
                ? "★ " + payload.ownerName()
                : "▲ " + payload.ownerName();

        MarkerOverlay marker = createMarker(
                new net.minecraft.core.BlockPos(payload.x(), payload.y(), payload.z()),
                label,
                color
        );
        marker.setDimension(toDimension(payload.dimensionId()));
        try {
            api.show(marker);
            commandMarkers.put(key, marker);
        } catch (Throwable t) {
            WarProject.LOGGER.error("[WP JM] Failed to show command marker {}: {}", key, t.getMessage());
        }
    }

    private void onMarkerClear(com.frostlogic.warproject.network.payload.s2c.FactionMarkerClearPayload payload) {
        if (api == null) return;
        String key = payload.scope().name() + ":" + payload.scopeKey();
        MarkerOverlay overlay = commandMarkers.remove(key);
        if (overlay != null) {
            try { api.remove(overlay); } catch (Throwable ignored) {}
        }
    }
}