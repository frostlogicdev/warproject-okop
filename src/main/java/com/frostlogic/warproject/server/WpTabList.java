package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Maintains a server-side TAB list header / footer with the WarProject brand
 * and player count. Vanilla still draws ping bars next to each player name —
 * we only own the header / footer rows.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class WpTabList {
    /** Cadence of periodic refresh (in server ticks). 100 ticks ≈ 5 s. */
    private static final int TICK_INTERVAL = 100;

    private static int tickCounter;

    private WpTabList() {
    }

    public static void update(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int online = server.getPlayerList().getPlayerCount();
        Component header = Component.literal("\n")
                .append(Component.literal("WarProject")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n"));
        Component footer = Component.literal("\n")
                .append(Component.literal("Игроков онлайн: " + online)
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"));
        ClientboundTabListPacket packet = new ClientboundTabListPacket(header, footer);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The connection is already open by the time PlayerLoggedInEvent fires;
        // sending immediately is fine, but we also refresh all players so the
        // online-count in the footer reflects the new arrival.
        MinecraftServer server = player.getServer();
        if (server != null) {
            update(server);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null) {
            update(server);
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        update(event.getServer());
    }
}
