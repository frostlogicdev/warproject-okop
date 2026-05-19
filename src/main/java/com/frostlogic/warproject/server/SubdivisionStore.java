package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SubdivisionStore {
    private static final SubdivisionStore INSTANCE = new SubdivisionStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, Subdivision> byId = new LinkedHashMap<>();
    private Path file;

    private SubdivisionStore() {
    }

    public static SubdivisionStore get() {
        return INSTANCE;
    }

    public void load(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path dir = worldDir.resolve("warproject");
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            WarProject.LOGGER.error("Cannot create warproject directory", ex);
        }
        this.file = dir.resolve("subdivisions.json");
        byId.clear();
        if (!Files.exists(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonArray list = root.getAsJsonObject().getAsJsonArray("subdivisions");
            if (list == null) {
                return;
            }
            for (JsonElement element : list) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Subdivision subdivision = readSubdivision(element.getAsJsonObject());
                if (subdivision != null) {
                    byId.put(subdivision.getId(), subdivision);
                }
            }
        } catch (Exception ex) {
            WarProject.LOGGER.error("Failed to load subdivisions.json", ex);
        }
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
        }
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        for (Subdivision subdivision : byId.values()) {
            list.add(writeSubdivision(subdivision));
        }
        root.add("subdivisions", list);
        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            WarProject.LOGGER.error("Failed to save subdivisions.json", ex);
        }
    }

    public Optional<Subdivision> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Subdivision> findByMember(UUID uuid) {
        for (Subdivision subdivision : byId.values()) {
            if (subdivision.isMember(uuid)) {
                return Optional.of(subdivision);
            }
        }
        return Optional.empty();
    }

    public List<Subdivision> byFaction(Faction faction) {
        List<Subdivision> result = new ArrayList<>();
        for (Subdivision subdivision : byId.values()) {
            if (subdivision.getFaction() == faction) {
                result.add(subdivision);
            }
        }
        return result;
    }

    public Collection<Subdivision> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Optional<Subdivision> create(Faction faction, String name, UUID commander) {
        if (faction == null || !faction.isPlayable() || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String id = makeId(faction, name);
        if (byId.containsKey(id)) {
            return Optional.empty();
        }
        Subdivision subdivision = new Subdivision(id, faction, name.trim(), commander, System.currentTimeMillis());
        byId.put(id, subdivision);
        return Optional.of(subdivision);
    }

    public boolean disband(String id) {
        return byId.remove(id) != null;
    }

    public boolean addMember(String id, UUID uuid) {
        Subdivision subdivision = byId.get(id);
        if (subdivision == null) {
            return false;
        }
        return subdivision.addMember(uuid);
    }

    public boolean removeMember(String id, UUID uuid) {
        Subdivision subdivision = byId.get(id);
        if (subdivision == null) {
            return false;
        }
        return subdivision.removeMember(uuid);
    }

    public Map<String, String> nameSuggestions(Faction faction) {
        Map<String, String> map = new HashMap<>();
        for (Subdivision subdivision : byId.values()) {
            if (faction == null || subdivision.getFaction() == faction) {
                map.put(subdivision.getId(), subdivision.getName());
            }
        }
        return map;
    }

    public static String makeId(Faction faction, String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-zа-я0-9_-]", "");
        if (slug.isBlank()) {
            slug = "subdiv";
        }
        return faction.id() + "_" + slug;
    }

    private static Subdivision readSubdivision(JsonObject object) {
        try {
            String id = object.get("id").getAsString();
            Faction faction = Faction.fromInput(object.get("faction").getAsString()).orElse(Faction.NONE);
            String name = object.get("name").getAsString();
            UUID commander = UUID.fromString(object.get("commander").getAsString());
            long createdAt = object.has("createdAt") ? object.get("createdAt").getAsLong() : System.currentTimeMillis();
            Subdivision subdivision = new Subdivision(id, faction, name, commander, createdAt);
            JsonArray members = object.getAsJsonArray("members");
            if (members != null) {
                for (JsonElement element : members) {
                    try {
                        subdivision.addMember(UUID.fromString(element.getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return subdivision;
        } catch (Exception ex) {
            WarProject.LOGGER.warn("Skipping invalid subdivision entry", ex);
            return null;
        }
    }

    private static JsonObject writeSubdivision(Subdivision subdivision) {
        JsonObject object = new JsonObject();
        object.addProperty("id", subdivision.getId());
        object.addProperty("faction", subdivision.getFaction().id());
        object.addProperty("name", subdivision.getName());
        if (subdivision.getCommanderUuid() != null) {
            object.addProperty("commander", subdivision.getCommanderUuid().toString());
        }
        JsonArray members = new JsonArray();
        for (UUID uuid : subdivision.getMembers()) {
            members.add(uuid.toString());
        }
        object.add("members", members);
        object.addProperty("createdAt", subdivision.getCreatedAt());
        return object;
    }
}
