package com.frostlogic.warproject.server.award;

import com.frostlogic.warproject.WpConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents an automatic award trigger condition.
 * <p>
 * Each trigger maps a trigger type (e.g. "CAPTURE_SUCCESS") to an award name
 * that should be granted when the condition is met. Triggers are parsed from
 * the {@link WpConfig#AWARDS_AUTO_TRIGGERS} configuration list.
 * <p>
 * Config format: "TRIGGER_TYPE:Award Name"
 * Example: "CAPTURE_SUCCESS:Награда за взятие в плен врага!"
 * <p>
 * Requirements: 15.1, 15.2
 */
public final class AwardTrigger {

    /** The primary trigger type fired when a player captures an enemy's passport. */
    public static final String CAPTURE_SUCCESS = "CAPTURE_SUCCESS";

    private final String triggerType;
    private final String awardName;

    public AwardTrigger(String triggerType, String awardName) {
        this.triggerType = triggerType;
        this.awardName = awardName;
    }

    /**
     * Returns the trigger type identifier (e.g. "CAPTURE_SUCCESS").
     */
    public String triggerType() {
        return triggerType;
    }

    /**
     * Returns the award name to grant when this trigger fires.
     */
    public String awardName() {
        return awardName;
    }

    /**
     * Checks if this trigger matches the given trigger type.
     *
     * @param type the trigger type to check against
     * @return {@code true} if this trigger should fire for the given type
     */
    public boolean matches(String type) {
        return triggerType.equals(type);
    }

    /**
     * Parses the configured auto-trigger list from {@link WpConfig#AWARDS_AUTO_TRIGGERS}.
     * Each entry must be in the format "TRIGGER_TYPE:Award Name".
     * Entries that do not contain a colon separator are skipped.
     *
     * @return an unmodifiable list of parsed triggers
     */
    public static List<AwardTrigger> loadFromConfig() {
        List<? extends String> raw = WpConfig.AWARDS_AUTO_TRIGGERS.get();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<AwardTrigger> triggers = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int sep = entry.indexOf(':');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            String type = entry.substring(0, sep).trim();
            String award = entry.substring(sep + 1).trim();
            if (!type.isEmpty() && !award.isEmpty()) {
                triggers.add(new AwardTrigger(type, award));
            }
        }
        return Collections.unmodifiableList(triggers);
    }

    /**
     * Finds the first trigger matching the given type from the configured list.
     *
     * @param triggerType the trigger type to search for
     * @return an optional containing the matching trigger, or empty if none found
     */
    public static Optional<AwardTrigger> findByType(String triggerType) {
        return loadFromConfig().stream()
                .filter(t -> t.matches(triggerType))
                .findFirst();
    }

    @Override
    public String toString() {
        return "AwardTrigger{" + triggerType + ":" + awardName + "}";
    }
}
