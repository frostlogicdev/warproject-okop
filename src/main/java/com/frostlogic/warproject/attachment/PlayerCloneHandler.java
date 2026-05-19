package com.frostlogic.warproject.attachment;

import com.frostlogic.warproject.WarProject;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Restores all WarProject player attachments when a player respawns after death.
 * <p>
 * NeoForge clears attachment data on clone by default; this handler copies
 * all mod-specific attachments from the original player entity to the new one.
 * <p>
 * Requirements: 13.3 (passport preservation on death), 21.1 (attachment persistence).
 * Design: §4.3
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class PlayerCloneHandler {

    private PlayerCloneHandler() {
        // static event subscriber — no instantiation
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        var original = event.getOriginal();
        var clone = event.getEntity();

        // PLAYER_STATE
        original.getExistingData(WpAttachmentTypes.PLAYER_STATE)
                .ifPresent(state -> clone.setData(WpAttachmentTypes.PLAYER_STATE, state));

        // FACTION (Optional<FactionId>)
        original.getExistingData(WpAttachmentTypes.FACTION)
                .ifPresent(faction -> clone.setData(WpAttachmentTypes.FACTION, faction));

        // ROLE
        original.getExistingData(WpAttachmentTypes.ROLE)
                .ifPresent(role -> clone.setData(WpAttachmentTypes.ROLE, role));

        // RANK
        original.getExistingData(WpAttachmentTypes.RANK)
                .ifPresent(rank -> clone.setData(WpAttachmentTypes.RANK, rank));

        // REGION (Optional<RegionRef>)
        original.getExistingData(WpAttachmentTypes.REGION)
                .ifPresent(region -> clone.setData(WpAttachmentTypes.REGION, region));

        // COLLABORATOR
        original.getExistingData(WpAttachmentTypes.COLLABORATOR)
                .ifPresent(collab -> clone.setData(WpAttachmentTypes.COLLABORATOR, collab));

        // CAPTURED
        original.getExistingData(WpAttachmentTypes.CAPTURED)
                .ifPresent(captured -> clone.setData(WpAttachmentTypes.CAPTURED, captured));

        // ENEMY_TICKS
        original.getExistingData(WpAttachmentTypes.ENEMY_TICKS)
                .ifPresent(ticks -> clone.setData(WpAttachmentTypes.ENEMY_TICKS, ticks));

        // RP_NAME (Optional<RpName>)
        original.getExistingData(WpAttachmentTypes.RP_NAME)
                .ifPresent(rpName -> clone.setData(WpAttachmentTypes.RP_NAME, rpName));

        // RP_NAME_LOCKED
        original.getExistingData(WpAttachmentTypes.RP_NAME_LOCKED)
                .ifPresent(locked -> clone.setData(WpAttachmentTypes.RP_NAME_LOCKED, locked));
    }
}
