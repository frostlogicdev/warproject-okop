package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Forces the vanilla {@code keepInventory} game-rule to {@code true} on every
 * loaded dimension when the server starts.
 * <p>
 * War Project's death policy is "soft RP" — the death cinematic and 60s
 * respawn delay handled by
 * {@link com.frostlogic.warproject.server.combat.RealisticDeathHandler} stay,
 * but nothing drops on death and the player keeps their XP. Setting the
 * built-in {@code keepInventory} rule is the simplest way to express that in
 * Minecraft 1.21 — it preserves both the inventory and the player's XP.
 * <p>
 * The rule is reapplied on every server start so it survives world copies,
 * dimension regenerations, and accidental operator overrides between
 * restarts.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class SoftRpGameRulesHandler {

    private SoftRpGameRulesHandler() {
        // static event subscriber — no instantiation
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            GameRules.BooleanValue keepInv = level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
            if (!keepInv.get()) {
                keepInv.set(true, server);
                WarProject.LOGGER.info("[WP SoftRP] keepInventory forced ON for dimension {}", level.dimension().location());
            }
        }
    }
}
