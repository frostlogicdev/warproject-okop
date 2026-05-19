package com.frostlogic.warproject.server.role;

import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Resolves the effective {@link Role} for a {@link CommandSourceStack}.
 * <p>
 * Resolution logic:
 * <ol>
 *   <li>If the source has permission level &ge; 2 (vanilla OP), the role is {@link Role#OP}.</li>
 *   <li>Otherwise, reads the cached role from {@link WpAttachmentTypes#ROLE} attachment on the player entity.</li>
 *   <li>If the source is not a player (e.g. console/command block), returns {@link Role#OP}.</li>
 * </ol>
 * <p>
 * Requirements: 10.x, 22.1, 22.2
 * Design: §3, §7
 */
public final class RoleResolver {

    private static final int OP_PERMISSION_LEVEL = 2;

    private RoleResolver() {
        // utility class
    }

    /**
     * Resolves the effective role for the given command source.
     *
     * @param source the command source stack
     * @return the resolved role, never null
     */
    public static Role resolve(CommandSourceStack source) {
        // Non-player sources (console, command blocks) are treated as OP
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return Role.OP;
        }

        // Vanilla OP (permission level >= 2) always maps to Role.OP
        if (source.hasPermission(OP_PERMISSION_LEVEL)) {
            return Role.OP;
        }

        // Read from the player's cached attachment
        return player.getData(WpAttachmentTypes.ROLE);
    }

    /**
     * Checks whether the command source has at least the given role.
     *
     * @param source      the command source stack
     * @param requiredRole the minimum required role
     * @return true if the source's effective role is at least {@code requiredRole}
     */
    public static boolean atLeast(CommandSourceStack source, Role requiredRole) {
        return resolve(source).atLeast(requiredRole);
    }
}
