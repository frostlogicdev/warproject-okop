package com.frostlogic.warproject.localization;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Localization key coverage test (Property 26).
 *
 * <p>Verifies that:
 * <ol>
 *   <li>Both {@code ru_ru.json} and {@code en_us.json} parse as valid JSON objects.</li>
 *   <li>{@code keySet(ru_ru) == keySet(en_us)} — both files declare exactly the same keys
 *       (parity check, per Property 26 in design §12).</li>
 *   <li>A core set of keys actually used by the mod is present in both files
 *       ({@code wp.error.client_mod_required}, {@code wp.faction.zarnavia}, etc.).</li>
 * </ol>
 *
 * <p>The full constant-pool reflection variant of Property 26 is reserved for a follow-up
 * jqwik task; this test is the deterministic safety net invoked on every build.
 *
 * <p><b>Validates: Requirements 19.1, 19.2</b>
 * <p>Design: §12 Property 26
 */
class LangCoverageTest {

    private static final String RU_RU_PATH = "assets/warproject/lang/ru_ru.json";
    private static final String EN_US_PATH = "assets/warproject/lang/en_us.json";

    private static JsonObject ruRu;
    private static JsonObject enUs;

    @BeforeAll
    static void loadLangFiles() throws IOException {
        ruRu = readLangResource(RU_RU_PATH);
        enUs = readLangResource(EN_US_PATH);
    }

    // ---------------------------------------------------------------------
    // Sanity
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("ru_ru.json is a non-empty JSON object")
    void ruRuLoadsAsJsonObject() {
        assertThat(ruRu).isNotNull();
        assertThat(ruRu.size())
                .as("ru_ru.json must declare at least one translation key")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("en_us.json is a non-empty JSON object")
    void enUsLoadsAsJsonObject() {
        assertThat(enUs).isNotNull();
        assertThat(enUs.size())
                .as("en_us.json must declare at least one translation key")
                .isGreaterThan(0);
    }

    // ---------------------------------------------------------------------
    // Property 26: keySet parity
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Property 26: keySet(ru_ru) == keySet(en_us)")
    void langFilesHaveIdenticalKeySets() {
        Set<String> ruKeys = new TreeSet<>(ruRu.keySet());
        Set<String> enKeys = new TreeSet<>(enUs.keySet());

        Set<String> onlyInRu = new TreeSet<>(ruKeys);
        onlyInRu.removeAll(enKeys);

        Set<String> onlyInEn = new TreeSet<>(enKeys);
        onlyInEn.removeAll(ruKeys);

        assertThat(onlyInRu)
                .as("Keys present in ru_ru.json but missing from en_us.json")
                .isEmpty();
        assertThat(onlyInEn)
                .as("Keys present in en_us.json but missing from ru_ru.json")
                .isEmpty();

        // Final, redundant cross-check — guards against future refactors that would
        // skip set-difference comparisons.
        assertThat(ruKeys).isEqualTo(enKeys);
    }

    @Test
    @DisplayName("Every translation value is a non-empty string")
    void translationValuesAreNonEmptyStrings() {
        assertAllValuesNonEmptyStrings(ruRu, "ru_ru.json");
        assertAllValuesNonEmptyStrings(enUs, "en_us.json");
    }

    // ---------------------------------------------------------------------
    // Property 26: presence of core translation keys
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Core translation keys are present in both lang files")
    void coreKeysArePresentInBothFiles() {
        // Keys explicitly called out by task 20.5 plus a small set sampled from the
        // mandatory namespaces enumerated in design §10 / requirements 19.x.
        String[] coreKeys = {
                // Disconnect message for clients without the mod (Req. 4.4).
                "wp.error.client_mod_required",

                // Faction display names (Req. 6.x).
                "wp.faction.zarnavia",
                "wp.faction.chernogryad",

                // Candidate actionbar/timeout messaging (Req. 7.6, 7.7, 7.8).
                "wp.candidate.actionbar",
                "wp.candidate.timeout",
                "wp.candidate.cannot_leave_base",

                // GeneralChat (Req. 15.x).
                "wp.gc.line",
                "wp.gc.cooldown",
                "wp.gc.no_recipients",

                // Collaborator marker (Req. 11.2).
                "wp.collab.tag",

                // Passport item display name (Req. 12.3).
                "item.warproject.passport",

                // Radial menu shell (Req. 9.x).
                "wp.radial.title",
                "wp.radial.no_actions",

                // Core moderation feedback (Req. 10.2, 10.5).
                "wp.command.ban.success",
                "wp.command.kick.success",
                "wp.command.reload.success",

                // Subdivision flow (Req. 16.x).
                "wp.command.subdivision.create.success",
                "wp.command.subdivision.delete.success",

                // Generic error fallbacks (Req. 21.1, 21.3).
                "wp.error.player_only",
                "wp.error.service_unavailable",
                "wp.error.internal",
        };

        for (String key : coreKeys) {
            assertThat(ruRu.has(key))
                    .as("ru_ru.json must declare core key '%s'", key)
                    .isTrue();
            assertThat(enUs.has(key))
                    .as("en_us.json must declare core key '%s'", key)
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static JsonObject readLangResource(String classpathPath) throws IOException {
        ClassLoader loader = LangCoverageTest.class.getClassLoader();
        try (InputStream is = loader.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Resource not found on test classpath: " + classpathPath);
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static void assertAllValuesNonEmptyStrings(JsonObject obj, String fileLabel) {
        for (String key : obj.keySet()) {
            var element = obj.get(key);
            assertThat(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
                    .as("%s key '%s' must map to a string", fileLabel, key)
                    .isTrue();
            String value = element.getAsString();
            assertThat(value)
                    .as("%s key '%s' must have a non-empty value", fileLabel, key)
                    .isNotEmpty();
        }
    }
}
