package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WarPlayerDataStore {
    private static final WarPlayerDataStore INSTANCE = new WarPlayerDataStore();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, WarPlayerProfile> profiles = new LinkedHashMap<>();
    private Path dataDir;
    private Path profilesFile;
    private Path auditLogFile;
    private boolean loaded;

    private WarPlayerDataStore() {
    }

    public static WarPlayerDataStore get() {
        return INSTANCE;
    }

    public synchronized void load(MinecraftServer server) {
        dataDir = server.getWorldPath(LevelResource.ROOT).resolve(WarProject.MOD_ID);
        profilesFile = dataDir.resolve("players.json");
        auditLogFile = dataDir.resolve("audit.log");
        profiles.clear();

        try {
            Files.createDirectories(dataDir);
            if (Files.exists(profilesFile)) {
                try (BufferedReader reader = Files.newBufferedReader(profilesFile, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element != null && element.isJsonObject()) {
                        JsonArray array = element.getAsJsonObject().getAsJsonArray("profiles");
                        if (array != null) {
                            for (JsonElement profileElement : array) {
                                if (profileElement.isJsonObject()) {
                                    readProfile(profileElement.getAsJsonObject()).ifPresent(profile -> profiles.put(profile.getUuid(), profile));
                                }
                            }
                        }
                    }
                }
            }
            loaded = true;
            WarProject.LOGGER.info("War Project player profiles loaded: {}", profiles.size());
        } catch (IOException | IllegalStateException ex) {
            loaded = true;
            WarProject.LOGGER.error("Failed to load War Project player profiles", ex);
        }
    }

    public synchronized void save() {
        if (profilesFile == null) {
            return;
        }

        try {
            Files.createDirectories(dataDir);
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (WarPlayerProfile profile : profiles.values()) {
                array.add(writeProfile(profile));
            }
            root.add("profiles", array);

            try (BufferedWriter writer = Files.newBufferedWriter(profilesFile, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (IOException ex) {
            WarProject.LOGGER.error("Failed to save War Project player profiles", ex);
        }
    }

    public synchronized WarPlayerProfile getOrCreate(ServerPlayer player) {
        ensureLoaded(player.getServer());
        WarPlayerProfile profile = profiles.computeIfAbsent(player.getUUID(), uuid -> WarPlayerProfile.create(uuid, player.getGameProfile().getName()));
        profile.setLastKnownName(player.getGameProfile().getName());
        return profile;
    }

    public synchronized Optional<WarPlayerProfile> findByName(MinecraftServer server, String name) {
        ensureLoaded(server);
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(getOrCreate(online));
        }

        return profiles.values().stream()
                .filter(profile -> profile.getLastKnownName().equalsIgnoreCase(name))
                .findFirst();
    }

    public synchronized Collection<WarPlayerProfile> profiles(MinecraftServer server) {
        ensureLoaded(server);
        return new ArrayList<>(profiles.values());
    }

    public synchronized Optional<WarPlayerProfile> findByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(uuid));
    }

    public synchronized void appendAuditLog(MinecraftServer server, String message) {
        ensureLoaded(server);
        if (auditLogFile == null) {
            return;
        }

        String line = "[" + Instant.now() + "] " + message + System.lineSeparator();
        try {
            Files.createDirectories(dataDir);
            Files.writeString(auditLogFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            WarProject.LOGGER.error("Failed to write War Project audit log", ex);
        }
    }

    private void ensureLoaded(MinecraftServer server) {
        if (!loaded) {
            load(server);
        }
    }

    private Optional<WarPlayerProfile> readProfile(JsonObject object) {
        try {
            UUID uuid = UUID.fromString(getString(object, "uuid", ""));
            WarPlayerProfile profile = WarPlayerProfile.create(uuid, getString(object, "lastKnownName", ""));
            profile.setRpName(getString(object, "rpName", ""));
            profile.setFaction(readFaction(object, "faction"));
            profile.setCandidateFaction(readFaction(object, "candidateFaction"));
            profile.setRank(readRank(object, "rank"));
            profile.setCollaborator(getBoolean(object, "collaborator", false));
            profile.setCollaborationDeclaredBy(readFaction(object, "collaborationDeclaredBy"));
            profile.setCaptchaPassed(getBoolean(object, "captchaPassed", getBoolean(object, "robotCheckPassed", false)));
            profile.setPasswordHash(getString(object, "passwordHash", ""));
            profile.setPasswordSalt(getString(object, "passwordSalt", ""));
            profile.setAge((int) getLong(object, "age", 0L));
            profile.setBirthCountry(Country.fromIdOrUnknown(getString(object, "birthCountry", Country.UNKNOWN.id())));
            profile.setSubdivisionId(getString(object, "subdivisionId", ""));
            profile.setCaptive(getBoolean(object, "captive", false));
            String capturedBy = getString(object, "capturedBy", "");
            if (!capturedBy.isBlank()) {
                try {
                    profile.setCapturedBy(UUID.fromString(capturedBy));
                } catch (IllegalArgumentException ignored) {
                }
            }
            profile.setCreatedAt(getLong(object, "createdAt", System.currentTimeMillis()));
            profile.setUpdatedAt(getLong(object, "updatedAt", System.currentTimeMillis()));
            profile.setLastCommanderChatAt(getLong(object, "lastCommanderChatAt", 0L));
            // bio + diary (added 2026-05; missing on legacy profiles, safe defaults).
            profile.setBio(getString(object, "bio", ""));
            JsonElement diaryEl = object.get("diary");
            if (diaryEl != null && diaryEl.isJsonArray()) {
                java.util.List<WarPlayerProfile.DiaryEntry> entries = new java.util.ArrayList<>();
                for (JsonElement entryEl : diaryEl.getAsJsonArray()) {
                    if (!entryEl.isJsonObject()) continue;
                    JsonObject entryObj = entryEl.getAsJsonObject();
                    long ts = getLong(entryObj, "ts", 0L);
                    String text = getString(entryObj, "text", "");
                    if (!text.isBlank()) {
                        entries.add(new WarPlayerProfile.DiaryEntry(ts, text));
                    }
                }
                profile.replaceDiary(entries);
            }
            return Optional.of(profile);
        } catch (IllegalArgumentException ex) {
            WarProject.LOGGER.warn("Skipping invalid War Project profile entry", ex);
            return Optional.empty();
        }
    }

    private JsonObject writeProfile(WarPlayerProfile profile) {
        JsonObject object = new JsonObject();
        object.addProperty("uuid", profile.getUuid().toString());
        object.addProperty("lastKnownName", profile.getLastKnownName());
        object.addProperty("rpName", profile.getRpName());
        object.addProperty("faction", profile.getFaction().id());
        object.addProperty("candidateFaction", profile.getCandidateFaction().id());
        object.addProperty("rank", profile.getRank().id());
        object.addProperty("collaborator", profile.isCollaborator());
        object.addProperty("collaborationDeclaredBy", profile.getCollaborationDeclaredBy().id());
        object.addProperty("captchaPassed", profile.isCaptchaPassed());
        object.addProperty("passwordHash", profile.getPasswordHash());
        object.addProperty("passwordSalt", profile.getPasswordSalt());
        object.addProperty("age", profile.getAge());
        object.addProperty("birthCountry", profile.getBirthCountry().id());
        object.addProperty("subdivisionId", profile.getSubdivisionId());
        object.addProperty("captive", profile.isCaptive());
        if (profile.getCapturedBy() != null) {
            object.addProperty("capturedBy", profile.getCapturedBy().toString());
        }
        object.addProperty("createdAt", profile.getCreatedAt());
        object.addProperty("updatedAt", profile.getUpdatedAt());
        object.addProperty("lastCommanderChatAt", profile.getLastCommanderChatAt());
        // bio + diary (added 2026-05). Bio is a plain string; diary is an array
        // of {ts, text} objects, oldest entry first.
        object.addProperty("bio", profile.getBio());
        JsonArray diaryArray = new JsonArray();
        for (WarPlayerProfile.DiaryEntry entry : profile.getDiary()) {
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("ts", entry.timestamp());
            entryObj.addProperty("text", entry.text());
            diaryArray.add(entryObj);
        }
        object.add("diary", diaryArray);
        return object;
    }

    private static Faction readFaction(JsonObject object, String key) {
        return Faction.fromInput(getString(object, key, Faction.NONE.id())).orElse(Faction.NONE);
    }

    private static Rank readRank(JsonObject object, String key) {
        return Rank.fromInput(getString(object, key, Rank.NONE.id())).orElse(Rank.NONE);
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : fallback;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsBoolean() : fallback;
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsLong() : fallback;
    }
}
