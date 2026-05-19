package com.frostlogic.warproject.server.combat;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-tag system: when an enemy-faction player lands a hit, both the
 * attacker and victim are tagged for {@link #COMBAT_TAG_DURATION_MS} ms.
 * <p>
 * While tagged a player:
 * <ul>
 *   <li>Cannot use teleport-style commands ({@code /home}, {@code /spawn},
 *       {@code /tp}, {@code /tpa}, {@code /wp tp}, etc.). The list of
 *       blocked literals lives in {@link #BLOCKED_COMMAND_LITERALS}.</li>
 *   <li>If they disconnect, their entity is killed on the server within
 *       a tick — no Alt+F4 escape from a fight.</li>
 * </ul>
 * <p>
 * Same-faction PvP (friendly fire) does NOT engage combat-tag — only
 * inter-faction hits do.
 * <p>
 * The tag is mutual: hitting someone tags you back, so a runner can't
 * escape after one swing. Tag is refreshed on every subsequent hit, not
 * accumulated.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CombatTagService {

    /** How long the tag lasts after the last hit. */
    public static final long COMBAT_TAG_DURATION_MS = 15_000L;

    /**
     * Top-level command literals that are blocked while combat-tagged.
     * Compared against the FIRST token of the parsed command (lower-cased,
     * leading "/" stripped). Mod prefixes (e.g. {@code /wp}) match by their
     * root literal; for {@code /wp tp} we additionally inspect the second
     * token in {@link #isBlockedCommand}.
     */
    private static final Set<String> BLOCKED_COMMAND_LITERALS = Set.of(
            "tp", "tpa", "tpaccept", "tphere", "teleport",
            "home", "homes", "sethome",
            "spawn", "back", "warp", "warps", "rtp"
    );

    /**
     * Second-token literals that are blocked when the first token is
     * {@code wp}. Anything not in this set under {@code /wp} is allowed
     * (including chat, captcha, accept etc.).
     */
    private static final Set<String> BLOCKED_WP_SUBCOMMANDS = Set.of(
            "tp", "tphere"
    );

    /** uuid → tagExpiresAt (epoch ms). */
    private static final Map<UUID, Long> TAG_EXPIRES_AT = new ConcurrentHashMap<>();

    private CombatTagService() {
    }

    /**
     * Whether {@code uuid} is currently combat-tagged. Stale entries are
     * cleared lazily on access.
     */
    public static boolean isTagged(UUID uuid) {
        Long until = TAG_EXPIRES_AT.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }
        TAG_EXPIRES_AT.remove(uuid);
        return false;
    }

    /** Remaining ms on the tag, or 0 if not tagged. */
    public static long remainingMs(UUID uuid) {
        Long until = TAG_EXPIRES_AT.get(uuid);
        if (until == null) return 0L;
        long left = until - System.currentTimeMillis();
        return Math.max(0L, left);
    }

    /** Tags both players (mutual lock-in) and notifies them once on first tag. */
    public static void tag(ServerPlayer attacker, ServerPlayer victim) {
        long until = System.currentTimeMillis() + COMBAT_TAG_DURATION_MS;
        announceIfNew(attacker, until);
        announceIfNew(victim, until);
    }

    private static void announceIfNew(ServerPlayer player, long until) {
        Long prev = TAG_EXPIRES_AT.put(player.getUUID(), until);
        boolean wasTagged = prev != null && prev > System.currentTimeMillis();
        if (!wasTagged) {
            player.sendSystemMessage(Component.literal("⚔ Бой! Телепорты заблокированы.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Engaging the tag — only inter-faction PvP hits trigger it
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // We watch incoming damage on a player target where the source is another
        // player. LOW priority so any HIGHEST-priority cancellers (freeze, vanish,
        // candidate-rules) get to veto first; if the damage was cancelled we
        // skip the tag.
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        Object source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer attacker)) return;
        if (attacker.getUUID().equals(victim.getUUID())) return;

        Faction af = WarPlayerDataStore.get().getOrCreate(attacker).getFaction();
        Faction vf = WarPlayerDataStore.get().getOrCreate(victim).getFaction();
        if (!af.isPlayable() || !vf.isPlayable()) return;
        if (af == vf) return; // friendly fire doesn't engage combat-tag

        tag(attacker, victim);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Command gating while tagged
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        ServerPlayer player;
        try {
            player = event.getParseResults().getContext().getSource().getPlayer();
        } catch (Throwable t) {
            return;
        }
        if (player == null) return;
        if (player.hasPermissions(2)) return;
        if (!isTagged(player.getUUID())) return;

        String input = event.getParseResults().getReader().getString();
        if (!isBlockedCommand(input)) return;

        event.setCanceled(true);
        long left = (remainingMs(player.getUUID()) + 999L) / 1000L;
        player.sendSystemMessage(Component.literal(
                "[WP] Эта команда заблокирована в бою. Осталось: " + left + " сек.")
                .withStyle(ChatFormatting.RED));
    }

    /** Public for tests. */
    public static boolean isBlockedCommand(String rawInput) {
        if (rawInput == null) return false;
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.charAt(0) == '/') {
            trimmed = trimmed.substring(1);
        }
        // Split off the first two tokens.
        String[] parts = trimmed.split("\\s+", 3);
        String head = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (BLOCKED_COMMAND_LITERALS.contains(head)) return true;
        if (head.equals("wp") && parts.length >= 2) {
            String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
            return BLOCKED_WP_SUBCOMMANDS.contains(sub);
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout punishment
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * If a tagged player disconnects, kill their server entity so loot drops
     * and the kill registers normally. We only act for tagged & faction
     * players to avoid griefing fresh logins.
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;
        if (!isTagged(player.getUUID())) return;
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.getFaction().isPlayable()) return;
        // Kill via the void damage source so the death is logged like normal PvP.
        try {
            player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        } catch (Throwable t) {
            WarProject.LOGGER.warn("[WP CombatTag] Failed to kill tagged logout {}", player.getGameProfile().getName(), t);
        }
        TAG_EXPIRES_AT.remove(player.getUUID());
    }

    /** Drop the tag on death so respawn isn't immediately re-tagged. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TAG_EXPIRES_AT.remove(player.getUUID());
        }
    }

    /**
     * Periodic stale-tag cleanup so the map can't grow unbounded for ghosts.
     * Cheap — runs once per server-second.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TAG_EXPIRES_AT.isEmpty()) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();
        // Snapshot to avoid concurrent-modification noise.
        Set<UUID> stale = new HashSet<>();
        for (Map.Entry<UUID, Long> e : TAG_EXPIRES_AT.entrySet()) {
            if (e.getValue() <= now) stale.add(e.getKey());
        }
        for (Iterator<UUID> it = stale.iterator(); it.hasNext(); ) {
            UUID uuid = it.next();
            TAG_EXPIRES_AT.remove(uuid);
        }
    }
}
