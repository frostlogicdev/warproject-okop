package com.frostlogic.warproject.server;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Rank {
    NONE("none", "Без звания", "--", false, 0),
    PRIVATE("private", "Рядовой", "Ряд.", false, 1),
    CORPORAL("corporal", "Ефрейтор", "Ефр.", false, 2),
    SERGEANT("sergeant", "Сержант", "Сж.", true, 3),
    SENIOR_SERGEANT("senior_sergeant", "Старший сержант", "Ст.сж.", true, 4),
    LIEUTENANT("lieutenant", "Лейтенант", "Лт.", true, 5),
    SENIOR_LIEUTENANT("senior_lieutenant", "Старший лейтенант", "Ст.лт.", true, 6),
    CAPTAIN("captain", "Капитан", "Кап.", true, 7),
    MAJOR("major", "Майор", "Майор", true, 8),
    COLONEL("colonel", "Полковник", "Полк.", true, 9),
    GENERAL("general", "Генерал", "Ген.", true, 10);

    private final String id;
    private final String displayName;
    private final String shortName;
    private final boolean commander;
    private final int weight;

    Rank(String id, String displayName, String shortName, boolean commander, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.commander = commander;
        this.weight = weight;
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

    public boolean isCommander() {
        return commander;
    }

    public int weight() {
        return weight;
    }

    public boolean isAssignable() {
        return this != NONE;
    }

    public static String[] assignableIds() {
        return Arrays.stream(values())
                .filter(Rank::isAssignable)
                .map(Rank::id)
                .toArray(String[]::new);
    }

    public static Optional<Rank> fromInput(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (Rank rank : values()) {
            String display = rank.displayName.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (rank.id.equals(normalized) || display.equals(normalized)) {
                return Optional.of(rank);
            }
        }

        return switch (normalized) {
            case "рядовой", "ryadovoy", "soldier" -> Optional.of(PRIVATE);
            case "ефрейтор", "efreitor", "corporal" -> Optional.of(CORPORAL);
            case "сержант", "serzhant", "sgt" -> Optional.of(SERGEANT);
            case "старший_сержант", "старший-сержант", "старший сержант", "senior-sergeant" -> Optional.of(SENIOR_SERGEANT);
            case "лейтенант", "leytenant", "lt" -> Optional.of(LIEUTENANT);
            case "старший_лейтенант", "старший-лейтенант", "старший лейтенант", "senior-lieutenant" -> Optional.of(SENIOR_LIEUTENANT);
            case "капитан", "kapitan" -> Optional.of(CAPTAIN);
            case "майор", "mayor" -> Optional.of(MAJOR);
            case "полковник", "polkovnik" -> Optional.of(COLONEL);
            case "генерал", "general" -> Optional.of(GENERAL);
            case "none", "нет", "без" -> Optional.of(NONE);
            default -> Optional.empty();
        };
    }
}
