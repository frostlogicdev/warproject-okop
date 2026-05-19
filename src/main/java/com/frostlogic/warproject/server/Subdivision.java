package com.frostlogic.warproject.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Subdivision {
    private final String id;
    private final Faction faction;
    private String name;
    private UUID commanderUuid;
    private final Set<UUID> members;
    private final long createdAt;

    public Subdivision(String id, Faction faction, String name, UUID commanderUuid, long createdAt) {
        this.id = id;
        this.faction = faction;
        this.name = name;
        this.commanderUuid = commanderUuid;
        this.members = new HashSet<>();
        if (commanderUuid != null) {
            this.members.add(commanderUuid);
        }
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public Faction getFaction() {
        return faction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getCommanderUuid() {
        return commanderUuid;
    }

    public void setCommanderUuid(UUID commanderUuid) {
        this.commanderUuid = commanderUuid;
        if (commanderUuid != null) {
            this.members.add(commanderUuid);
        }
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return uuid != null && members.contains(uuid);
    }

    public boolean addMember(UUID uuid) {
        return uuid != null && members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        if (uuid.equals(commanderUuid)) {
            return false;
        }
        return members.remove(uuid);
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
