package com.frostlogic.warproject.server.audit;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.server.ServerEvents;
import com.frostlogic.warproject.server.region.BaseRegion;
import com.frostlogic.warproject.server.region.RegionCacheHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Writes a {@code DEATH} row into {@code audit_log} every time a player
 * dies, including the killer (if any), damage type, region (if inside a
 * configured base), and coordinates.
 * <p>
 * The row's {@code extra_json} carries:
 * <pre>
 * {
 *   "x":..,"y":..,"z":..,
 *   "dim":"minecraft:overworld",
 *   "source":"player|mob|fall|drown|...",
 *   "region":"ZARNAVIA"|null
 * }
 * </pre>
 * Player identifying fields are stored in the standard columns:
 * <ul>
 *   <li>{@code target_*} — the dead player.</li>
 *   <li>{@code actor_*} — the killing player, or {@code null} for environmental deaths.</li>
 *   <li>{@code reason} — short damage-source description ({@code DamageSource.getMsgId()}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class DeathLogService {

    private DeathLogService() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        var db = ServerEvents.getDatabase();
        if (db == null) return; // not initialised yet (very early in startup) — skip.

        DamageSource src = event.getSource();
        String sourceMsgId = src == null ? "unknown" : safeMsgId(src);

        String actorUuid = null;
        String actorName = null;
        if (src != null && src.getEntity() instanceof ServerPlayer killer && !killer.getUUID().equals(victim.getUUID())) {
            actorUuid = killer.getStringUUID();
            actorName = killer.getGameProfile().getName();
        } else if (src != null && src.getEntity() instanceof LivingEntity mob) {
            // Mob kills go in actor_name (no UUID) so /wp history can still tell
            // a player from a creeper, but we don't pollute the actor_uuid index.
            actorName = mob.getType().getDescriptionId();
        }

        Vec3 pos = victim.position();
        BaseRegion region = RegionCacheHandler.regionService()
                .regionAt(victim.level().dimension(), pos);
        String regionTag = region == null ? null : region.faction().name();

        String extraJson = "{"
                + "\"x\":" + fmt(pos.x) + ","
                + "\"y\":" + fmt(pos.y) + ","
                + "\"z\":" + fmt(pos.z) + ","
                + "\"dim\":" + jsonString(victim.level().dimension().location().toString()) + ","
                + "\"source\":" + jsonString(sourceMsgId) + ","
                + "\"region\":" + (regionTag == null ? "null" : jsonString(regionTag))
                + "}";

        long now = System.currentTimeMillis();
        String victimUuid = victim.getStringUUID();
        String victimName = victim.getGameProfile().getName();
        AuditLogDao dao = new AuditLogDao();
        // Effective-final captures for the lambda below.
        final String fActorUuid = actorUuid;
        final String fActorName = actorName;
        final String fSourceMsgId = sourceMsgId;
        final String fExtraJson = extraJson;
        // No transaction wrapper — single insert is atomic in SQLite.
        try {
            db.transaction(conn -> dao.insert(
                    conn, now,
                    fActorUuid,
                    fActorName,
                    victimUuid, victimName,
                    "DEATH",
                    fSourceMsgId,
                    fExtraJson
            ));
        } catch (RuntimeException ex) {
            WarProject.LOGGER.error("[WP Death] Failed to log death of {}: {}", victimName, ex.getMessage(), ex);
        }
    }

    private static String safeMsgId(DamageSource src) {
        try {
            return src.getMsgId();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String fmt(double v) {
        // 1 decimal — enough for spatial reasoning, no scientific notation.
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\', '"' -> sb.append('\\').append(c);
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
