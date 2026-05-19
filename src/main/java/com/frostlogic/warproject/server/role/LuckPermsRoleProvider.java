package com.frostlogic.warproject.server.role;

import com.frostlogic.warproject.attachment.Role;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional read-only adapter for LuckPerms role resolution.
 * <p>
 * When {@code cfg.roles.provider = LUCKPERMS_FALLBACK}, this provider is consulted
 * as a fallback source for player roles. It reads group membership from LuckPerms
 * and maps it to the WarProject {@link Role} hierarchy.
 * <p>
 * This is a stub/placeholder for future LuckPerms integration.
 * The actual implementation will depend on the LuckPerms API being available at runtime.
 * <p>
 * Requirements: 22.1, 22.2
 * Design: §16 (Open Question #23)
 */
public final class LuckPermsRoleProvider {

    /** Whether LuckPerms is available on the classpath at runtime. */
    private final boolean available;

    public LuckPermsRoleProvider() {
        this.available = isLuckPermsPresent();
        if (!available) {
            // Log at INFO so server admins know the fallback is inactive.
            // If config says LUCKPERMS_FALLBACK but LP isn't installed, roles
            // will silently fall back to the internal system (which is correct).
            try {
                org.slf4j.LoggerFactory.getLogger(LuckPermsRoleProvider.class)
                        .info("[WarProject] LuckPerms not found on classpath — LUCKPERMS_FALLBACK provider will always defer to internal role system.");
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Returns whether LuckPerms is available on the server.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Attempts to resolve a player's role from LuckPerms group membership.
     * <p>
     * Mapping (read-only):
     * <ul>
     *   <li>Group "general" → {@link Role#GENERAL}</li>
     *   <li>Group "commander" → {@link Role#COMMANDER}</li>
     *   <li>Group "soldier" → {@link Role#SOLDIER}</li>
     *   <li>Otherwise → empty (fall back to internal system)</li>
     * </ul>
     *
     * @param playerUuid the UUID of the player to resolve
     * @return the resolved role, or empty if LuckPerms is unavailable or player has no mapped group
     */
    public Optional<Role> resolve(UUID playerUuid) {
        if (!available) {
            return Optional.empty();
        }
        // Stub: actual LuckPerms API integration would go here.
        // Example pseudocode:
        //   LuckPerms lp = LuckPermsProvider.get();
        //   User user = lp.getUserManager().getUser(playerUuid);
        //   if (user == null) return Optional.empty();
        //   String primaryGroup = user.getPrimaryGroup();
        //   return mapGroupToRole(primaryGroup);
        return Optional.empty();
    }

    /**
     * Checks if the LuckPerms API is available on the classpath.
     */
    private static boolean isLuckPermsPresent() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
