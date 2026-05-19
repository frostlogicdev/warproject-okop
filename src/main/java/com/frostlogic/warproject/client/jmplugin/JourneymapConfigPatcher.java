package com.frostlogic.warproject.client.jmplugin;

import com.frostlogic.warproject.WarProject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies our recommended JourneyMap defaults to the user's config files.
 * <p>
 * JourneyMap stores its client config in {@code .minecraft/journeymap/config/6.0/}
 * as JSON-serialized {@code PropertiesBase} files (one per category):
 * {@code journeymap.core.config}, {@code journeymap.minimap1.config},
 * {@code journeymap.minimap2.config}, {@code journeymap.fullmap.config},
 * {@code journeymap.waypoint.config}.
 * <p>
 * On first run the patcher writes a sentinel file
 * {@code .minecraft/journeymap/config/warproject_imported.flag} so it never
 * touches user-edited configs again. The user can re-trigger by deleting that
 * flag.
 * <p>
 * Why a patch instead of shipping a full config file:
 * <ul>
 *     <li>JM's serialized format includes per-field metadata (defaults, valid
 *         ranges, version) that we'd need to keep in lockstep with the JM
 *         version. A targeted patch only touches the {@code value} of a few
 *         leaf fields.</li>
 *     <li>If JM hasn't been launched yet, the config files don't exist. We
 *         skip silently — JM will create defaults on its next start, and our
 *         patcher runs again on the player's next login.</li>
 * </ul>
 */
public final class JourneymapConfigPatcher {

    private JourneymapConfigPatcher() {}

    /**
     * Applies our recommended overlay to JM configs. Idempotent — only runs
     * the first time it sees a config directory without our flag.
     *
     * @return {@code true} if a patch was applied or the flag already existed
     *         (i.e. nothing further needs doing).
     */
    public static boolean tryApply() {
        try {
            Path mcDir = resolveMinecraftDir();
            if (mcDir == null) {
                WarProject.LOGGER.debug("[WP JM Config] Cannot resolve Minecraft directory; skipping");
                return false;
            }

            // Most installs use the standard config root; if a per-world override
            // exists, JM looks at it first. We only touch the standard one to
            // avoid surprising server-specific overrides.
            Path jmConfigDir = mcDir.resolve("journeymap").resolve("config").resolve("6.0");
            if (!Files.isDirectory(jmConfigDir)) {
                WarProject.LOGGER.debug("[WP JM Config] {} not found yet — JourneyMap may not have been launched once. Skipping; will retry next login.", jmConfigDir);
                return false;
            }

            Path flag = jmConfigDir.getParent().resolve("warproject_imported.flag");
            if (Files.exists(flag)) {
                WarProject.LOGGER.debug("[WP JM Config] Already patched (flag present); leaving user config alone.");
                return true;
            }

            // ─── Patch list ─────────────────────────────────────────────────
            // Each entry: filename, list of (categoryName, fieldName, newValue).
            List<Patch> patches = List.of(
                    new Patch(jmConfigDir.resolve("journeymap.core.config"), List.of(
                            // Hide the "What's new / developers" splash by recording
                            // the splash as already-seen.
                            f("Common", "splashViewed", "9.9.9"),
                            // Crank up surface map render distance for "see more
                            // map at once". JM clamps this internally.
                            f("Common", "renderdistance_surface_max", 32),
                            f("Common", "renderdistance_cave_max", 16),
                            // Pretty-up surface tiles (slope shading + biome blends).
                            f("Common", "map_style_topography", true),
                            f("Common", "map_style_shadows", true),
                            f("Common", "map_style_blendgrass", true),
                            f("Common", "map_style_blendfoliage", true),
                            f("Common", "map_style_blendwater", true),
                            f("Common", "map_style_antialiasing", true),
                            // Military RP: hide caves entirely on the map.
                            f("Common", "alwaysmapcaves", false),
                            f("Common", "map_style_caveshowsurface", true),
                            // Enable automap: JM will map chunks as they are loaded
                            // by the server, even beyond the player's view distance.
                            f("Common", "automap_enabled", true),
                            // Suppress the "JourneyMap: Press [key]" chat messages.
                            f("Common", "announce_mod", false),
                            // Disable the "What's New" popup entirely.
                            f("Common", "optIn_updateCheck", false)
                    )),
                    // Apply the same look + position to BOTH minimap presets so the
                    // user can switch presets without losing the layout.
                    new Patch(jmConfigDir.resolve("journeymap.minimap1.config"), miniMapFields()),
                    new Patch(jmConfigDir.resolve("journeymap.minimap2.config"), miniMapFields())
            );

            int applied = 0;
            for (Patch p : patches) {
                if (apply(p)) applied++;
            }

            // ─── Keybind patch: rebind fullscreen map to M ──────────────────
            applied += patchKeybinds(mcDir) ? 1 : 0;

            // Drop the sentinel even if some files were missing — we don't want
            // to keep retrying every login forever.
            Files.write(flag, ("Generated by WarProject @ " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            WarProject.LOGGER.info("[WP JM Config] Patched {} JourneyMap config file(s); flag written to {}", applied, flag);
            return true;
        } catch (Throwable t) {
            // Patcher failures are never fatal — JM just stays on its own defaults.
            WarProject.LOGGER.warn("[WP JM Config] Patch failed: {}", t.getMessage(), t);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static List<FieldEdit> miniMapFields() {
        // The minimap config format has its own enum-string for position.
        // JM serializes Position enum as the "key" attribute (matches the
        // KeyedEnum interface in the v2 API), e.g. "TopLeft" / "TopRight".
        List<FieldEdit> out = new ArrayList<>();
        out.add(f("Minimap", "position", "TopLeft"));
        out.add(f("Minimap", "showFps", false));
        // Topography + slope on minimap too.
        out.add(f("Minimap", "showCompass", true));
        out.add(f("Minimap", "showLocation", true));
        out.add(f("Minimap", "showBiome", false));
        return out;
    }

    private static Path resolveMinecraftDir() {
        try {
            // The integrated server's `LevelResource.ROOT` is rooted at the
            // world directory, not the install dir. Prefer Minecraft.gameDirectory
            // which is `.minecraft` itself.
            return Minecraft.getInstance().gameDirectory.toPath();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Applies a single patch to a single JM config file. Returns {@code true}
     * if any field was actually changed and the file was rewritten.
     */
    private static boolean apply(Patch patch) {
        if (!Files.isRegularFile(patch.file)) {
            WarProject.LOGGER.debug("[WP JM Config] {} not present, skipping", patch.file.getFileName());
            return false;
        }
        try {
            String original = Files.readString(patch.file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(original);
            if (!root.isJsonObject()) return false;
            JsonObject obj = root.getAsJsonObject();
            JsonObject categories = obj.has("categories") && obj.get("categories").isJsonObject()
                    ? obj.getAsJsonObject("categories")
                    : null;
            if (categories == null) return false;

            boolean dirty = false;
            for (FieldEdit edit : patch.edits) {
                if (!categories.has(edit.category) || !categories.get(edit.category).isJsonObject()) continue;
                JsonObject cat = categories.getAsJsonObject(edit.category);
                if (!cat.has(edit.field) || !cat.get(edit.field).isJsonObject()) continue;
                JsonObject field = cat.getAsJsonObject(edit.field);
                // JM stores leaf values under "value". Mutating just this leaf
                // preserves all surrounding metadata (default, range, version).
                if (!field.has("value")) continue;
                JsonElement before = field.get("value");
                JsonElement after = toJson(edit.value);
                if (after.equals(before)) continue;
                field.add("value", after);
                dirty = true;
            }

            if (dirty) {
                String updated = obj.toString();
                Files.write(patch.file, updated.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                WarProject.LOGGER.info("[WP JM Config] Patched {}", patch.file.getFileName());
            }
            return dirty;
        } catch (IOException e) {
            WarProject.LOGGER.warn("[WP JM Config] I/O while patching {}: {}", patch.file.getFileName(), e.getMessage());
            return false;
        }
    }

    private static JsonElement toJson(Object value) {
        if (value instanceof Boolean b) return new com.google.gson.JsonPrimitive(b);
        if (value instanceof Number n) return new com.google.gson.JsonPrimitive(n);
        if (value instanceof String s) return new com.google.gson.JsonPrimitive(s);
        return new com.google.gson.JsonPrimitive(String.valueOf(value));
    }

    private record Patch(Path file, List<FieldEdit> edits) {}
    private record FieldEdit(String category, String field, Object value) {}
    private static FieldEdit f(String category, String field, Object value) {
        return new FieldEdit(category, field, value);
    }

    // ─── Keybind patching ───────────────────────────────────────────────────

    /**
     * Patches JourneyMap's keybind options file to rebind the fullscreen map
     * from J (default) to M. This runs once on first login and is guarded by
     * the same sentinel flag as the config patches.
     * <p>
     * JourneyMap stores keybinds in Minecraft's standard {@code options.txt}
     * under keys like {@code key_key.journeymap.fullscreen_map}. We patch
     * that file directly.
     * <p>
     * Additionally, JM 6.0 stores its own keybind overrides in
     * {@code journeymap/config/6.0/journeymap.core.config} under the
     * "KeyBindings" category. We patch both locations for maximum compatibility.
     */
    private static boolean patchKeybinds(Path mcDir) {
        boolean patched = false;

        // 1. Patch Minecraft's options.txt
        Path optionsFile = mcDir.resolve("options.txt");
        if (Files.isRegularFile(optionsFile)) {
            try {
                List<String> lines = Files.readAllLines(optionsFile, StandardCharsets.UTF_8);
                List<String> newLines = new ArrayList<>(lines.size());
                boolean changed = false;

                for (String line : lines) {
                    // JourneyMap fullscreen map keybind line format:
                    // key_key.journeymap.map_toggle_alt:key.keyboard.j
                    if (line.startsWith("key_key.journeymap.map_toggle_alt:")) {
                        String newLine = "key_key.journeymap.map_toggle_alt:key.keyboard.m";
                        if (!line.equals(newLine)) {
                            newLines.add(newLine);
                            changed = true;
                        } else {
                            newLines.add(line);
                        }
                    } else {
                        newLines.add(line);
                    }
                }

                // If the key wasn't found (JM not launched yet), append it
                if (!changed) {
                    boolean found = lines.stream().anyMatch(l -> l.startsWith("key_key.journeymap.map_toggle_alt:"));
                    if (!found) {
                        newLines.add("key_key.journeymap.map_toggle_alt:key.keyboard.m");
                        changed = true;
                    }
                }

                if (changed) {
                    Files.write(optionsFile, newLines, StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    WarProject.LOGGER.info("[WP JM Config] Patched options.txt: fullscreen_map → M");
                    patched = true;
                }
            } catch (IOException e) {
                WarProject.LOGGER.warn("[WP JM Config] Failed to patch options.txt keybinds: {}", e.getMessage());
            }
        }

        // 2. Patch JM's own core config if it has a KeyBindings section
        Path jmCoreConfig = mcDir.resolve("journeymap").resolve("config").resolve("6.0")
                .resolve("journeymap.core.config");
        if (Files.isRegularFile(jmCoreConfig)) {
            try {
                String content = Files.readString(jmCoreConfig, StandardCharsets.UTF_8);
                // JM stores keybinds as "key.journeymap.fullscreen_map" with value "j" or "key.keyboard.j"
                // Replace any occurrence of the fullscreen map key being bound to J
                String updated = content;
                // Pattern: the value field for fullscreen_map key
                if (content.contains("fullscreen_map") && content.contains("\"j\"")) {
                    updated = content.replace("\"j\"", "\"m\"");
                    if (!updated.equals(content)) {
                        Files.write(jmCoreConfig, updated.getBytes(StandardCharsets.UTF_8),
                                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                        WarProject.LOGGER.info("[WP JM Config] Patched journeymap.core.config: fullscreen_map → M");
                        patched = true;
                    }
                }
            } catch (IOException e) {
                WarProject.LOGGER.warn("[WP JM Config] Failed to patch JM core config keybinds: {}", e.getMessage());
            }
        }

        return patched;
    }
}
