package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.RpName;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;

/**
 * Bridges legacy {@link WarPlayerProfile} state (JSON-backed) onto the modern
 * {@link WpAttachmentTypes} attachments used by the new command tree
 * ({@code SubdivisionCommands}, {@code FactionCommands}, etc.).
 *
 * <p>The legacy onboarding flow only writes to the JSON profile, so the new
 * commands incorrectly see every player as a CANDIDATE with no faction. This
 * bridge re-syncs every player on login and on a periodic tick, plus any
 * direct mutation site (invite / setrank / npc / etc.) calls
 * {@link #sync(ServerPlayer)} explicitly.
 *
 * <p>Sync direction: legacy {@code WarPlayerProfile} → attachments. The
 * attachments are treated as a read-through cache; the JSON file remains the
 * source of truth for the legacy flow.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class LegacyAttachmentBridge {
    /** Re-sync cadence for the periodic tick (in server ticks). 40 ticks ≈ 2 s. */
    private static final int TICK_INTERVAL = 40;

    private static int tickCounter;

    /**
     * Per-session cache: UUID → "is this player owned by the new DB-backed pipeline?".
     * <p>
     * The legacy bridge previously hit the database every 2 seconds for every
     * online player to answer this, which scales as O(N players × ticks). We cache
     * the answer on join (when {@link com.frostlogic.warproject.persistence.dao.AccountsDao}
     * is queried once) and invalidate on logout. Pipeline ownership cannot
     * change mid-session — registration / login is what creates the DB row, and
     * those are guarded by the player state machine.
     */
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean> NEW_PIPELINE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private LegacyAttachmentBridge() {
    }

    /** Synchronizes the player's attachments from their legacy profile. */
    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // If the new DB-backed pipeline owns this player, do NOT overwrite
        // attachments from the legacy JSON profile. The new pipeline
        // (ServerEvents.onPlayerLoginAuthFlow → WGuardService → CaptchaService →
        // PlayerLifecycleService) is the authoritative source for these players.
        if (isNewPipelinePlayer(player)) {
            return;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        applyFaction(player, profile);
        applyRole(player, profile);
        applyRank(player, profile);
        applyRpName(player, profile);
        applyPlayerState(player, profile);
    }

    private static void applyFaction(ServerPlayer player, WarPlayerProfile profile) {
        Optional<FactionId> next = mapFaction(profile.getFaction());
        Optional<FactionId> current = player.getData(WpAttachmentTypes.FACTION.get());
        if (!current.equals(next)) {
            player.setData(WpAttachmentTypes.FACTION.get(), next);
        }
    }

    private static void applyRole(ServerPlayer player, WarPlayerProfile profile) {
        Role next = mapRole(player, profile);
        Role current = player.getData(WpAttachmentTypes.ROLE.get());
        if (current != next) {
            player.setData(WpAttachmentTypes.ROLE.get(), next);
        }
    }

    private static void applyRank(ServerPlayer player, WarPlayerProfile profile) {
        String next = profile.getRank() == null ? "" : profile.getRank().id();
        String current = player.getData(WpAttachmentTypes.RANK.get());
        if (!java.util.Objects.equals(current, next)) {
            player.setData(WpAttachmentTypes.RANK.get(), next);
        }
    }

    private static void applyRpName(ServerPlayer player, WarPlayerProfile profile) {
        Optional<RpName> next = mapRpName(profile);
        Optional<RpName> current = player.getData(WpAttachmentTypes.RP_NAME.get());
        if (!current.equals(next)) {
            player.setData(WpAttachmentTypes.RP_NAME.get(), next);
        }
    }

    private static void applyPlayerState(ServerPlayer player, WarPlayerProfile profile) {
        PlayerState next = mapPlayerState(profile);
        PlayerState current = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (current != next) {
            player.setData(WpAttachmentTypes.PLAYER_STATE.get(), next);
        }
    }

    private static Optional<FactionId> mapFaction(Faction faction) {
        if (faction == null || !faction.isPlayable()) {
            return Optional.empty();
        }
        return switch (faction) {
            case ZARNAVIA -> Optional.of(FactionId.ZARNAVIA);
            case CHERNOGRYAD -> Optional.of(FactionId.CHERNOGRYAD);
            default -> Optional.empty();
        };
    }

    private static Role mapRole(ServerPlayer player, WarPlayerProfile profile) {
        if (player.hasPermissions(2)) {
            return Role.OP;
        }
        Rank rank = profile.getRank();
        if (rank == Rank.GENERAL) {
            return Role.GENERAL;
        }
        if (rank != null && rank.isCommander()) {
            return Role.COMMANDER;
        }
        if (rank == Rank.PRIVATE || rank == Rank.CORPORAL) {
            return Role.SOLDIER;
        }
        return Role.CANDIDATE;
    }

    private static Optional<RpName> mapRpName(WarPlayerProfile profile) {
        if (!profile.hasRpName()) {
            return Optional.empty();
        }
        String full = profile.getRpName().trim();
        int sep = full.indexOf(' ');
        if (sep < 0) {
            return Optional.of(new RpName(full, ""));
        }
        String first = full.substring(0, sep).trim();
        String last = full.substring(sep + 1).trim();
        return Optional.of(new RpName(first, last));
    }

    private static PlayerState mapPlayerState(WarPlayerProfile profile) {
        if (profile.isCaptive()) {
            return PlayerState.CAPTURED;
        }
        if (profile.isFactionMember()) {
            return PlayerState.ACCEPTED;
        }
        if (profile.getCandidateFaction().isPlayable()) {
            return PlayerState.CANDIDATE;
        }
        if (profile.isLoggedIn() && profile.isCaptchaPassed() && profile.hasRpName()) {
            return PlayerState.FACTIONLESS;
        }
        if (profile.isLoggedIn() && profile.isCaptchaPassed() && !profile.hasRpName()) {
            return PlayerState.RPNAME_REQUIRED;
        }
        if (profile.isLoggedIn() && !profile.isCaptchaPassed()) {
            return PlayerState.CAPTCHA;
        }
        if (profile.isRegistered()) {
            return PlayerState.LOGIN_PENDING;
        }
        return PlayerState.NEW;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        var server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    /**
     * Returns {@code true} if the player is managed by the new DB-backed auth
     * pipeline (WGuardService / CaptchaService / PlayerLifecycleService). In
     * that case the legacy bridge must NOT overwrite their attachments.
     * <p>
     * Detection: the new pipeline's {@code ServerEvents.onPlayerLoginAuthFlow}
     * only fires when the database is available. If the DB is up and the player
     * has an account row in it, they belong to the new pipeline.
     * <p>
     * The result is cached per session ({@link #NEW_PIPELINE_CACHE}) — pipeline
     * ownership does not change mid-session, so we avoid the per-tick DB hit.
     */
    private static boolean isNewPipelinePlayer(ServerPlayer player) {
        com.frostlogic.warproject.persistence.Database db = ServerEvents.getDatabase();
        if (db == null) {
            return false; // new pipeline not active at all
        }
        Boolean cached = NEW_PIPELINE_CACHE.get(player.getUUID());
        if (cached != null) {
            return cached;
        }
        try {
            boolean owned = db.inTx(conn ->
                    new com.frostlogic.warproject.persistence.dao.AccountsDao()
                            .findByUuid(conn, player.getStringUUID()).isPresent());
            NEW_PIPELINE_CACHE.put(player.getUUID(), owned);
            return owned;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Invalidates the cached pipeline-ownership flag for this player.
     * Called on logout, and also when a player transitions from legacy to new
     * pipeline mid-session (e.g. via registration in the new auth flow).
     */
    public static void invalidateCache(java.util.UUID uuid) {
        NEW_PIPELINE_CACHE.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            invalidateCache(player.getUUID());
        }
    }
}
