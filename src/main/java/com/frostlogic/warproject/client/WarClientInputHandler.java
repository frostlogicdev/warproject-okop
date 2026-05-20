package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.c2s.RadialOpenRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side input handler for the WarProject hotkeys.
 * <p>
 * On press of the radial-menu key (default {@code H}, configurable via
 * {@link WarKeyBindings#RADIAL_MENU}) — when no screen is open — sends a
 * {@link RadialOpenRequestPayload} to the server. The server runs its own
 * raytrace and authorization (per design §11 SP-7) and replies with a
 * {@link com.frostlogic.warproject.network.payload.s2c.RadialMenuPayload}
 * which the {@link com.frostlogic.warproject.network.ClientPayloadHandler}
 * uses to open {@link RadialMenuScreen}. The client never opens the menu
 * itself.
 * <p>
 * The local raytrace below is best-effort UX only: if it can't find a
 * candidate target, the client still sends the request (the server will
 * decide and reply with an {@code AuthErrorPayload} if no target exists).
 * This avoids any client-authoritative gate on opening the menu.
 * <p>
 * Requirements: 9.1
 * Design: §5.3, §11 SP-7
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class WarClientInputHandler {
    private static final double LOCAL_HINT_DISTANCE = 5.0D;
    private static boolean radialRequested = false;
    /** Tracks previous middle-button state to detect press edge. */
    private static boolean middleWasDown = false;

    private WarClientInputHandler() {
    }

    /**
     * Intercepts middle mouse button BEFORE vanilla processes it (pick block).
     * Opens the marker TTL screen ONLY when the fullscreen map is open,
     * so normal pick-block (copy block) works during gameplay.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onMouseButton(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        // Only react to middle mouse button press (action=1 = GLFW_PRESS)
        if (event.getButton() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Only intercept when the fullscreen map screen is open
        if (!isMapScreen(mc.screen)) return;

        // Open the marker TTL screen
        net.minecraft.world.phys.HitResult hit = pickBlockOrPosition(mc);
        if (hit != null) {
            net.minecraft.core.BlockPos pos = blockPosOf(hit);
            if (pos != null) {
                mc.setScreen(new com.frostlogic.warproject.client.screen.MarkerTtlScreen(pos));
                event.setCanceled(true); // prevent vanilla pick block
            }
        }
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        WarLoginClient.tickPending();

        boolean keyDown = WarKeyBindings.RADIAL_MENU.isDown();
        net.minecraft.client.gui.screens.Screen current = mc.screen;

        // Consume all queued clicks first
        boolean clicked = false;
        while (WarKeyBindings.RADIAL_MENU.consumeClick()) {
            clicked = true;
        }

        // Toggle behavior: press H to open, press H again (or Esc) to close.
        if (clicked) {
            if (current instanceof RadialMenuScreen) {
                // Already open — close it
                mc.setScreen(null);
            } else if (current == null && !radialRequested) {
                // No screen open — request the radial menu from server
                radialRequested = true;
                Player target = findTargetPlayer(mc);
                java.util.UUID hint = target != null ? target.getUUID() : new java.util.UUID(0L, 0L);
                float yaw = mc.player.getYRot();
                float pitch = mc.player.getXRot();
                PacketDistributor.sendToServer(new RadialOpenRequestPayload(hint, yaw, pitch));
            }
        }

        // Reset the request flag when the radial screen is closed (by Esc, click, or toggle)
        if (!(mc.screen instanceof RadialMenuScreen)) {
            radialRequested = false;
        }

        // Middle mouse button marker — only when the map screen is open.
        // Fallback: poll GLFW directly in case the event doesn't fire.
        boolean middleDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (middleDown && !middleWasDown && isMapScreen(mc.screen)) {
            // Rising edge — middle button just pressed while map is open
            net.minecraft.world.phys.HitResult hit = pickBlockOrPosition(mc);
            if (hit != null) {
                net.minecraft.core.BlockPos pos = blockPosOf(hit);
                if (pos != null) {
                    mc.setScreen(new com.frostlogic.warproject.client.screen.MarkerTtlScreen(pos));
                }
            }
        }
        middleWasDown = middleDown;

        // Drain any queued clicks so they don't accumulate.
        while (WarKeyBindings.SET_MARKER.consumeClick()) {
            // handled above
        }
    }

    /**
     * Block raycast from the camera with a generous range. Returns the first hit
     * (block or far-end miss). The miss case still gives us a usable block pos
     * (the player's feet) so the marker can always be placed somewhere.
     */
    private static net.minecraft.world.phys.HitResult pickBlockOrPosition(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        double range = 256.0; // long range so distant points on the map work
        Vec3 from = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 to = from.add(look.scale(range));
        var ctx = new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player
        );
        net.minecraft.world.phys.BlockHitResult result = mc.level.clip(ctx);
        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            // No block hit: drop the marker on the player's feet.
            return new net.minecraft.world.phys.BlockHitResult(
                    mc.player.position(),
                    net.minecraft.core.Direction.UP,
                    mc.player.blockPosition(),
                    false
            );
        }
        return result;
    }

    private static net.minecraft.core.BlockPos blockPosOf(net.minecraft.world.phys.HitResult hit) {
        if (hit instanceof net.minecraft.world.phys.BlockHitResult b) {
            return b.getBlockPos();
        }
        return null;
    }

    /**
     * Returns true if the given screen is the JourneyMap fullscreen map.
     * JourneyMap's fullscreen map screen class is checked by simple name
     * to avoid a hard dependency on the JourneyMap API.
     */
    private static boolean isMapScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null) return false;
        String className = screen.getClass().getName();
        return className.contains("journeymap") && className.contains("Fullscreen") ||
               className.contains("journeymap") && className.contains("MapScreen");
    }

    private static Player findTargetPlayer(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return null;
        }
        Vec3 from = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 to = from.add(look.scale(LOCAL_HINT_DISTANCE));
        AABB bbox = camera.getBoundingBox().expandTowards(look.scale(LOCAL_HINT_DISTANCE)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, from, to, bbox,
                e -> e instanceof Player && e != camera && e.isAlive(),
                LOCAL_HINT_DISTANCE * LOCAL_HINT_DISTANCE);
        if (hit != null && hit.getEntity() instanceof Player p) {
            return p;
        }
        return null;
    }
}
