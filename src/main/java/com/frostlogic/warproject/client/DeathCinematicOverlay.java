package com.frostlogic.warproject.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Client-side realistic death cinematic overlay.
 * <p>
 * Renders a multi-phase death animation when the player dies:
 * <ol>
 *   <li><b>Phase 1 — Impact</b> (0–0.5s for headshot, 0–2s for body):
 *       Rapid darkening with red flash. Headshot = instant near-blackout.</li>
 *   <li><b>Phase 2 — Fading consciousness</b> (next 3s):
 *       Screen goes dark with intermittent "blinks" — brief moments of blurry
 *       light breaking through the darkness, simulating eyes fluttering.</li>
 *   <li><b>Phase 3 — Eyes closing</b> (next 2s):
 *       Two black bars close from top and bottom like eyelids shutting.</li>
 *   <li><b>Phase 4 — Blackout</b> (remaining time until respawn):
 *       Full black screen with respawn countdown timer.</li>
 * </ol>
 * <p>
 * The overlay suppresses the vanilla death screen while active.
 */
public final class DeathCinematicOverlay {

    // ─── State ────────────────────────────────────────────────────────────────────

    private static boolean active = false;
    private static boolean headshot = false;
    private static long startTimeMs = 0;
    private static int totalDurationTicks = 1200; // 60 seconds default
    private static long startTick = 0;

    // Phase durations in milliseconds
    private static final long HEADSHOT_IMPACT_MS = 500;
    private static final long BODY_IMPACT_MS = 2000;
    private static final long BLINK_PHASE_MS = 3000;
    private static final long EYES_CLOSE_MS = 2000;

    private DeathCinematicOverlay() {
    }

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Starts the death cinematic.
     *
     * @param isHeadshot        whether the kill was a headshot
     * @param respawnDelayTicks total respawn delay in ticks
     */
    public static void start(boolean isHeadshot, int respawnDelayTicks) {
        active = true;
        headshot = isHeadshot;
        startTimeMs = System.currentTimeMillis();
        totalDurationTicks = respawnDelayTicks;
        Minecraft mc = Minecraft.getInstance();
        startTick = mc.level != null ? mc.level.getGameTime() : 0;
    }

    /**
     * Stops the cinematic (on respawn or disconnect).
     */
    public static void stop() {
        active = false;
    }

    /**
     * Returns whether the cinematic is currently playing.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Returns remaining seconds until respawn, or 0 if not active.
     */
    public static int getRemainingSeconds() {
        if (!active) return 0;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        long totalMs = (long) totalDurationTicks * 50L; // ticks to ms
        long remaining = totalMs - elapsed;
        return (int) Math.max(0, (remaining + 999) / 1000);
    }

    // ─── Rendering ────────────────────────────────────────────────────────────────

    /**
     * Called every frame from the HUD render event. Renders the death cinematic overlay.
     *
     * @param graphics the GuiGraphics context
     * @param width    screen width
     * @param height   screen height
     */
    public static void render(GuiGraphics graphics, int width, int height) {
        if (!active) return;

        long elapsed = System.currentTimeMillis() - startTimeMs;
        long totalMs = (long) totalDurationTicks * 50L;

        // Auto-stop if time exceeded (safety)
        if (elapsed >= totalMs) {
            stop();
            return;
        }

        long impactDuration = headshot ? HEADSHOT_IMPACT_MS : BODY_IMPACT_MS;
        long blinkStart = impactDuration;
        long blinkEnd = blinkStart + BLINK_PHASE_MS;
        long eyesCloseEnd = blinkEnd + EYES_CLOSE_MS;

        if (elapsed < impactDuration) {
            renderImpactPhase(graphics, width, height, elapsed, impactDuration);
        } else if (elapsed < blinkEnd) {
            renderBlinkPhase(graphics, width, height, elapsed - blinkStart);
        } else if (elapsed < eyesCloseEnd) {
            renderEyesClosePhase(graphics, width, height, elapsed - blinkEnd);
        } else {
            renderBlackoutPhase(graphics, width, height);
        }
    }

    // ─── Phase Renderers ──────────────────────────────────────────────────────────

    /**
     * Phase 1: Impact — rapid darkening with red tint.
     * Headshot: almost instant blackout with bright red flash.
     * Body: gradual darkening over 2 seconds.
     */
    private static void renderImpactPhase(GuiGraphics graphics, int w, int h, long elapsed, long duration) {
        float progress = Mth.clamp((float) elapsed / duration, 0f, 1f);

        // Red flash (stronger for headshot, fades quickly)
        float redAlpha;
        if (headshot) {
            // Intense flash that peaks at 20% and fades
            redAlpha = progress < 0.2f ? progress / 0.2f * 0.7f : (1f - progress) * 0.5f;
        } else {
            // Subtle red that peaks at 30% and fades
            redAlpha = progress < 0.3f ? progress / 0.3f * 0.4f : (1f - progress) * 0.3f;
        }
        if (redAlpha > 0.01f) {
            fillRect(graphics, 0, 0, w, h, 0.6f, 0.0f, 0.0f, redAlpha);
        }

        // Darkening — headshot goes to 95% black fast, body to 80%
        float targetDarkness = headshot ? 0.95f : 0.8f;
        float darkness = progress * targetDarkness;
        fillRect(graphics, 0, 0, w, h, 0f, 0f, 0f, darkness);
    }

    /**
     * Phase 2: Blinking — dark with intermittent "blinks" of blurry light.
     * Simulates eyes fluttering open briefly then closing again.
     */
    private static void renderBlinkPhase(GuiGraphics graphics, int w, int h, long elapsedInPhase) {
        float phaseProgress = Mth.clamp((float) elapsedInPhase / BLINK_PHASE_MS, 0f, 1f);

        // Base darkness increases over the phase (consciousness fading)
        float baseDarkness = 0.85f + phaseProgress * 0.1f;

        // Blink pattern: 3-4 blinks, each shorter than the last
        // Each blink briefly reduces darkness (eyes flutter open)
        float blinkReduction = 0f;
        float blinkTime = phaseProgress * 4f; // 4 blink cycles
        float blinkCycle = blinkTime - (float) Math.floor(blinkTime); // 0..1 within each cycle

        // Each blink: quick open (0-0.2) then close (0.2-0.5), rest is closed
        if (blinkCycle < 0.15f) {
            // Opening
            float openProgress = blinkCycle / 0.15f;
            // Each successive blink is weaker
            float blinkStrength = Math.max(0f, 0.4f - phaseProgress * 0.35f);
            blinkReduction = openProgress * blinkStrength;
        } else if (blinkCycle < 0.3f) {
            // Closing
            float closeProgress = (blinkCycle - 0.15f) / 0.15f;
            float blinkStrength = Math.max(0f, 0.4f - phaseProgress * 0.35f);
            blinkReduction = (1f - closeProgress) * blinkStrength;
        }

        float finalDarkness = baseDarkness - blinkReduction;

        // During blinks, add slight blur effect (simulated with semi-transparent layers)
        if (blinkReduction > 0.05f) {
            // Blurry light — render a slightly lighter, desaturated overlay
            fillRect(graphics, 0, 0, w, h, 0.15f, 0.12f, 0.1f, blinkReduction * 0.3f);
        }

        fillRect(graphics, 0, 0, w, h, 0f, 0f, 0f, finalDarkness);
    }

    /**
     * Phase 3: Eyes closing — two black bars close from top and bottom like eyelids.
     */
    private static void renderEyesClosePhase(GuiGraphics graphics, int w, int h, long elapsedInPhase) {
        float progress = Mth.clamp((float) elapsedInPhase / EYES_CLOSE_MS, 0f, 1f);

        // Ease-out curve for natural eyelid movement (fast start, slow end)
        float eased = 1f - (1f - progress) * (1f - progress);

        // Background is already very dark
        fillRect(graphics, 0, 0, w, h, 0f, 0f, 0f, 0.92f);

        // Top eyelid
        int topBarHeight = (int) (h * 0.5f * eased);
        fillRect(graphics, 0, 0, w, topBarHeight, 0f, 0f, 0f, 1f);

        // Bottom eyelid
        int bottomBarHeight = (int) (h * 0.5f * eased);
        fillRect(graphics, 0, h - bottomBarHeight, w, h, 0f, 0f, 0f, 1f);

        // Slight reddish tint on the "eyelid" edges (blood vessels visible through closed eyelids)
        if (eased > 0.3f && eased < 1.0f) {
            int edgeHeight = 3;
            float edgeAlpha = (1f - eased) * 0.3f;
            fillRect(graphics, 0, topBarHeight - edgeHeight, w, topBarHeight, 0.2f, 0.02f, 0.0f, edgeAlpha);
            fillRect(graphics, 0, h - bottomBarHeight, w, h - bottomBarHeight + edgeHeight, 0.2f, 0.02f, 0.0f, edgeAlpha);
        }
    }

    /**
     * Phase 4: Full blackout with respawn countdown.
     */
    private static void renderBlackoutPhase(GuiGraphics graphics, int w, int h) {
        // Full black
        fillRect(graphics, 0, 0, w, h, 0f, 0f, 0f, 1f);

        // Respawn countdown text
        int remaining = getRemainingSeconds();
        String text;
        if (remaining > 0) {
            text = "Возрождение через " + remaining + " сек...";
        } else {
            text = "Возрождение...";
        }

        Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(text);
        int x = (w - textWidth) / 2;
        int y = h / 2;

        // Subtle pulsing alpha for the text
        long elapsed = System.currentTimeMillis() - startTimeMs;
        float pulse = 0.5f + 0.3f * Mth.sin((float) (elapsed * 0.003));
        int alpha = (int) (pulse * 255);
        int color = (alpha << 24) | 0xCCCCCC; // light gray with pulsing alpha

        graphics.drawString(mc.font, text, x, y, color, false);
    }

    // ─── Utility ──────────────────────────────────────────────────────────────────

    /**
     * Fills a rectangle with the given RGBA color using the GUI overlay render type.
     */
    private static void fillRect(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                 float r, float g, float b, float a) {
        if (a <= 0.001f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, x1, y2, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.disableBlend();
    }
}
