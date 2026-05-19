package com.frostlogic.warproject.server;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public enum Country {
    ZARNAVIA("zarnavia", "Зарнавия"),
    CHERNOGRYAD("chernogryad", "Черногрядь"),
    NEUTRAL("neutral", "Нейтральная страна"),
    UNKNOWN("unknown", "—");

    private final String id;
    private final String displayName;

    Country(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<Country> fromInput(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (Country country : values()) {
            if (country.id.equals(norm)) {
                return Optional.of(country);
            }
        }
        // Legacy ids carried over from the pre-rebrand profiles.
        return switch (norm) {
            case "mednopolye" -> Optional.of(ZARNAVIA);
            case "zlatoberezhye" -> Optional.of(CHERNOGRYAD);
            default -> Optional.empty();
        };
    }

    public static Country fromIdOrUnknown(String raw) {
        return fromInput(raw).orElse(UNKNOWN);
    }

    public static Country generateFor(Faction faction) {
        if (faction == null || !faction.isPlayable()) {
            return NEUTRAL;
        }
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 70) {
            return faction == Faction.ZARNAVIA ? ZARNAVIA : CHERNOGRYAD;
        } else if (roll < 90) {
            return NEUTRAL;
        } else {
            return faction == Faction.ZARNAVIA ? CHERNOGRYAD : ZARNAVIA;
        }
    }

    public static int generateAge() {
        return ThreadLocalRandom.current().nextInt(18, 51);
    }
}
