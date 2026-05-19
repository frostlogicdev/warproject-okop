package com.frostlogic.warproject.server.militaryid;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.ServiceRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Optional;

/**
 * Event listener that wires {@link MilitaryIdService} to faction membership lifecycle events.
 * <p>
 * Handles:
 * <ul>
 *   <li><b>Player login</b> — triggers {@link MilitaryIdService#syncOnLogin(ServerPlayer)} to
 *       synchronize the Military ID card data with any changes that occurred while offline.</li>
 * </ul>
 * <p>
 * Other integration points (faction acceptance, rank changes, subdivision changes) are wired
 * directly at their respective call sites in {@code FactionChoiceHandler}, {@code RankService},
 * and {@code ServerPayloadHandler} for tighter coupling with the transactional flows.
 * <p>
 * Requirements: 5.7
 * Design: §2 Military ID System — MilitaryIdService
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class MilitaryIdEventListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MilitaryIdEventListener() {
        // static event subscriber — no instantiation
    }

    /**
     * Synchronizes the Military ID card on player login.
     * <p>
     * Runs at {@link EventPriority#NORMAL} priority — after the auth flow checks
     * (HIGHEST and HIGH priority) have completed, but before any lower-priority handlers.
     * Only triggers for players who are already accepted faction members (CANDIDATE or higher
     * with a faction assigned), since only they would have a Military ID card.
     * <p>
     * Requirements: 5.7
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Only sync for players who have a faction (they might have a Military ID card)
        Optional<FactionId> factionOpt = player.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return;
        }

        // Only sync for players who are at least CANDIDATE state
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state == PlayerState.NEW || state == PlayerState.LOGIN_PENDING || state == PlayerState.FACTIONLESS) {
            return;
        }

        MilitaryIdService service = ServiceRegistry.militaryId();
        if (service == null) {
            LOGGER.warn("[WP MilitaryIdEventListener] MilitaryIdService not available during login sync for {}",
                    player.getGameProfile().getName());
            return;
        }

        try {
            service.syncOnLogin(player);
            LOGGER.debug("[WP MilitaryIdEventListener] Synced military ID for {} on login",
                    player.getGameProfile().getName());
        } catch (Exception e) {
            LOGGER.error("[WP MilitaryIdEventListener] Failed to sync military ID for {} on login: {}",
                    player.getGameProfile().getName(), e.getMessage(), e);
        }
    }
}
