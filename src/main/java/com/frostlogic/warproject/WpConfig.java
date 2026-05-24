package com.frostlogic.warproject;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server-side configuration for WarProject mod.
 * Uses NeoForge {@link ModConfigSpec} (server config type).
 * <p>
 * Sections: auth, captcha, candidate, regions, factions, ranks, captivity, ransom,
 * roles, radial, db, audit, collaborator, map, events, diplomacy, awards.
 * <p>
 * Defaults follow §16 of the design document and Requirement 23 assumptions.
 */
public final class WpConfig {

    public static final ModConfigSpec SPEC;

    // --- auth.password ---
    public static final ModConfigSpec.IntValue AUTH_PASSWORD_MIN_LEN;
    public static final ModConfigSpec.IntValue AUTH_PASSWORD_MAX_LEN;
    public static final ModConfigSpec.ConfigValue<String> AUTH_PASSWORD_ALLOWED_CHARS;

    // --- auth.login ---
    public static final ModConfigSpec.IntValue AUTH_LOGIN_ATTEMPTS;

    // --- captcha ---
    public static final ModConfigSpec.IntValue CAPTCHA_SPAWN_X;
    public static final ModConfigSpec.IntValue CAPTCHA_SPAWN_Y;
    public static final ModConfigSpec.IntValue CAPTCHA_SPAWN_Z;
    public static final ModConfigSpec.IntValue CAPTCHA_CODE_LEN;
    public static final ModConfigSpec.ConfigValue<String> CAPTCHA_ALPHABET;
    public static final ModConfigSpec.BooleanValue CAPTCHA_CASE_SENSITIVE;
    public static final ModConfigSpec.IntValue CAPTCHA_ATTEMPTS;
    public static final ModConfigSpec.IntValue CAPTCHA_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue CAPTCHA_LOGIN_COOLDOWN_SECONDS;

    // --- candidate ---
    public static final ModConfigSpec.IntValue CANDIDATE_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue CANDIDATE_ACTIONBAR_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CANDIDATE_EXTRA_DENIES;

    // --- regions ---
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGIONS_BASES;
    public static final ModConfigSpec.IntValue REGIONS_TICK_INTERVAL_TICKS;

    // --- factions ---
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> FACTIONS_ZARNAVIA_SPAWN;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> FACTIONS_CHERNOGRYAD_SPAWN;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> FACTIONS_CHOICE_HALL_SPAWN;

    // --- ranks ---
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANKS_ZARNAVIA;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANKS_CHERNOGRYAD;

    // --- captivity ---
    public static final ModConfigSpec.ConfigValue<String> CAPTIVITY_VULNERABILITY;

    // --- ransom ---
    public static final ModConfigSpec.ConfigValue<String> RANSOM_ECONOMY;
    public static final ModConfigSpec.ConfigValue<String> RANSOM_MODE;

    // --- roles ---
    public static final ModConfigSpec.ConfigValue<String> ROLES_PROVIDER;

    // --- radial ---
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RADIAL_MENU_ITEMS;

    // --- db ---
    public static final ModConfigSpec.ConfigValue<String> DB_DRIVER;
    public static final ModConfigSpec.ConfigValue<String> DB_JDBC_URL;
    public static final ModConfigSpec.ConfigValue<String> DB_USERNAME;
    public static final ModConfigSpec.ConfigValue<String> DB_PASSWORD;

    // --- audit ---
    public static final ModConfigSpec.ConfigValue<String> AUDIT_NOTIFIER;

    // --- collaborator ---
    public static final ModConfigSpec.IntValue COLLABORATOR_THRESHOLD_TICKS;

    // --- events ---
    public static final ModConfigSpec.IntValue EVENTS_MAX_CONCURRENT;
    public static final ModConfigSpec.IntValue EVENTS_MAX_NAME_LENGTH;

    // --- diplomacy ---
    public static final ModConfigSpec.IntValue DIPLOMACY_MAX_TRUCE_DURATION;
    public static final ModConfigSpec.IntValue DIPLOMACY_MIN_TRUCE_DURATION;
    public static final ModConfigSpec.IntValue DIPLOMACY_TRUCE_COOLDOWN;

    // --- awards ---
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AWARDS_AUTO_TRIGGERS;
    public static final ModConfigSpec.IntValue AWARDS_MAX_PER_PLAYER;

    // --- chat ---
    public static final ModConfigSpec.IntValue CHAT_LOCAL_RADIUS;

    // --- transport NPCs ---
    public static final ModConfigSpec.BooleanValue TRANSPORT_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TRANSPORT_VEHICLES_ZARNAVIA;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TRANSPORT_VEHICLES_CHERNOGRYAD;

    // --- wguard (anti-cheat) ---
    public static final ModConfigSpec.BooleanValue WGUARD_ENABLED;
    public static final ModConfigSpec.IntValue WGUARD_SPEED_MAX_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue WGUARD_FLY_VIOLATION_THRESHOLD;
    public static final ModConfigSpec.IntValue WGUARD_REACH_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue WGUARD_KILLAURA_MAX_ATTACKS_PER_SECOND;
    public static final ModConfigSpec.IntValue WGUARD_KILLAURA_MAX_ROTATION_DEGREES_PER_TICK;
    public static final ModConfigSpec.IntValue WGUARD_FASTBREAK_MIN_TICKS;
    public static final ModConfigSpec.IntValue WGUARD_VIOLATION_KICK_THRESHOLD;
    public static final ModConfigSpec.IntValue WGUARD_VIOLATION_BAN_THRESHOLD;
    public static final ModConfigSpec.IntValue WGUARD_VIOLATION_DECAY_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WGUARD_VEHICLE_MOD_NAMESPACES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WGUARD_WEAPON_MOD_NAMESPACES;
    public static final ModConfigSpec.IntValue WGUARD_VEHICLE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue WGUARD_SKIP_RIDING_PLAYERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // ========== auth ==========
        builder.push("auth");
        builder.push("password");

        AUTH_PASSWORD_MIN_LEN = builder
                .comment("Minimum password length")
                .defineInRange("minLen", 6, 1, 128);

        AUTH_PASSWORD_MAX_LEN = builder
                .comment("Maximum password length")
                .defineInRange("maxLen", 32, 1, 128);

        AUTH_PASSWORD_ALLOWED_CHARS = builder
                .comment("Regex character class for allowed password characters")
                .define("allowedChars", "[A-Za-z0-9!@#$%^&*()_\\-+=]");

        builder.pop(); // password

        builder.push("login");

        AUTH_LOGIN_ATTEMPTS = builder
                .comment("Maximum consecutive failed login attempts before cooldown + kick (default: 5)")
                .defineInRange("attempts", 5, 1, 50);

        builder.pop(2); // login, auth

        // ========== captcha ==========
        builder.push("captcha");

        CAPTCHA_SPAWN_X = builder
                .comment("X coordinate of captcha spawn point")
                .defineInRange("spawnX", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

        CAPTCHA_SPAWN_Y = builder
                .comment("Y coordinate of captcha spawn point (default: 320, sky)")
                .defineInRange("spawnY", 320, -64, 1024);

        CAPTCHA_SPAWN_Z = builder
                .comment("Z coordinate of captcha spawn point")
                .defineInRange("spawnZ", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

        CAPTCHA_CODE_LEN = builder
                .comment("Length of captcha code")
                .defineInRange("codeLen", 6, 3, 16);

        CAPTCHA_ALPHABET = builder
                .comment("Characters used to generate captcha code")
                .define("alphabet", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

        CAPTCHA_CASE_SENSITIVE = builder
                .comment("Whether captcha verification is case-sensitive")
                .define("caseSensitive", false);

        CAPTCHA_ATTEMPTS = builder
                .comment("Maximum captcha attempts before kick")
                .defineInRange("attempts", 3, 1, 10);

        CAPTCHA_TIMEOUT_SECONDS = builder
                .comment("Captcha timeout in seconds")
                .defineInRange("timeoutSeconds", 180, 30, 600);

        CAPTCHA_LOGIN_COOLDOWN_SECONDS = builder
                .comment("Cooldown in seconds after failed captcha before player can reconnect")
                .defineInRange("loginCooldownSeconds", 90, 10, 600);

        builder.pop(); // captcha

        // ========== candidate ==========
        builder.push("candidate");

        CANDIDATE_TIMEOUT_SECONDS = builder
                .comment("Candidate timeout in seconds of active session (default: 20m = 1200; raise to 86400 for 24h)")
                .defineInRange("timeoutSeconds", 1200, 60, 604800);

        CANDIDATE_ACTIONBAR_INTERVAL_TICKS = builder
                .comment("Interval in ticks between actionbar reminders for candidates (default: 600 = ~30s)")
                .defineInRange("actionbarIntervalTicks", 600, 20, 6000);

        CANDIDATE_EXTRA_DENIES = builder
                .comment("Additional deny rules for candidates (list of tag/action identifiers)")
                .defineListAllowEmpty("extraDenies", List.of(), WpConfig::validateString);

        builder.pop(); // candidate

        // ========== regions ==========
        builder.push("regions");

        REGIONS_BASES = builder
                .comment(
                        "List of base region definitions.",
                        "Format: 'faction;dimension;minX,minY,minZ;maxX,maxY,maxZ'",
                        "Example: 'ZARNAVIA;minecraft:overworld;100,60,100;200,120,200'"
                )
                .defineListAllowEmpty("bases", List.of(), WpConfig::validateString);

        REGIONS_TICK_INTERVAL_TICKS = builder
                .comment("Interval in ticks between region cache updates on PlayerTickEvent (default: 10)")
                .defineInRange("tickIntervalTicks", 10, 1, 200);

        builder.pop(); // regions

        // ========== factions ==========
        builder.push("factions");

        FACTIONS_ZARNAVIA_SPAWN = builder
                .comment("Spawn coordinates [x, y, z] for Zarnavia base")
                .defineListAllowEmpty("zarnaviaSpawn", List.of(0, 64, 0), WpConfig::validateInteger);

        FACTIONS_CHERNOGRYAD_SPAWN = builder
                .comment("Spawn coordinates [x, y, z] for Chernogryad base")
                .defineListAllowEmpty("chernogryadSpawn", List.of(0, 64, 0), WpConfig::validateInteger);

        FACTIONS_CHOICE_HALL_SPAWN = builder
                .comment("Spawn coordinates [x, y, z] for the faction choice hall")
                .defineListAllowEmpty("choiceHallSpawn", List.of(0, 64, 0), WpConfig::validateInteger);

        builder.pop(); // factions

        // ========== ranks ==========
        builder.push("ranks");

        RANKS_ZARNAVIA = builder
                .comment(
                        "Ordered list of ranks for Zarnavia faction (lowest to highest).",
                        "Format: 'role:rankName' where role is SOLDIER, COMMANDER, or GENERAL"
                )
                .defineListAllowEmpty("zarnavia", defaultRanks(), WpConfig::validateString);

        RANKS_CHERNOGRYAD = builder
                .comment(
                        "Ordered list of ranks for Chernogryad faction (lowest to highest).",
                        "Format: 'role:rankName' where role is SOLDIER, COMMANDER, or GENERAL"
                )
                .defineListAllowEmpty("chernogryad", defaultRanks(), WpConfig::validateString);

        builder.pop(); // ranks

        // ========== captivity ==========
        builder.push("captivity");

        CAPTIVITY_VULNERABILITY = builder
                .comment(
                        "Vulnerability predicate for passport capture.",
                        "Predicate name or expression. Default: HP_BELOW_HALF"
                )
                .define("vulnerability", "HP_BELOW_HALF");

        builder.pop(); // captivity

        // ========== ransom ==========
        builder.push("ransom");

        RANSOM_ECONOMY = builder
                .comment("Economy integration for ransom. Options: NONE, NUMISMATIC, WP_INTERNAL")
                .define("economy", "NONE");

        RANSOM_MODE = builder
                .comment("Ransom mode. Options: MANUAL, AUTOMATIC")
                .define("mode", "MANUAL");

        builder.pop(); // ransom

        // ========== roles ==========
        builder.push("roles");

        ROLES_PROVIDER = builder
                .comment("Role provider. Options: INTERNAL, LUCKPERMS_FALLBACK")
                .define("provider", "INTERNAL");

        builder.pop(); // roles

        // ========== radial ==========
        builder.push("radial");

        RADIAL_MENU_ITEMS = builder
                .comment(
                        "Enabled radial menu items.",
                        "Options: ACCEPT, CAPTURE_PASSPORT, PROMOTE, DEMOTE, COLLAB_MARK, COLLAB_UNMARK,",
                        "         RANSOM_OPEN, SUBDIV_INVITE, SUBDIV_KICK"
                )
                .defineListAllowEmpty("menuItems", List.of(
                        "ACCEPT",
                        "CAPTURE_PASSPORT",
                        "PROMOTE",
                        "DEMOTE",
                        "COLLAB_MARK",
                        "COLLAB_UNMARK"
                ), WpConfig::validateString);

        builder.pop(); // radial

        // ========== db ==========
        builder.push("db");

        DB_DRIVER = builder
                .comment("Database driver. Options: sqlite, mysql")
                .define("driver", "sqlite");

        DB_JDBC_URL = builder
                .comment("JDBC connection URL")
                .define("jdbcUrl", "jdbc:sqlite:warproject.db");

        DB_USERNAME = builder
                .comment("Database username (used for MySQL)")
                .define("username", "");

        DB_PASSWORD = builder
                .comment("Database password (used for MySQL)")
                .define("password", "");

        builder.pop(); // db

        // ========== audit ==========
        builder.push("audit");

        AUDIT_NOTIFIER = builder
                .comment("Audit notifier implementation. Options: NONE, DISCORD")
                .define("notifier", "NONE");

        builder.pop(); // audit

        // ========== collaborator ==========
        builder.push("collaborator");

        COLLABORATOR_THRESHOLD_TICKS = builder
                .comment("Ticks of continuous presence in enemy region before auto-collaborator mark (default: 72000 = 1 hour at 20tps)")
                .defineInRange("thresholdTicks", 72000, 1200, 1728000);

        builder.pop(); // collaborator

        // ========== events ==========
        builder.push("events");

        EVENTS_MAX_CONCURRENT = builder
                .defineInRange("maxConcurrent", 10, 1, 50);

        EVENTS_MAX_NAME_LENGTH = builder
                .defineInRange("maxNameLength", 64, 8, 128);

        builder.pop(); // events

        // ========== diplomacy ==========
        builder.push("diplomacy");

        DIPLOMACY_MAX_TRUCE_DURATION = builder
                .defineInRange("maxTruceDuration", 86400, 3600, 604800);

        DIPLOMACY_MIN_TRUCE_DURATION = builder
                .defineInRange("minTruceDuration", 300, 60, 3600);

        DIPLOMACY_TRUCE_COOLDOWN = builder
                .defineInRange("truceCooldown", 43200, 0, 604800);

        builder.pop(); // diplomacy

        // ========== awards ==========
        builder.push("awards");

        AWARDS_AUTO_TRIGGERS = builder
                .defineListAllowEmpty("autoTriggers", List.of(), WpConfig::validateString);

        AWARDS_MAX_PER_PLAYER = builder
                .defineInRange("maxPerPlayer", 100, 1, 1000);

        builder.pop(); // awards

        // ========== chat ==========
        builder.push("chat");

        CHAT_LOCAL_RADIUS = builder
                .comment("Radius in blocks for local (proximity) chat. Players outside this radius cannot hear the message.")
                .defineInRange("localRadius", 50, 10, 1000);

        builder.pop(); // chat

        // ========== wguard (anti-cheat) ==========
        builder.push("wguard");

        WGUARD_ENABLED = builder
                .comment("Enable WGuard anti-cheat system")
                .define("enabled", true);

        WGUARD_SPEED_MAX_BLOCKS_PER_TICK = builder
                .comment("Maximum allowed horizontal movement per tick in blocks. Vanilla max: ~10 (sprinting + jumping). Default: 12 to allow some latency tolerance.")
                .defineInRange("speedMaxBlocksPerTick", 12, 5, 50);

        WGUARD_FLY_VIOLATION_THRESHOLD = builder
                .comment("Number of consecutive fly violations before action. Each violation = 1 tick of airborne movement without flight permission.")
                .defineInRange("flyViolationThreshold", 5, 1, 50);

        WGUARD_REACH_MAX_DISTANCE = builder
                .comment("Maximum allowed attack/interact distance in blocks. Vanilla: ~6.0 for creative, ~4.5 for survival. Default: 5.5 to allow some latency tolerance.")
                .defineInRange("reachMaxDistance", 55, 30, 100);

        WGUARD_KILLAURA_MAX_ATTACKS_PER_SECOND = builder
                .comment("Maximum attacks per second before KillAura flag. Vanilla max for auto-clicker: ~16 (game tick rate). Default: 12.")
                .defineInRange("killAuraMaxAttacksPerSecond", 12, 4, 30);

        WGUARD_KILLAURA_MAX_ROTATION_DEGREES_PER_TICK = builder
                .comment("Maximum head rotation degrees per tick before KillAura flag. Normal play: ~180°/sec = 9°/tick. Default: 90 to allow fast flicks.")
                .defineInRange("killAuraMaxRotationPerTick", 90, 20, 360);

        WGUARD_FASTBREAK_MIN_TICKS = builder
                .comment("Minimum ticks between breaking two different blocks. Vanilla: instant for some tools. Default: 2 to catch nuker patterns.")
                .defineInRange("fastBreakMinTicks", 2, 1, 20);

        WGUARD_VIOLATION_KICK_THRESHOLD = builder
                .comment("Total violation level at which the player is kicked. Violations decay over time.")
                .defineInRange("violationKickThreshold", 50, 10, 500);

        WGUARD_VIOLATION_BAN_THRESHOLD = builder
                .comment("Total violation level at which the player is banned. Violations decay over time.")
                .defineInRange("violationBanThreshold", 150, 20, 1000);

        WGUARD_VIOLATION_DECAY_TICKS = builder
                .comment("Ticks between violation level decay steps. Each step reduces total VL by 1.")
                .defineInRange("violationDecayTicks", 100, 20, 6000);

        WGUARD_VEHICLE_MOD_NAMESPACES = builder
                .comment(
                        "Mod namespace prefixes for vehicle mods (planes, cars, tanks, etc.).",
                        "Players riding entities from these mods are exempt from SPEED/FLY/NO_FALL checks.",
                        "Examples: immersive_aircraft, vs_eureka, flan, create, valkyrieskies, mcwplanes"
                )
                .defineListAllowEmpty("vehicleModNamespaces", List.of(
                        "immersive_aircraft", "vs_eureka", "flan", "create",
                        "valkyrieskies", "mcwplanes", "mcheli", "simpleplanes",
                        "aircraft", "vcraft", "vz"
                ), WpConfig::validateString);

        WGUARD_WEAPON_MOD_NAMESPACES = builder
                .comment(
                        "Mod namespace prefixes for weapon mods (guns, rifles, turrets, etc.).",
                        "Players holding items from these mods get relaxed KILL_AURA checks",
                        "(automatic weapons fire faster than melee).",
                        "Examples: tacz, vic3, cmg, timeless_and_classics, flan, gun"
                )
                .defineListAllowEmpty("weaponModNamespaces", List.of(
                        "tacz", "vic3", "cmg", "timeless_and_classics",
                        "flan", "gun", "cgm", "scorched_guns", "dtt"
                ), WpConfig::validateString);

        WGUARD_VEHICLE_SPEED_MULTIPLIER = builder
                .comment("Speed limit multiplier when riding a vehicle from a known mod. Default: 10 (allows planes/cars).")
                .defineInRange("vehicleSpeedMultiplier", 10, 1, 100);

        WGUARD_SKIP_RIDING_PLAYERS = builder
                .comment("If true, completely skip SPEED/FLY/NO_FALL checks for players riding any entity (not just whitelisted mods). Recommended true for servers with vehicle mods.")
                .define("skipRidingPlayers", true);

        builder.pop(); // wguard

        // ========== transport (vehicle requisition NPCs) ==========
        builder.push("transport");

        TRANSPORT_ENABLED = builder
                .comment("Master toggle for the transport-technician NPCs at both faction bases.")
                .define("enabled", true);

        TRANSPORT_VEHICLES_ZARNAVIA = builder
                .comment(
                        "Vehicles offered by the Zarnavia technician.",
                        "Format per entry: 'min_rank|item_id|display_translation_key'",
                        "  min_rank        — lowest rank that can claim (none, private, corporal, sergeant, ...).",
                        "  item_id         — registry id of the vehicle item from any vehicle mod (e.g. iv:tiger_2).",
                        "  display_key     — translation key shown in the menu.",
                        "Pipe separator is used because item ids contain colons.",
                        "If the mod that registers item_id is not installed, the entry shows the barrier icon."
                )
                .defineListAllowEmpty("vehiclesZarnavia", List.of(
                        "private|iv:tiger_2|wp.transport.vehicle.tiger_2",
                        "sergeant|iv:btr_80a|wp.transport.vehicle.btr_80a",
                        "lieutenant|iv:t_72b3|wp.transport.vehicle.t_72b3"
                ), WpConfig::validateString);

        TRANSPORT_VEHICLES_CHERNOGRYAD = builder
                .comment("Vehicles offered by the Chernogryad technician. Same format as vehiclesZarnavia.")
                .defineListAllowEmpty("vehiclesChernogryad", List.of(
                        "private|iv:tiger_2|wp.transport.vehicle.tiger_2",
                        "sergeant|iv:btr_80a|wp.transport.vehicle.btr_80a",
                        "lieutenant|iv:t_72b3|wp.transport.vehicle.t_72b3"
                ), WpConfig::validateString);

        builder.pop(); // transport

        SPEC = builder.build();
    }

    private WpConfig() {
        // utility class
    }

    /**
     * Reloads the configuration from disk without resetting the DB connection.
     * Called by {@code /wp reload} command.
     */
    public static void reload() {
        SPEC.afterReload();
    }

    // --- Validation helpers ---

    private static boolean validateString(Object obj) {
        return obj instanceof String;
    }

    private static boolean validateInteger(Object obj) {
        return obj instanceof Integer;
    }

    // --- Default rank lists ---

    private static List<String> defaultRanks() {
        return List.of(
                "SOLDIER:рядовой",
                "SOLDIER:ефрейтор",
                "SOLDIER:мл. сержант",
                "SOLDIER:сержант",
                "SOLDIER:ст. сержант",
                "SOLDIER:старшина",
                "COMMANDER:лейтенант",
                "COMMANDER:ст. лейтенант",
                "COMMANDER:капитан",
                "COMMANDER:майор",
                "COMMANDER:подполковник",
                "COMMANDER:полковник",
                "GENERAL:генерал-майор",
                "GENERAL:генерал-лейтенант",
                "GENERAL:генерал"
        );
    }
}
