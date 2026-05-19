package com.frostlogic.warproject.server;

import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Faction {
    NONE("none", "Без стороны", "--", ChatFormatting.GRAY),
    ZARNAVIA("zarnavia", "Зарнавия", "ЗАР", ChatFormatting.GREEN),
    CHERNOGRYAD("chernogryad", "Черногрядь", "ЧР", ChatFormatting.BLACK);

    private final String id;
    private final String displayName;
    private final String shortName;
    private final ChatFormatting color;

    Faction(String id, String displayName, String shortName, ChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String shortName() {
        return shortName;
    }

    public ChatFormatting color() {
        return color;
    }

    public boolean isPlayable() {
        return this != NONE;
    }

    public static String[] playableIds() {
        return Arrays.stream(values())
                .filter(Faction::isPlayable)
                .map(Faction::id)
                .toArray(String[]::new);
    }

    public static Optional<Faction> fromInput(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (Faction faction : values()) {
            String display = faction.displayName.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (faction.id.equals(normalized) || display.equals(normalized)) {
                return Optional.of(faction);
            }
        }

        return switch (normalized) {
            // Primary aliases for the current factions.
            case "zar", "zarn", "зар", "зарнавия", "зарнавие" -> Optional.of(ZARNAVIA);
            case "chern", "chrn", "чер", "черногрядь", "черногрядие", "черно" -> Optional.of(CHERNOGRYAD);
            // Legacy aliases preserved so old players.json / PassportData rows
            // referencing the pre-rebrand ids still load. Once any profile is
            // re-saved it will use the new id.
            case "mednopolye", "med", "mednopol", "мед", "меднополье", "меднополие" -> Optional.of(ZARNAVIA);
            case "zlatoberezhye", "zlat", "zlato", "zlatobereg", "злат", "златобережье", "златобережие" -> Optional.of(CHERNOGRYAD);
            case "none", "no", "нет", "без" -> Optional.of(NONE);
            default -> Optional.empty();
        };
    }
}
