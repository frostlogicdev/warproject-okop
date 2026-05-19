package com.frostlogic.warproject.server.map;

import com.frostlogic.warproject.WarProject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Server-side counterpart of {@code JourneymapConfigPatcher}: applies our
 * recommended JM defaults to the server-side global properties file so that
 * faraway chunks are auto-mapped and streamed to clients beyond their render
 * distance.
 * <p>
 * JM stores server-side configs at {@code <server>/journeymap/server/<JM_MAJOR_MINOR>/journeymap.server.global.config}.
 * If that file doesn't exist when the patcher first runs (i.e. JM hasn't
 * generated defaults yet), we just skip — JM will create defaults this
 * launch and we patch them on the next server start.
 * <p>
 * A sentinel file {@code warproject_imported.flag} guards against repeated
 * patching: once applied, the admin can hand-edit the JSON freely.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class JourneymapServerConfigPatcher {

    private JourneymapServerConfigPatcher() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            tryApply(event.getServer());
        } catch (Throwable t) {
            // Never fatal — fall back to JM's defaults.
            WarProject.LOGGER.warn("[WP JM Server Config] Patch failed: {}", t.getMessage(), t);
        }
    }

    private static void tryApply(MinecraftServer server) {
        Path serverDir = server.getServerDirectory();
        Path jmDir = serverDir.resolve("journeymap").resolve("server").resolve("6.0");
        Path globalCfg = jmDir.resolve("journeymap.server.global.config");
        Path flag = jmDir.getParent().resolve("warproject_imported.flag");

        if (!Files.isDirectory(jmDir)) {
            WarProject.LOGGER.debug("[WP JM Server Config] {} not present yet — JM will generate defaults this launch. Will retry next start.", jmDir);
            return;
        }
        if (Files.exists(flag)) {
            WarProject.LOGGER.debug("[WP JM Server Config] Already patched (flag present); leaving config alone.");
            return;
        }

        // Edits to apply against journeymap.server.global.config.
        // All of these fields live in the "Inherit" / "Permissions" categories
        // of GlobalProperties (subclass of PermissionProperties).
        List<FieldEdit> edits = List.of(
                // Pump server-allowed render range to JM's hard cap (32 chunks).
                // 0 = "use client value"; we set 32 so distant areas keep streaming.
                new FieldEdit("surfaceRenderRange", 32),
                new FieldEdit("caveRenderRange", 32),
                // Make sure surface/topo mapping are open to all players (default
                // is ALL but a fresh install of JM may keep OPS for safety).
                new FieldEdit("surfaceMapping", "ALL"),
                new FieldEdit("topoMapping", "ALL"),
                new FieldEdit("biomeMapping", "ALL"),
                // Allow ally and enemy radar — without it the player-radar packets
                // we add separately would be the only sighting source.
                new FieldEdit("radarEnabled", "ALL"),
                new FieldEdit("playerRadarEnabled", true),
                new FieldEdit("playerRadarNamesEnabled", true),
                // Disable death-points (military RP — no automatic markers).
                new FieldEdit("allowDeathPoints", false)
        );

        if (!Files.isRegularFile(globalCfg)) {
            WarProject.LOGGER.debug("[WP JM Server Config] {} not present, will retry next start", globalCfg);
            return;
        }

        try {
            String original = Files.readString(globalCfg, StandardCharsets.UTF_8);
            // Strip leading comment lines (JM uses // comments which are not valid JSON)
            StringBuilder jsonBuilder = new StringBuilder();
            for (String line : original.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("//")) {
                    jsonBuilder.append(line).append("\n");
                }
            }
            String jsonStr = jsonBuilder.toString().trim();
            if (jsonStr.isEmpty()) return;

            JsonElement root = JsonParser.parseString(jsonStr);
            if (!root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();

            // JM server config can be either:
            // A) Flat JSON: { "surfaceMapping": "ALL", ... }
            // B) Nested with categories: { "categories": { "Permissions": { "field": { "value": ... } } } }
            // We handle both formats.

            boolean dirty = false;
            JsonObject categories = obj.has("categories") && obj.get("categories").isJsonObject()
                    ? obj.getAsJsonObject("categories")
                    : null;

            for (FieldEdit e : edits) {
                if (categories != null) {
                    // Nested format — walk categories
                    for (var entry : categories.entrySet()) {
                        if (!entry.getValue().isJsonObject()) continue;
                        JsonObject cat = entry.getValue().getAsJsonObject();
                        if (!cat.has(e.field) || !cat.get(e.field).isJsonObject()) continue;
                        JsonObject leaf = cat.getAsJsonObject(e.field);
                        if (!leaf.has("value")) continue;
                        JsonElement before = leaf.get("value");
                        JsonElement after = toJson(e.value);
                        if (after.equals(before)) break;
                        leaf.add("value", after);
                        dirty = true;
                        break;
                    }
                } else if (obj.has(e.field)) {
                    // Flat format — direct key-value pairs
                    JsonElement before = obj.get(e.field);
                    String afterStr = String.valueOf(e.value);
                    JsonElement after = new com.google.gson.JsonPrimitive(afterStr);
                    if (!after.equals(before)) {
                        obj.addProperty(e.field, afterStr);
                        dirty = true;
                    }
                }
            }

            if (dirty) {
                // Rebuild with comments header
                String header = """
                        // JourneyMap server configuration file. Patched by WarProject.
                        // To restore defaults, delete this file and warproject_imported.flag
                        //
                        // Global Server Configuration : Applies to all dimensions unless overridden.\s
                        """;
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                String formatted = header + gson.toJson(obj);
                Files.write(globalCfg, formatted.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                WarProject.LOGGER.info("[WP JM Server Config] Patched {}", globalCfg.getFileName());
            }

            Files.write(flag, ("Generated by WarProject @ " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            WarProject.LOGGER.info("[WP JM Server Config] Flag written to {}", flag);
        } catch (Exception e) {
            WarProject.LOGGER.warn("[WP JM Server Config] I/O while patching {}: {}", globalCfg.getFileName(), e.getMessage());
        }
    }

    private static JsonElement toJson(Object value) {
        if (value instanceof Boolean b) return new com.google.gson.JsonPrimitive(b);
        if (value instanceof Number n) return new com.google.gson.JsonPrimitive(n);
        if (value instanceof String s) return new com.google.gson.JsonPrimitive(s);
        return new com.google.gson.JsonPrimitive(String.valueOf(value));
    }

    private record FieldEdit(String field, Object value) {}
}
