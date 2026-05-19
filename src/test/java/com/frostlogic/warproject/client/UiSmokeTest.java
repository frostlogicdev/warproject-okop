package com.frostlogic.warproject.client;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the WarProject client UI screens (task 21.7).
 * <p>
 * The screens themselves depend on a running Minecraft client instance
 * (Minecraft.getInstance(), font, fonts, GuiGraphics, etc.) so they cannot be
 * instantiated under a plain JUnit harness. Instead, this test verifies the
 * minimum invariants that <em>are</em> safely observable from a unit test:
 * <ol>
 *   <li>Each screen class is reachable on the test classpath and can be loaded
 *       via {@link Class#forName(String, boolean, ClassLoader)} <em>without</em>
 *       triggering its static initialiser. A {@link ClassNotFoundException} or
 *       {@link NoClassDefFoundError} thrown here would indicate the screen
 *       class moved, was renamed, or was accidentally deleted.</li>
 *   <li>Every translation key statically referenced by those screens is
 *       declared in both {@code en_us.json} and {@code ru_ru.json}, so each
 *       screen will render without falling back to raw key strings.</li>
 * </ol>
 *
 * <p><b>Validates: Requirements 2.1, 3.1, 6.3, 9.x, 12.3 (UI translation coverage)</b>
 */
class UiSmokeTest {

    private static final String LOGIN_SCREEN = "com.frostlogic.warproject.client.screen.LoginScreen";
    private static final String REGISTER_SCREEN = "com.frostlogic.warproject.client.screen.RegisterScreen";
    private static final String FACTION_CHOICE_SCREEN = "com.frostlogic.warproject.client.screen.FactionChoiceScreen";
    private static final String PASSPORT_SCREEN = "com.frostlogic.warproject.client.screen.PassportScreen";
    private static final String RADIAL_MENU_SCREEN = "com.frostlogic.warproject.client.RadialMenuScreen";

    private static final String[] SCREEN_CLASSES = {
            LOGIN_SCREEN,
            REGISTER_SCREEN,
            FACTION_CHOICE_SCREEN,
            PASSPORT_SCREEN,
            RADIAL_MENU_SCREEN,
    };

    private static JsonObject ruRu;
    private static JsonObject enUs;

    @BeforeAll
    static void loadLangFiles() throws IOException {
        ruRu = readLangResource("assets/warproject/lang/ru_ru.json");
        enUs = readLangResource("assets/warproject/lang/en_us.json");
    }

    // ---------------------------------------------------------------------
    // 1. Screen class loading
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("All UI screen classes are reachable on the test classpath")
    void screenClassesAreLoadable() {
        ClassLoader loader = UiSmokeTest.class.getClassLoader();
        for (String className : SCREEN_CLASSES) {
            // initialize=false so that we do not trigger any static initialisers
            // that might reach into Minecraft.getInstance().
            assertThatScreenLoads(className, loader);
        }
    }

    private static void assertThatScreenLoads(String className, ClassLoader loader) {
        try {
            Class<?> cls = Class.forName(className, false, loader);
            assertThat(cls)
                    .as("Class.forName('%s') must return a non-null Class", className)
                    .isNotNull();
            assertThat(cls.getName()).isEqualTo(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Screen class missing from test classpath: " + className, e);
        } catch (NoClassDefFoundError e) {
            throw new AssertionError(
                    "Screen class '" + className + "' could not be linked: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------
    // 2. Translation key coverage per screen
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("LoginScreen translation keys exist in both lang files")
    void loginScreenKeysExist() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("wp.auth.title");
        keys.add("wp.auth.password");
        keys.add("wp.auth.login");
        assertKeysPresent("LoginScreen", keys);
    }

    @Test
    @DisplayName("RegisterScreen translation keys exist in both lang files")
    void registerScreenKeysExist() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("wp.auth.title");
        keys.add("wp.auth.password");
        keys.add("wp.auth.password_confirm");
        keys.add("wp.auth.register");
        keys.add("wp.auth.show");
        keys.add("wp.auth.hide");
        assertKeysPresent("RegisterScreen", keys);
    }

    @Test
    @DisplayName("FactionChoiceScreen translation keys exist in both lang files")
    void factionChoiceScreenKeysExist() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("wp.faction.choice_title");
        keys.add("wp.faction.choice_question");
        keys.add("wp.faction.confirm");
        keys.add("wp.faction.cancel");
        // The screen also resolves the faction display name from these keys.
        for (FactionId id : FactionId.values()) {
            keys.add(id.displayNameKey());
        }
        assertKeysPresent("FactionChoiceScreen", keys);
    }

    @Test
    @DisplayName("PassportScreen translation keys exist in both lang files")
    void passportScreenKeysExist() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("wp.passport.title");
        keys.add("wp.passport.id");
        keys.add("wp.passport.faction");
        keys.add("wp.passport.name");
        keys.add("wp.passport.dob");
        keys.add("wp.passport.status");
        keys.add("wp.passport.accepted");
        keys.add("wp.passport.accepted_by");
        keys.add("wp.passport.trophy");
        // PassportScreen renders the faction via FactionId.displayNameKey()
        // and the status via "wp.player_state." + state.getSerializedName().
        for (FactionId id : FactionId.values()) {
            keys.add(id.displayNameKey());
        }
        for (PlayerState state : PlayerState.values()) {
            keys.add("wp.player_state." + state.getSerializedName());
        }
        assertKeysPresent("PassportScreen", keys);
    }

    @Test
    @DisplayName("RadialMenuScreen translation keys exist in both lang files")
    void radialMenuScreenKeysExist() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("wp.radial.title");
        keys.add("wp.radial.no_actions");
        keys.add("wp.radial.unknown_name");
        keys.add("wp.collab.tag");
        // Every visible item the server may put in the payload.
        for (RadialMenuItem item : RadialMenuItem.values()) {
            String suffix = item.name().toLowerCase(Locale.ROOT);
            keys.add("wp.radial.item." + suffix);
            keys.add("wp.radial.item." + suffix + ".hint");
        }
        // Status/faction labels rendered on the central card.
        for (FactionId id : FactionId.values()) {
            keys.add(id.displayNameKey());
        }
        for (PlayerState state : PlayerState.values()) {
            keys.add("wp.player_state." + state.getSerializedName());
        }
        assertKeysPresent("RadialMenuScreen", keys);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void assertKeysPresent(String screenName, Set<String> keys) {
        Set<String> missingFromEn = new TreeSet<>();
        Set<String> missingFromRu = new TreeSet<>();
        for (String key : keys) {
            if (!enUs.has(key)) {
                missingFromEn.add(key);
            }
            if (!ruRu.has(key)) {
                missingFromRu.add(key);
            }
        }
        assertThat(missingFromEn)
                .as("%s references translation keys that are missing from en_us.json", screenName)
                .isEmpty();
        assertThat(missingFromRu)
                .as("%s references translation keys that are missing from ru_ru.json", screenName)
                .isEmpty();
    }

    private static JsonObject readLangResource(String classpathPath) throws IOException {
        ClassLoader loader = UiSmokeTest.class.getClassLoader();
        try (InputStream is = loader.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Resource not found on test classpath: " + classpathPath);
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
