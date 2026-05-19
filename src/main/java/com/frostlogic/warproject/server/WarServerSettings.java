package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class WarServerSettings {
    private static final WarServerSettings INSTANCE = new WarServerSettings();
    private static final double DEFAULT_INTRO_RADIUS = 24.0;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Mutations to bases / scalars all go through `synchronized` setters which
    // also `save()`. Reads (hot path on every player tick × every online player)
    // can be lock-free thanks to `volatile`. Concurrent reads against a setter
    // see either the old or the new reference atomically — both are valid
    // values, never a torn one.
    private final java.util.Map<Faction, WarpPoint> basePoints = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile WarpPoint introPoint;
    private volatile WarpPoint spawnPoint;
    private volatile double introRadius = DEFAULT_INTRO_RADIUS;
    private volatile boolean restrictNewPlayers = true;
    private Path settingsFile;
    private volatile boolean loaded;

    private WarServerSettings() {
    }

    public static WarServerSettings get() {
        return INSTANCE;
    }

    public synchronized void load(MinecraftServer server) {
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve(WarProject.MOD_ID);
        settingsFile = dataDir.resolve("settings.json");
        basePoints.clear();
        introPoint = null;
        spawnPoint = null;
        introRadius = DEFAULT_INTRO_RADIUS;
        restrictNewPlayers = true;

        try {
            Files.createDirectories(dataDir);
            if (Files.exists(settingsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element != null && element.isJsonObject()) {
                        JsonObject object = element.getAsJsonObject();
                        introPoint = readPoint(object, "introPoint");
                        spawnPoint = readPoint(object, "spawnPoint");
                        JsonElement bases = object.get("bases");
                        if (bases != null && bases.isJsonObject()) {
                            JsonObject basesObject = bases.getAsJsonObject();
                            for (Faction faction : Faction.values()) {
                                if (!faction.isPlayable()) {
                                    continue;
                                }
                                WarpPoint point = readPoint(basesObject, faction.id());
                                if (point != null) {
                                    basePoints.put(faction, point);
                                }
                            }
                        }
                        if (object.has("introRadius") && !object.get("introRadius").isJsonNull()) {
                            introRadius = Math.max(2.0, object.get("introRadius").getAsDouble());
                        }
                        if (object.has("restrictNewPlayers") && !object.get("restrictNewPlayers").isJsonNull()) {
                            restrictNewPlayers = object.get("restrictNewPlayers").getAsBoolean();
                        }
                    }
                }
            }
            loaded = true;
            WarProject.LOGGER.info("War Project settings loaded. intro={}, spawn={}, bases={}",
                    introPoint != null, spawnPoint != null, basePoints.size());
        } catch (IOException ex) {
            loaded = true;
            WarProject.LOGGER.error("Failed to load War Project settings", ex);
        }
    }

    public synchronized void save() {
        if (settingsFile == null) {
            return;
        }

        try {
            Files.createDirectories(settingsFile.getParent());
            JsonObject root = new JsonObject();
            writePoint(root, "introPoint", introPoint);
            writePoint(root, "spawnPoint", spawnPoint);
            JsonObject basesObject = new JsonObject();
            for (Map.Entry<Faction, WarpPoint> entry : basePoints.entrySet()) {
                writePoint(basesObject, entry.getKey().id(), entry.getValue());
            }
            root.add("bases", basesObject);
            root.addProperty("introRadius", introRadius);
            root.addProperty("restrictNewPlayers", restrictNewPlayers);

            try (BufferedWriter writer = Files.newBufferedWriter(settingsFile, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (IOException ex) {
            WarProject.LOGGER.error("Failed to save War Project settings", ex);
        }
    }

    public Optional<WarpPoint> getIntroPoint() {
        return Optional.ofNullable(introPoint);
    }

    public Optional<WarpPoint> getSpawnPoint() {
        return Optional.ofNullable(spawnPoint);
    }

    public Optional<WarpPoint> getBasePoint(Faction faction) {
        if (faction == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(basePoints.get(faction));
    }

    public synchronized void setIntroPoint(WarpPoint point) {
        this.introPoint = point;
        save();
    }

    public synchronized void setSpawnPoint(WarpPoint point) {
        this.spawnPoint = point;
        save();
    }

    public synchronized void setBasePoint(Faction faction, WarpPoint point) {
        if (faction == null || !faction.isPlayable() || point == null) {
            return;
        }
        basePoints.put(faction, point);
        save();
    }

    public double getIntroRadius() {
        return introRadius;
    }

    public synchronized void setIntroRadius(double radius) {
        this.introRadius = Math.max(2.0, radius);
        save();
    }

    public boolean isRestrictNewPlayers() {
        return restrictNewPlayers;
    }

    public synchronized void setRestrictNewPlayers(boolean restrictNewPlayers) {
        this.restrictNewPlayers = restrictNewPlayers;
        save();
    }

    public boolean isLoaded() {
        return loaded;
    }

    private WarpPoint readPoint(JsonObject root, String key) {
        if (!root.has(key)) {
            return null;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return WarpPoint.deserialize(element.getAsString()).orElse(null);
    }

    private void writePoint(JsonObject root, String key, WarpPoint point) {
        if (point == null) {
            root.add(key, com.google.gson.JsonNull.INSTANCE);
        } else {
            root.addProperty(key, point.serialize());
        }
    }
}
