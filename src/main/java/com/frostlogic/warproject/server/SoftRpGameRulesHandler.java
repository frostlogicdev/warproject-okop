package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Forces a curated set of vanilla game-rules on every loaded dimension when
 * the server starts, encoding War Project's "приятный сервер" design choices.
 * <p>
 * Enforced rules:
 * <ul>
 *   <li>{@code keepInventory = true} — soft RP death; the 120s respawn delay
 *       handled by {@link com.frostlogic.warproject.server.combat.RealisticDeathHandler}
 *       stays, but nothing drops on death and XP is preserved.</li>
 *   <li>{@code doMobSpawning = false} — pure PvP/RP server, no hostile mob
 *       clutter at night.</li>
 *   <li>{@code playersSleepingPercentage = 101} — beds never skip night;
 *       full day/night cycle remains intact for atmosphere.</li>
 *   <li>{@code showDeathMessages = false} — death messages are suppressed
 *       globally; {@link com.frostlogic.warproject.server.combat.CustomDeathMessageHandler}
 *       privately notifies the killer and the victim only.</li>
 *   <li>{@code announceAdvancements = false} — advancement broadcasts add
 *       no RP value and clutter the chat.</li>
 * </ul>
 * <p>
 * Rules are reapplied on every server start so they survive world copies,
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
            GameRules rules = level.getGameRules();

            forceBool(rules, GameRules.RULE_KEEPINVENTORY, true, server, "keepInventory", level);
            forceBool(rules, GameRules.RULE_DOMOBSPAWNING, false, server, "doMobSpawning", level);
            forceBool(rules, GameRules.RULE_SHOWDEATHMESSAGES, false, server, "showDeathMessages", level);
            forceBool(rules, GameRules.RULE_ANNOUNCEADVANCEMENTS, false, server, "announceAdvancements", level);
            forceInt(rules, GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE, 101, server,
                    "playersSleepingPercentage", level);
        }
    }

    private static void forceBool(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                  boolean target, MinecraftServer server, String name, ServerLevel level) {
        GameRules.BooleanValue v = rules.getRule(key);
        if (v.get() != target) {
            v.set(target, server);
            WarProject.LOGGER.info("[WP SoftRP] {} forced to {} for dimension {}",
                    name, target, level.dimension().location());
        }
    }

    private static void forceInt(GameRules rules, GameRules.Key<GameRules.IntegerValue> key,
                                 int target, MinecraftServer server, String name, ServerLevel level) {
        GameRules.IntegerValue v = rules.getRule(key);
        if (v.get() != target) {
            v.set(target, server);
            WarProject.LOGGER.info("[WP SoftRP] {} forced to {} for dimension {}",
                    name, target, level.dimension().location());
        }
    }
}
