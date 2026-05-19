package com.frostlogic.warproject.server.captivity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Server-side menu for the ransom trade between a captor and a prisoner.
 * <p>
 * This menu implements a two-player trade with mutual confirmation:
 * <ul>
 *   <li>The captor offers items in their trade slots (top section)</li>
 *   <li>The prisoner offers items in their trade slots (bottom section)</li>
 *   <li>Both players must call {@link #confirm(Player)} to finalize</li>
 *   <li>On mutual confirmation, the server atomically releases the prisoner
 *       via {@link CaptivityService#ransom(String, ServerPlayer, ServerPlayer)}</li>
 * </ul>
 * <p>
 * Slot layout (54 total slots in the menu container):
 * <pre>
 *   Slots 0-8:   Captor's offered items (trade row)
 *   Slots 9-17:  Prisoner's offered items (trade row)
 *   Slots 18-44: Viewing player's inventory (main 27 slots)
 *   Slots 45-53: Viewing player's hotbar (9 slots)
 * </pre>
 * <p>
 * Requirements: 14.5, 15 (open question #21 — manual)
 * Design: §16 (mode=MANUAL)
 */
public class RansomTradeMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Number of trade slots per player. */
    public static final int TRADE_SLOTS_PER_PLAYER = 9;

    /** Total trade slots (both players combined). */
    private static final int TOTAL_TRADE_SLOTS = TRADE_SLOTS_PER_PLAYER * 2;

    /** Display title for the menu. */
    public static final Component TITLE = Component.translatable("wp.captivity.ransom_trade.title");

    /** The passport ID being ransomed. */
    private final String passportId;

    /** UUID of the captor (the player holding the trophy passport). */
    private final UUID captorUuid;

    /** UUID of the prisoner (the original passport owner). */
    private final UUID prisonerUuid;

    /** Whether the captor has confirmed the trade. */
    private boolean captorConfirmed;

    /** Whether the prisoner has confirmed the trade. */
    private boolean prisonerConfirmed;

    /** Reference to the CaptivityService for executing the ransom (server-side only). */
    @Nullable
    private final CaptivityService captivityService;

    /** Whether the trade has been completed (prevents double-execution). */
    private boolean completed;

    // ─────────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Server-side constructor. Called when opening the menu for a player.
     *
     * @param containerId     the container/window ID
     * @param playerInventory the viewing player's inventory
     * @param passportId      the passport ID being ransomed
     * @param captorUuid      UUID of the captor
     * @param prisonerUuid    UUID of the prisoner
     * @param captivityService reference to the captivity service for ransom execution
     */
    public RansomTradeMenu(int containerId, Inventory playerInventory,
                           String passportId, UUID captorUuid, UUID prisonerUuid,
                           @Nullable CaptivityService captivityService) {
        super(WpMenuTypes.RANSOM_TRADE.get(), containerId);
        this.passportId = passportId;
        this.captorUuid = captorUuid;
        this.prisonerUuid = prisonerUuid;
        this.captivityService = captivityService;
        this.captorConfirmed = false;
        this.prisonerConfirmed = false;
        this.completed = false;

        // Per-instance trade container — avoids cross-session item leakage
        SimpleTradeContainer tradeContainer = new SimpleTradeContainer(TRADE_SLOTS_PER_PLAYER * 2);

        // --- Trade slots (captor row: 0-8) ---
        for (int col = 0; col < TRADE_SLOTS_PER_PLAYER; col++) {
            addSlot(new TradeSlot(tradeContainer, col, 8 + col * 18, 18));
        }

        // --- Trade slots (prisoner row: 9-17) ---
        for (int col = 0; col < TRADE_SLOTS_PER_PLAYER; col++) {
            addSlot(new TradeSlot(tradeContainer, TRADE_SLOTS_PER_PLAYER + col, 8 + col * 18, 54));
        }

        // --- Player inventory (main: slots 18-44) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // --- Player hotbar (slots 45-53) ---
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /**
     * Client-side constructor. Called from the network factory when the client receives
     * the menu open packet.
     *
     * @param containerId     the container/window ID
     * @param playerInventory the viewing player's inventory
     * @param buf             the extra data buffer containing passportId, captorUuid, prisonerUuid
     * @return a new RansomTradeMenu instance for the client
     */
    public static RansomTradeMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        String passportId = buf.readUtf(16);
        UUID captorUuid = buf.readUUID();
        UUID prisonerUuid = buf.readUUID();
        return new RansomTradeMenu(containerId, playerInventory, passportId, captorUuid, prisonerUuid, null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Trade confirmation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Called when a player confirms the trade.
     * <p>
     * If both players have confirmed, the ransom is executed atomically via
     * {@link CaptivityService#ransom(String, ServerPlayer, ServerPlayer)}.
     *
     * @param player the player confirming the trade
     * @return true if the trade was completed as a result of this confirmation
     */
    public boolean confirm(Player player) {
        if (completed) {
            return false;
        }

        UUID playerUuid = player.getUUID();

        if (playerUuid.equals(captorUuid)) {
            captorConfirmed = true;
            LOGGER.debug("Captor {} confirmed ransom trade for passport {}", playerUuid, passportId);
        } else if (playerUuid.equals(prisonerUuid)) {
            prisonerConfirmed = true;
            LOGGER.debug("Prisoner {} confirmed ransom trade for passport {}", playerUuid, passportId);
        } else {
            LOGGER.warn("Unknown player {} attempted to confirm ransom trade for passport {}", playerUuid, passportId);
            return false;
        }

        // Check if both have confirmed
        if (captorConfirmed && prisonerConfirmed) {
            return executeRansom(player);
        }

        return false;
    }

    /**
     * Resets the confirmation state for a player (e.g., when they modify their offered items).
     *
     * @param player the player whose confirmation should be reset
     */
    public void resetConfirmation(Player player) {
        UUID playerUuid = player.getUUID();
        if (playerUuid.equals(captorUuid)) {
            captorConfirmed = false;
        } else if (playerUuid.equals(prisonerUuid)) {
            prisonerConfirmed = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Ransom execution
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Executes the ransom via CaptivityService. Only runs on the server side.
     *
     * @param triggeringPlayer the player whose confirmation triggered the execution
     * @return true if the ransom was successfully executed
     */
    private boolean executeRansom(Player triggeringPlayer) {
        if (captivityService == null) {
            // Client side — no-op, server will handle
            return false;
        }

        if (!(triggeringPlayer instanceof ServerPlayer)) {
            return false;
        }

        ServerPlayer serverPlayer = (ServerPlayer) triggeringPlayer;
        ServerPlayer captor = serverPlayer.getServer().getPlayerList().getPlayer(captorUuid);
        ServerPlayer prisoner = serverPlayer.getServer().getPlayerList().getPlayer(prisonerUuid);

        if (captor == null || prisoner == null) {
            LOGGER.warn("Cannot execute ransom: captor or prisoner is offline. passportId={}", passportId);
            return false;
        }

        CaptivityService.RansomResult result = captivityService.ransom(passportId, captor, prisoner);

        if (result instanceof CaptivityService.RansomResult.Success) {
            completed = true;
            LOGGER.info("Ransom trade completed for passport {}. Captor: {}, Prisoner: {}",
                    passportId, captorUuid, prisonerUuid);

            // Close the menu for both players
            captor.closeContainer();
            prisoner.closeContainer();
            return true;
        } else if (result instanceof CaptivityService.RansomResult.Failure failure) {
            LOGGER.warn("Ransom trade failed for passport {}: {}", passportId, failure.errorKey());
            return false;
        }

        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // AbstractContainerMenu overrides
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            if (slotIndex < TOTAL_TRADE_SLOTS) {
                // Moving from trade slots to player inventory
                if (!this.moveItemStackTo(stackInSlot, TOTAL_TRADE_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to trade slots
                // Determine which trade row based on the player
                int tradeStart = 0;
                int tradeEnd = TRADE_SLOTS_PER_PLAYER;
                if (player.getUUID().equals(prisonerUuid)) {
                    tradeStart = TRADE_SLOTS_PER_PLAYER;
                    tradeEnd = TOTAL_TRADE_SLOTS;
                }
                if (!this.moveItemStackTo(stackInSlot, tradeStart, tradeEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            // Reset confirmations when items change
            captorConfirmed = false;
            prisonerConfirmed = false;
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (completed) {
            return false;
        }
        UUID playerUuid = player.getUUID();
        return playerUuid.equals(captorUuid) || playerUuid.equals(prisonerUuid);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────────

    public String getPassportId() {
        return passportId;
    }

    public UUID getCaptorUuid() {
        return captorUuid;
    }

    public UUID getPrisonerUuid() {
        return prisonerUuid;
    }

    public boolean isCaptorConfirmed() {
        return captorConfirmed;
    }

    public boolean isPrisonerConfirmed() {
        return prisonerConfirmed;
    }

    public boolean isCompleted() {
        return completed;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Trade slot (virtual container slot for offered items)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * A virtual slot used for trade offers. Items placed here are "offered" but not
     * yet transferred until both parties confirm.
     * <p>
     * Each menu instance creates its own {@link SimpleTradeContainer} so that
     * concurrent trade sessions do not share or leak items between each other.
     */
    private static class TradeSlot extends Slot {
        TradeSlot(SimpleTradeContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }
    }

    /**
     * Per-instance container backing for trade slots.
     * Each RansomTradeMenu creates its own SimpleTradeContainer to avoid
     * cross-session item leakage.
     */
    private static class SimpleTradeContainer implements net.minecraft.world.Container {
        private final net.minecraft.core.NonNullList<ItemStack> items;

        SimpleTradeContainer(int size) {
            this.items = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);
        }

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            if (amount >= stack.getCount()) {
                items.set(slot, ItemStack.EMPTY);
                return stack;
            }
            return stack.split(amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            items.clear();
        }
    }
}
