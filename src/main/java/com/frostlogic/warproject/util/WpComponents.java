package com.frostlogic.warproject.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Centralized helpers for constructing {@link Component} instances that participate
 * in the WarProject localization pipeline.
 *
 * <p>All user-facing strings produced by mod code MUST go through one of these helpers
 * so that they are resolved against {@code assets/warproject/lang/*.json} at render
 * time. This guarantees both Russian and English fallback text and keeps the code free
 * of hard-coded literals.
 *
 * <p>Design references: §3 (Module Decomposition — {@code util/WpComponents}) and
 * §10 (Resource Pack Layout). Validates Requirement 19.1 (use of
 * {@code Component.translatable} for every user-visible message).
 */
public final class WpComponents {

    /** modId used to address custom fonts shipped under {@code assets/warproject/font}. */
    public static final String MOD_ID = "warproject";

    private WpComponents() {
        // utility class — no instances
    }

    // ---------------------------------------------------------------------
    // Core helpers
    // ---------------------------------------------------------------------

    /**
     * Build a translatable {@link Component} for the given i18n key and arguments.
     *
     * <p>This is the canonical entry point for any user-visible message. The returned
     * component is a {@link MutableComponent}, but is exposed as {@link Component} so
     * callers do not accidentally mutate shared instances.
     *
     * @param key  fully qualified translation key (e.g. {@code "wp.error.no_target"})
     * @param args optional arguments forwarded to the language file placeholders
     * @return a fresh translatable component
     */
    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * Like {@link #tr(String, Object...)}, but the result is returned as a
     * {@link MutableComponent} for callers that want to chain {@code withStyle(...)}
     * or {@code append(...)} on top.
     */
    public static MutableComponent mtr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    // ---------------------------------------------------------------------
    // Styled message helpers
    // ---------------------------------------------------------------------

    /**
     * Translatable message styled red, intended for user-visible errors and refusals.
     */
    public static MutableComponent error(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    /**
     * Translatable message styled green, intended for confirmations of successful actions.
     */
    public static MutableComponent success(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GREEN);
    }

    /**
     * Translatable message styled gray, intended for informational / non-critical hints.
     */
    public static MutableComponent info(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    /**
     * Translatable message styled yellow, intended for warnings and pending-state hints.
     */
    public static MutableComponent warn(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.YELLOW);
    }

    /**
     * Translatable message styled gold, intended for highlighted system announcements.
     */
    public static MutableComponent highlight(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GOLD);
    }

    // ---------------------------------------------------------------------
    // Font helpers (Resource Pack §10)
    // ---------------------------------------------------------------------

    /** {@link ResourceLocation} of the WarProject "designer" header font. */
    public static final ResourceLocation FONT_DESIGNER =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "designer");

    /** {@link ResourceLocation} of the WarProject "handwritten" passport-signature font. */
    public static final ResourceLocation FONT_HANDWRITTEN =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "handwritten");

    /**
     * Apply the WarProject designer header font to a component.
     */
    public static MutableComponent withDesignerFont(MutableComponent component) {
        return component.withStyle(Style.EMPTY.withFont(FONT_DESIGNER));
    }

    /**
     * Apply the WarProject handwritten font (used on the Passport signature) to a component.
     */
    public static MutableComponent withHandwrittenFont(MutableComponent component) {
        return component.withStyle(Style.EMPTY.withFont(FONT_HANDWRITTEN));
    }
}
