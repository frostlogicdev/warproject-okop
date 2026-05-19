package com.frostlogic.warproject.server.wguard;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service for the WGuard anti-cheat system.
 * <p>
 * Tracks per-player violation levels (VL) across multiple check categories,
 * decays VL over time, and takes actions (warn → kick → ban) when thresholds
 * are exceeded.
 * <p>
 * Check categories:
 * <ul>
 *   <li><b>SPEED</b> — horizontal movement exceeding configured blocks/tick</li>
 *   <li><b>FLY</b> — sustained airborne movement without flight permission</li>
 *   <li><b>REACH</b> — attack/interact distance exceeding configured maximum</li>
 *   <li><b>KILL_AURA</b> — attack frequency + rotation speed anomalies</li>
 *   <li><b>FAST_BREAK</b> — block-break frequency exceeding human limits</li>
 *   <li><b>NUKER</b> — breaking multiple different blocks in rapid succession</li>
 *   <li><b>NO_FALL</b> — taking no fall damage when expected</li>
 * </ul>
 * <p>
 * Each check adds a configurable VL weight. The total VL is compared against
 * {@link WpConfig#WGUARD_VIOLATION_KICK_THRESHOLD} and
 * {@link WpConfig#WGUARD_VIOLATION_BAN_THRESHOLD}. VL decays by 1 every
 * {@link WpConfig#WGUARD_VIOLATION_DECAY_TICKS} ticks.
 */
public final class WGuardService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Check type identifiers — used as keys in VL tracking and audit log. */
    public enum CheckType {
        SPEED("SPEED", 10),
        FLY("FLY", 5),
        REACH("REACH", 15),
        KILL_AURA("KILL_AURA", 25),
        FAST_BREAK("FAST_BREAK", 10),
        NUKER("NUKER", 20),
        NO_FALL("NO_FALL", 10);

        private final String id;
        private final int vlWeight;

        CheckType(String id, int vlWeight) {
            this.id = id;
            this.vlWeight = vlWeight;
        }

        public String id() { return id; }
        public int vlWeight() { return vlWeight; }
    }

    /** Per-player violation tracking data. */
    public static final class PlayerViolations {
        private int totalVl;
        private final Map<String, Integer> perCheckVl = new ConcurrentHashMap<>();
        private long lastDecayTick;

        public int totalVl() { return totalVl; }

        public void addVl(CheckType check, int amount) {
            totalVl += amount;
            perCheckVl.merge(check.id(), amount, Integer::sum);
        }

        public void decay(int amount) {
            totalVl = Math.max(0, totalVl - amount);
            perCheckVl.entrySet().removeIf(e -> {
                e.setValue(Math.max(0, e.getValue() - amount));
                return e.getValue() == 0;
            });
        }

        public int checkVl(CheckType check) {
            return perCheckVl.getOrDefault(check.id(), 0);
        }

        public void setLastDecayTick(long tick) { this.lastDecayTick = tick; }
        public long lastDecayTick() { return lastDecayTick; }
    }

    private final Database database;
    private final AuditLogDao auditLogDao;
    private final Map<UUID, PlayerViolations> violations = new ConcurrentHashMap<>();

    public WGuardService(Database database, AuditLogDao auditLogDao) {
        this.database = database;
        this.auditLogDao = auditLogDao;
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns (or creates) the violation tracker for the given player.
     */
    public PlayerViolations getViolations(ServerPlayer player) {
        return violations.computeIfAbsent(player.getUUID(), k -> new PlayerViolations());
    }

    /**
     * Records a violation for the given check type, updates VL, and
     * takes action if thresholds are exceeded.
     *
     * @param player the offending player
     * @param check  the check that was violated
     * @param detail human-readable detail for the audit log
     */
    public void flag(ServerPlayer player, CheckType check, String detail) {
        if (!WpConfig.WGUARD_ENABLED.get()) return;

        // OPs are exempt from anti-cheat
        if (player.hasPermissions(2)) return;

        PlayerViolations pv = getViolations(player);
        pv.addVl(check, check.vlWeight());

        String playerName = player.getGameProfile().getName();
        String uuid = player.getStringUUID();
        int vl = pv.totalVl();

        LOGGER.info("[WGuard] {} failed {} — {} (total VL: {})", playerName, check.id(), detail, vl);

        // Audit log
        writeAudit(uuid, playerName, check, detail, vl);

        // Notify OPs online
        notifyStaff(player, check, vl);

        // Check thresholds
        int banThreshold = WpConfig.WGUARD_VIOLATION_BAN_THRESHOLD.get();
        int kickThreshold = WpConfig.WGUARD_VIOLATION_KICK_THRESHOLD.get();

        if (vl >= banThreshold) {
            ban(player, check);
        } else if (vl >= kickThreshold) {
            kick(player, check);
        } else {
            // Warning message to the player
            player.sendSystemMessage(Component.translatable("wp.wguard." + check.id().toLowerCase())
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    /**
     * Decays violation levels for all tracked players. Called periodically
     * from the tick handler.
     */
    public void decayAll(long currentTick) {
        int decayInterval = WpConfig.WGUARD_VIOLATION_DECAY_TICKS.get();
        for (Map.Entry<UUID, PlayerViolations> entry : violations.entrySet()) {
            PlayerViolations pv = entry.getValue();
            if (currentTick - pv.lastDecayTick() >= decayInterval) {
                pv.decay(1);
                pv.setLastDecayTick(currentTick);
                if (pv.totalVl() <= 0) {
                    violations.remove(entry.getKey());
                }
            }
        }
    }

    /**
     * Removes violation tracking for a disconnected player.
     */
    public void removePlayer(UUID uuid) {
        violations.remove(uuid);
    }

    /**
     * Returns the total VL for a player, or 0 if not tracked.
     */
    public int getTotalVl(UUID uuid) {
        PlayerViolations pv = violations.get(uuid);
        return pv != null ? pv.totalVl() : 0;
    }

    // ─── Actions ───────────────────────────────────────────────────────────────

    private void kick(ServerPlayer player, CheckType check) {
        String playerName = player.getGameProfile().getName();
        LOGGER.warn("[WGuard] Kicking {} for repeated {} violations (VL: {})",
                playerName, check.id(), getViolations(player).totalVl());
        player.connection.disconnect(Component.translatable("wp.wguard.kick"));
    }

    private void ban(ServerPlayer player, CheckType check) {
        String playerName = player.getGameProfile().getName();
        String uuid = player.getStringUUID();
        LOGGER.warn("[WGuard] Banning {} for persistent {} violations (VL: {})",
                playerName, check.id(), getViolations(player).totalVl());

        // Write ban to database
        long now = System.currentTimeMillis();
        try {
            database.transaction(conn -> {
                auditLogDao.insert(conn, now, null, "WGuard",
                        uuid, playerName, "WGUARD_BAN", check.id(),
                        "{\"total_vl\":" + getViolations(player).totalVl() + "}");
                // Insert ban row — uses the existing bans table
                new com.frostlogic.warproject.persistence.dao.BansDao().upsert(conn,
                        new com.frostlogic.warproject.persistence.dao.BansDao.Ban(
                                uuid, "WGuard: " + check.id(), "WGuard", now, 0));
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WGuard] Failed to write ban for {}: {}", playerName, e.getMessage());
        }

        player.connection.disconnect(Component.translatable("wp.wguard.ban"));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void writeAudit(String uuid, String name, CheckType check, String detail, int vl) {
        long now = System.currentTimeMillis();
        try {
            database.transaction(conn ->
                    auditLogDao.insert(conn, now, uuid, name,
                            null, null, "WGUARD_FLAG", check.id(),
                            "{\"detail\":\"" + detail.replace("\"", "'") + "\",\"vl\":" + vl + "}"));
        } catch (RuntimeException e) {
            LOGGER.error("[WGuard] Failed to write audit for {}: {}", name, e.getMessage());
        }
    }

    private void notifyStaff(ServerPlayer offender, CheckType check, int vl) {
        String offenderName = offender.getGameProfile().getName();
        Component notification = Component.translatable("wp.wguard.notify",
                offenderName, check.id(), vl);
        for (ServerPlayer p : offender.server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2) && p != offender) {
                p.sendSystemMessage(notification.copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        }
    }
}
