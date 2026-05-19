package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.RpName;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.s2c.PlayerPublicViewPayload;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages the player lifecycle finite state machine (design §5.1).
 * <p>
 * Provides atomic state transitions that update the database ({@code players.status}),
 * the in-memory attachment ({@link WpAttachmentTypes#PLAYER_STATE}), and broadcast
 * a {@link PlayerPublicViewPayload} to the affected player.
 * <p>
 * Valid transitions (from §5.1):
 * <ul>
 *   <li>NEW → REGISTERED_PENDING (registration OK)</li>
 *   <li>REGISTERED_PENDING → CAPTCHA (teleport to captcha spawn)</li>
 *   <li>CAPTCHA → RPNAME_REQUIRED (/wp captcha OK)</li>
 *   <li>LOGIN_PENDING → any active state (login OK, resolves from DB)</li>
 *   <li>RPNAME_REQUIRED → FACTIONLESS (/wp rpname OK → tp to choice hall)</li>
 *   <li>FACTIONLESS → CANDIDATE (NPC choice + confirm)</li>
 *   <li>CANDIDATE → ACCEPTED (/wp accept or radial menu)</li>
 *   <li>ACCEPTED → CAPTURED (passport capture)</li>
 *   <li>CAPTURED → ACCEPTED (ransom / admin return)</li>
 * </ul>
 * <p>
 * Requirements: 5.9, 5.10, 7.x, 8.6
 * Design: §5.1
 */
public final class PlayerLifecycleService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Map of valid transitions: from-state → set of allowed to-states.
     */
    private static final Map<PlayerState, Set<PlayerState>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(PlayerState.class);
        VALID_TRANSITIONS.put(PlayerState.NEW, EnumSet.of(PlayerState.REGISTERED_PENDING));
        VALID_TRANSITIONS.put(PlayerState.REGISTERED_PENDING, EnumSet.of(PlayerState.CAPTCHA));
        VALID_TRANSITIONS.put(PlayerState.CAPTCHA, EnumSet.of(PlayerState.RPNAME_REQUIRED));
        // LOGIN_PENDING can resolve to any "active" state (the state stored in DB from previous session)
        VALID_TRANSITIONS.put(PlayerState.LOGIN_PENDING, EnumSet.of(
                PlayerState.RPNAME_REQUIRED,
                PlayerState.FACTIONLESS,
                PlayerState.CANDIDATE,
                PlayerState.ACCEPTED,
                PlayerState.CAPTURED
        ));
        VALID_TRANSITIONS.put(PlayerState.RPNAME_REQUIRED, EnumSet.of(PlayerState.FACTIONLESS));
        VALID_TRANSITIONS.put(PlayerState.FACTIONLESS, EnumSet.of(PlayerState.CANDIDATE));
        VALID_TRANSITIONS.put(PlayerState.CANDIDATE, EnumSet.of(PlayerState.ACCEPTED));
        VALID_TRANSITIONS.put(PlayerState.ACCEPTED, EnumSet.of(PlayerState.CAPTURED));
        VALID_TRANSITIONS.put(PlayerState.CAPTURED, EnumSet.of(PlayerState.ACCEPTED));
    }

    private final Database database;
    private final PlayersDao playersDao;

    public PlayerLifecycleService(Database database, PlayersDao playersDao) {
        this.database = database;
        this.playersDao = playersDao;
    }

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns the current lifecycle state of the player from the attachment.
     *
     * @param player the server player
     * @return the current {@link PlayerState}
     */
    public PlayerState currentState(ServerPlayer player) {
        return player.getData(WpAttachmentTypes.PLAYER_STATE.get());
    }

    /**
     * Checks whether a transition from {@code from} to {@code to} is valid
     * according to the state diagram in design §5.1.
     *
     * @param from the source state
     * @param to   the target state
     * @return true if the transition is allowed
     */
    public boolean canTransition(PlayerState from, PlayerState to) {
        Set<PlayerState> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Advances the player to a new lifecycle state.
     * <p>
     * This method atomically:
     * <ol>
     *   <li>Validates the transition against the state diagram</li>
     *   <li>Updates {@code players.status} in the database</li>
     *   <li>Updates the {@link WpAttachmentTypes#PLAYER_STATE} attachment</li>
     *   <li>Sends a {@link PlayerPublicViewPayload} to the player</li>
     * </ol>
     *
     * @param player   the server player to advance
     * @param newState the target state
     * @throws IllegalStateException if the transition is not valid
     */
    public void advance(ServerPlayer player, PlayerState newState) {
        PlayerState current = currentState(player);
        if (!canTransition(current, newState)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + current + " → " + newState
                            + " for player " + player.getGameProfile().getName()
            );
        }

        String uuid = player.getStringUUID();

        // Atomically update the database
        database.transaction(conn -> {
            playersDao.updateStatus(conn, uuid, newState.getSerializedName());
        });

        // Update the in-memory attachment
        player.setData(WpAttachmentTypes.PLAYER_STATE.get(), newState);

        // Send public view payload to the player
        sendPublicView(player);

        LOGGER.debug("[WarProject] Player {} transitioned: {} → {}",
                player.getGameProfile().getName(), current, newState);
    }

    /**
     * Sets the RP name for the player, locks it, and advances to {@link PlayerState#FACTIONLESS}.
     * <p>
     * This method:
     * <ol>
     *   <li>Validates the player is in {@link PlayerState#RPNAME_REQUIRED}</li>
     *   <li>Updates the RP name in the database and attachment</li>
     *   <li>Locks the RP name (prevents further changes without admin override)</li>
     *   <li>Advances the state to FACTIONLESS</li>
     * </ol>
     *
     * @param player  the server player
     * @param name    the RP first name (Ник)
     * @param surname the RP surname (Фамилия)
     * @throws IllegalStateException if the player is not in RPNAME_REQUIRED state
     */
    public void setRpName(ServerPlayer player, String name, String surname) {
        PlayerState current = currentState(player);
        if (current != PlayerState.RPNAME_REQUIRED) {
            throw new IllegalStateException(
                    "setRpName requires state RPNAME_REQUIRED, but player "
                            + player.getGameProfile().getName() + " is in " + current
            );
        }

        String uuid = player.getStringUUID();

        // Atomically update DB: set rp_name, rp_surname, and advance status to FACTIONLESS
        database.transaction(conn -> {
            playersDao.setRpName(conn, uuid, name, surname);
            playersDao.updateStatus(conn, uuid, PlayerState.FACTIONLESS.getSerializedName());
        });

        // Update attachments
        player.setData(WpAttachmentTypes.RP_NAME.get(), Optional.of(new RpName(name, surname)));
        player.setData(WpAttachmentTypes.RP_NAME_LOCKED.get(), true);
        player.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.FACTIONLESS);

        // Send public view payload
        sendPublicView(player);

        LOGGER.debug("[WarProject] Player {} set RP name: {} {}",
                player.getGameProfile().getName(), name, surname);
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────────

    /**
     * Sends a {@link PlayerPublicViewPayload} to the player with their current public state.
     */
    private void sendPublicView(ServerPlayer player) {
        Optional<FactionId> factionOpt = player.getData(WpAttachmentTypes.FACTION.get());
        FactionId faction = factionOpt.orElse(FactionId.ZARNAVIA); // default for payload; client handles null-faction display
        Role role = player.getData(WpAttachmentTypes.ROLE.get());
        PlayerState status = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        boolean collaborator = player.getData(WpAttachmentTypes.COLLABORATOR.get());
        Optional<RpName> rpNameOpt = player.getData(WpAttachmentTypes.RP_NAME.get());
        String rpFullName = rpNameOpt.map(RpName::fullName).orElse(null);

        PlayerPublicViewPayload payload = new PlayerPublicViewPayload(
                player.getUUID(),
                faction,
                role,
                status,
                collaborator,
                rpFullName
        );

        player.connection.send(payload);
    }
}
