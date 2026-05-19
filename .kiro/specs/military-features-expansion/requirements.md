# Requirements Document

## Introduction

This document specifies requirements for five major feature additions to the War Project Minecraft mod (NeoForge 1.21.1, Java 21): a 3D Map & Minimap system, Military ID Card (Военный билет), Event System (Система ивентов), Diplomacy System, and Awards/Medals System (Система наград). These features extend the existing faction-based military gameplay with enhanced situational awareness, player identity, organized events, inter-faction diplomacy, and achievement recognition.

## Glossary

- **Map_System**: The client-side rendering subsystem responsible for displaying the 3D map overlay and minimap HUD element
- **Minimap_Widget**: The persistent HUD widget showing a small real-time map in the corner of the player's screen
- **Fullscreen_Map**: The full-screen map view opened via keybind, showing a larger area with more detail
- **Military_ID_Service**: The server-side service managing Military ID card creation, updates, and persistence
- **Military_ID_Card**: An in-game item (similar to Passport) containing faction, rank, subdivision, and awards data for an accepted faction member
- **Event_Service**: The server-side service managing event lifecycle (creation, joining, teleportation, completion)
- **Event**: A named, admin-created gameplay session with a designated spawn point that players can optionally join
- **Diplomacy_Service**: The server-side service managing truces, prisoner exchanges, and negotiations between factions
- **Truce**: A time-limited agreement between two factions that suspends hostile actions
- **Awards_Service**: The server-side service managing award definitions, granting, and persistence
- **Award**: A named visual badge granted to a player for a specific achievement, displayed on the Military ID and in UI
- **Faction_Member**: A player who has been accepted into either Zarnavia or Chernogryad faction
- **General**: A player holding the GENERAL role within a faction, authorized for diplomacy and high-level commands
- **Commander**: A player holding the COMMANDER role within a faction
- **Admin**: A server operator with permission level 2 or higher

## Requirements

### Requirement 1: Minimap HUD Rendering

**User Story:** As a faction member, I want to see a minimap in the corner of my screen, so that I can maintain situational awareness of nearby terrain, structures, and allies without opening a full map.

#### Acceptance Criteria

1. WHILE a Faction_Member is in-game, THE Map_System SHALL render the Minimap_Widget in a configurable screen corner (options: top-right, top-left, bottom-right, bottom-left; default: top-right) with a fixed size of 128×128 pixels
2. THE Minimap_Widget SHALL display terrain within a configurable radius (range: 32 to 256 blocks, default: 64 blocks) centered on the player, refreshing at the same tick interval used for allied position updates (default: every 20 ticks)
3. THE Minimap_Widget SHALL render structures and buildings as elevated shapes with height-based shading so that blocks above ground level appear visually distinct from flat terrain
4. THE Minimap_Widget SHALL display allied Faction_Member positions as colored markers matching their faction color
5. IF an enemy Faction_Member is within the minimap radius AND an unobstructed block-level raycast from the player's eye position to the enemy succeeds, THEN THE Minimap_Widget SHALL display that enemy's position as a marker in the opposing faction's color
6. IF an enemy Faction_Member is outside the minimap radius OR the raycast is obstructed, THEN THE Minimap_Widget SHALL NOT display that enemy's position
7. WHEN the player presses the minimap toggle keybind, THE Map_System SHALL hide or show the Minimap_Widget, persisting the visibility state across sessions
8. THE Minimap_Widget SHALL render faction territory boundaries as colored border lines using the owning faction's color

### Requirement 2: Fullscreen Map View

**User Story:** As a faction member, I want to open a detailed fullscreen map, so that I can plan movements, review territory, and locate objectives across a larger area.

#### Acceptance Criteria

1. WHEN the player presses the map keybind (default: M), THE Map_System SHALL toggle the Fullscreen_Map screen between open and closed states
2. WHEN the Fullscreen_Map is open and the player presses Escape or the map keybind, THE Map_System SHALL close the Fullscreen_Map and return input control to gameplay
3. THE Fullscreen_Map SHALL NOT pause the game while open
4. THE Fullscreen_Map SHALL render terrain, structures, and faction territories across a configurable view radius (default: 256 blocks) centered on the player's position
5. THE Fullscreen_Map SHALL support zoom via mouse scroll wheel with a minimum of 3 discrete zoom levels, where the closest level shows at least 64 blocks radius and the farthest level shows the full configured view radius
6. THE Fullscreen_Map SHALL render buildings and structures as 3D objects with distinct outlines distinguishable from flat terrain at all zoom levels
7. THE Fullscreen_Map SHALL display allied player positions as faction-colored markers with name labels, showing at most 50 allies simultaneously and prioritizing the nearest allies when the count exceeds 50
8. THE Fullscreen_Map SHALL display faction territory regions with colored overlays at 30-50% opacity matching faction colors
9. WHEN the player drags with the left mouse button, THE Fullscreen_Map SHALL pan the view in the corresponding direction, limited to a maximum pan distance of 2 times the current view radius from the player's position
10. THE Fullscreen_Map SHALL display the player's current block coordinates (X, Y, Z) and cardinal facing direction, updated each client frame

### Requirement 3: Map Data Caching and Performance

**User Story:** As a player, I want the map system to perform smoothly without causing lag, so that gameplay remains fluid even with map rendering active.

#### Acceptance Criteria

1. THE Map_System SHALL cache terrain chunk data client-side and update only when chunks are modified or newly loaded
2. THE Map_System SHALL render the Minimap_Widget within a frame budget of 2 milliseconds per frame, averaged over a rolling window of 100 frames
3. THE Map_System SHALL load map data asynchronously on a background thread, contributing no more than 0.5 milliseconds of synchronous work to the main client thread per frame
4. WHEN a chunk is unloaded from the client, THE Map_System SHALL retain cached map data for that chunk until the cache exceeds the configured maximum size (default: 512 chunks), evicting the least-recently-accessed chunk first
5. THE Map_System SHALL transmit allied player positions from server to client at a configurable tick interval (default: every 20 ticks), including a maximum of 100 player positions per packet
6. IF asynchronous map data loading fails for a chunk, THEN THE Map_System SHALL skip rendering that chunk on the map and retry loading on the next cache update cycle without blocking the main client thread

### Requirement 4: Military ID Card Creation

**User Story:** As a faction member, I want to receive a Military ID card when I am accepted into a faction, so that I have an official document showing my military status and information.

#### Acceptance Criteria

1. WHEN a player's faction membership is confirmed by an accepting officer (transition from CANDIDATE to full member), THE Military_ID_Service SHALL generate a Military_ID_Card item and place it in the player's inventory using the same placement algorithm as PassportPlacement (main inventory slots 9–35 first, then hotbar swap fallback)
2. THE Military_ID_Card SHALL contain the following data fields: passport ID reference (string, max 64 characters), faction (FactionId), rank (Rank ID string, max 64 characters), subdivision name (string, max 64 characters, empty string if unassigned), list of awards (list of strings, each max 64 characters, max 16 entries, initially empty), date of enlistment (string in "dd.MM.yyyy" format), and accepting officer name (string, max 64 characters)
3. THE Military_ID_Card SHALL use a DataComponentType registered via DeferredRegister with a persistent Codec and a network-synchronized StreamCodec, following the same record-based pattern as the existing PassportData in the passport package
4. THE Military_ID_Card StreamCodec SHALL encode and decode all fields using RegistryFriendlyByteBuf with a maximum of 64 characters per string field and a maximum of 16 entries for the awards list
5. IF the player's inventory is full (no free slots in main inventory, hotbar, or offhand) when the Military_ID_Card is generated, THEN THE Military_ID_Service SHALL drop the card as an item entity at the player's current position

### Requirement 5: Military ID Card Data Updates

**User Story:** As a faction member, I want my Military ID to reflect my current rank, subdivision, and awards, so that the document stays accurate as my career progresses.

#### Acceptance Criteria

1. WHEN a Faction_Member's rank changes (promotion or demotion), THE Military_ID_Service SHALL update the rank field on the player's Military_ID_Card to the new rank value and broadcast inventory changes to the client
2. WHEN a Faction_Member is assigned to a subdivision, THE Military_ID_Service SHALL update the subdivision field on the player's Military_ID_Card to the new subdivision name
3. WHEN a Faction_Member is removed from a subdivision, THE Military_ID_Service SHALL clear the subdivision field on the player's Military_ID_Card to empty
4. WHEN an Award is granted to a Faction_Member, THE Military_ID_Service SHALL append the award to the awards list on the player's Military_ID_Card, up to a maximum of 32 awards
5. THE Military_ID_Service SHALL locate the Military_ID_Card in the player's inventory by scanning all inventory slots and matching the item's passport ID reference against the player's passport ID
6. IF the Military_ID_Card is not found in the player's inventory during an update, THEN THE Military_ID_Service SHALL log a warning and skip the update without throwing an exception
7. IF the player is offline when a rank, subdivision, or award change occurs, THEN THE Military_ID_Service SHALL skip the in-memory item update; the Military_ID_Card SHALL be updated on the player's next login
8. IF an Award already present in the awards list is granted again, THEN THE Military_ID_Service SHALL not append a duplicate entry and SHALL skip the update

### Requirement 6: Military ID Card Display

**User Story:** As a faction member, I want to view my Military ID card in a readable UI, so that I can see all my military information presented clearly.

#### Acceptance Criteria

1. WHEN a player right-clicks while holding a Military_ID_Card, THE Map_System SHALL open a Military ID display screen that does not pause the game and is closeable by pressing ESC
2. THE Military ID display screen SHALL show the following data fields as labeled text lines: faction name, rank, subdivision, awards list, date of enlistment, and accepting officer
3. IF the subdivision field is empty or the awards list is empty, THEN THE Military ID display screen SHALL omit those fields from the display without showing blank lines or placeholder text
4. THE Military ID display screen SHALL render award badges as 16x16 pixel icons positioned to the left of each award name
5. THE Military ID display screen SHALL use the same color scheme (dark background, bordered center panel, gold title text, light-colored label text) and centered panel layout as the existing passport display screen
6. IF the Military_ID_Card contains no data component, THEN THE Map_System SHALL not open the display screen

### Requirement 7: Event Creation

**User Story:** As an admin, I want to create events via commands, so that I can organize gameplay activities for players.

#### Acceptance Criteria

1. WHEN an Admin executes `/wp ivent create '<event_name>'`, THE Event_Service SHALL create a new Event with the specified name and status PENDING, persist Event data (name, status, creator UUID, creation timestamp) to the database, and respond with a success message indicating the event was created
2. IF the provided event name is shorter than 3 characters or longer than 64 characters, THEN THE Event_Service SHALL reject creation and respond with an error message indicating the name must be between 3 and 64 characters
3. IF an Event with the same name already exists in PENDING or ACTIVE status, THEN THE Event_Service SHALL reject creation and respond with an error message: "Ивент с таким именем уже существует"
4. WHEN an Admin executes `/wp ivent setspawn` while exactly one Event owned by that Admin is in PENDING status, THE Event_Service SHALL set the event spawn point to the Admin's current position (x, y, z coordinates and dimension) and respond with a success message indicating the spawn point was set
5. IF an Admin executes `/wp ivent setspawn` and no Event owned by that Admin exists in PENDING status, THEN THE Event_Service SHALL reject the command and respond with an error message indicating no pending event was found

### Requirement 8: Event Activation and Joining

**User Story:** As a player, I want to be notified about events and optionally join them, so that I can participate in organized activities without being forced.

#### Acceptance Criteria

1. WHEN an Admin executes `/wp ivent start`, THE Event_Service SHALL change the Event status from PENDING to ACTIVE
2. WHEN an Event becomes ACTIVE, THE Event_Service SHALL broadcast a chat message to all online players: "Ивент запущен '<event_name>' чтоб попасть на ивент введите /wp join ivent"
3. WHEN a player executes `/wp join ivent` while an Event is ACTIVE and the Event spawn point is configured, THE Event_Service SHALL teleport the player to the Event spawn point and add the player to the active Event participant list
4. THE Event_Service SHALL NOT force any player to join an Event
5. IF no Event is currently ACTIVE when a player executes `/wp join ivent`, THEN THE Event_Service SHALL respond with: "Сейчас нет активных ивентов"
6. THE Event_Service SHALL record which players have joined the active Event as an in-memory list of player UUIDs that persists for the duration of the Event's ACTIVE status
7. IF an Admin executes `/wp ivent start` and no Event is in PENDING status, THEN THE Event_Service SHALL respond with an error message indicating no pending event is available to start
8. IF a player executes `/wp join ivent` while an Event is ACTIVE but the Event spawn point is not configured, THEN THE Event_Service SHALL respond with an error message indicating the event spawn point has not been set
9. IF a player executes `/wp join ivent` and the player is already recorded as a participant of the active Event, THEN THE Event_Service SHALL teleport the player to the Event spawn point without adding a duplicate entry to the participant list

### Requirement 9: Event Completion

**User Story:** As an admin, I want to end events cleanly, so that the event lifecycle is properly managed.

#### Acceptance Criteria

1. WHEN an Admin executes `/wp ivent stop` and the current Event status is ACTIVE, THE Event_Service SHALL change the Event status to COMPLETED
2. IF an Admin executes `/wp ivent stop` and no Event is currently in ACTIVE status, THEN THE Event_Service SHALL send an error message to the Admin indicating that no active event exists to stop
3. WHEN an Event is stopped, THE Event_Service SHALL broadcast a chat message: "Ивент '<event_name>' завершён!"
4. WHEN an Event is stopped, THE Event_Service SHALL teleport each online player who joined the Event back to their faction spawn point as defined in WpConfig (FACTIONS_ZARNAVIA_SPAWN or FACTIONS_CHERNOGRYAD_SPAWN based on the player's FactionId)
5. WHEN an Event is stopped, THE Event_Service SHALL persist the completion timestamp and the count of players who joined the Event to the database within the same transaction as the status change
6. WHEN an Admin executes `/wp ivent delete '<event_name>'` and the Event record exists with status COMPLETED or PENDING, THE Event_Service SHALL remove the Event record from the database
7. IF an Admin executes `/wp ivent delete '<event_name>'` and the Event record does not exist or its status is ACTIVE, THEN THE Event_Service SHALL send an error message to the Admin indicating that the event cannot be deleted

### Requirement 10: Diplomacy — Truce System

**User Story:** As a general, I want to propose and accept temporary truces with the enemy faction, so that factions can have periods of ceasefire for negotiations or events.

#### Acceptance Criteria

1. WHEN a General executes `/wp diplomacy truce propose <duration_minutes>` with a duration between 5 and 120 inclusive, THE Diplomacy_Service SHALL send a chat notification to all online Generals of the opposing faction indicating a truce proposal with the specified duration
2. WHEN an opposing General executes `/wp diplomacy truce accept` while a pending proposal exists and no Truce is currently active, THE Diplomacy_Service SHALL activate the Truce for the specified duration and broadcast to all online players: "Перемирие между фракциями установлено на <duration> минут!"
3. WHILE a Truce is active, THE Diplomacy_Service SHALL cancel all PvP damage events between members of the two factions
4. IF a General executes `/wp diplomacy truce propose` with a duration outside the range 5–120 minutes, THEN THE Diplomacy_Service SHALL reject the command with an error message indicating the valid range
5. WHEN the Truce duration expires, THE Diplomacy_Service SHALL deactivate the Truce and broadcast to all online players: "Перемирие завершено!"
6. THE Diplomacy_Service SHALL persist active Truce data (start time, duration, proposing faction) to the database
7. WHEN a General executes `/wp diplomacy truce break`, THE Diplomacy_Service SHALL immediately end the active Truce and broadcast to all online players: "Перемирие нарушено фракцией <faction_name>!"
8. IF a General executes `/wp diplomacy truce propose` while a Truce is already active or a pending proposal exists, THEN THE Diplomacy_Service SHALL reject the command with an error message indicating that a truce or proposal is already in progress
9. IF no opposing General is online when `/wp diplomacy truce propose` is executed, THEN THE Diplomacy_Service SHALL reject the command with an error message indicating no opposing General is available to receive the proposal
10. IF a pending truce proposal is not accepted within 60 seconds, THEN THE Diplomacy_Service SHALL expire the proposal and notify the proposing General that the proposal was not accepted

### Requirement 11: Diplomacy — Prisoner Exchange

**User Story:** As a general, I want to negotiate prisoner exchanges with the enemy faction, so that captured faction members can be returned through diplomatic means.

#### Acceptance Criteria

1. WHEN a General executes `/wp diplomacy exchange propose <own_prisoner> <enemy_prisoner>`, THE Diplomacy_Service SHALL validate that the proposing General's faction holds the enemy_prisoner's captured passport AND the opposing faction holds the own_prisoner's captured passport, and if valid, create an exchange proposal and send a chat notification to all online Generals of the opposing faction indicating the proposed exchange
2. WHEN an opposing General executes `/wp diplomacy exchange accept`, THE Diplomacy_Service SHALL execute the exchange by invoking CaptivityService to release both prisoners, returning each prisoner's passport to its original owner and clearing their captured state
3. IF a General executes `/wp diplomacy exchange propose` while an unresolved proposal already exists between the same two factions, THEN THE Diplomacy_Service SHALL reject the command with an error message indicating that a pending proposal already exists
4. IF either prisoner is no longer in captivity when the exchange is accepted, THEN THE Diplomacy_Service SHALL reject the exchange with message: "Обмен невозможен: один из пленных уже освобождён"
5. WHEN a General executes `/wp diplomacy exchange reject`, THE Diplomacy_Service SHALL cancel the pending exchange proposal directed at that General's faction and notify the proposing faction's online Generals that the exchange was rejected
6. IF no pending exchange proposal exists for the executing General's faction when accept or reject is executed, THEN THE Diplomacy_Service SHALL reject the command with an error message indicating no pending proposal
7. IF a pending exchange proposal is not accepted or rejected within 300 seconds of creation, THEN THE Diplomacy_Service SHALL automatically expire the proposal and notify the proposing faction's online Generals that the proposal has expired

### Requirement 12: Diplomacy — Negotiation Channel

**User Story:** As a general, I want a private communication channel with enemy generals during truces, so that diplomatic discussions can happen without public visibility.

#### Acceptance Criteria

1. WHILE a Truce is active, THE Diplomacy_Service SHALL enable the `/wp diplomacy msg` command for players with Role GENERAL or OP of both factions, and reject usage by any player with a lower Role
2. WHEN a General or OP sends a message via `/wp diplomacy msg <message>`, THE Diplomacy_Service SHALL deliver the message to all online players with Role GENERAL or OP of both factions, prefixed with "[Дипломатия]", within the same server tick
3. IF the message provided to `/wp diplomacy msg` is empty after trimming or exceeds 200 characters, THEN THE Diplomacy_Service SHALL reject the message and respond with an error message indicating the length constraint
4. THE Diplomacy_Service SHALL log each diplomatic message to the audit system with action "DIPLOMACY_SEND", recording sender UUID, sender name, timestamp, and message length, without recording the message body
5. IF no Truce is currently active when a player uses `/wp diplomacy msg`, THEN THE Diplomacy_Service SHALL reject the command and respond with: "Дипломатический канал доступен только во время перемирия"
6. IF a player with Role GENERAL or OP sends a diplomacy message and no other eligible recipients are online, THEN THE Diplomacy_Service SHALL deliver the message only to the sender and indicate that no other diplomats are currently available
7. WHEN a Truce ends, THE Diplomacy_Service SHALL immediately disable the diplomacy channel and reject any subsequent `/wp diplomacy msg` commands until a new Truce becomes active

### Requirement 13: Award Definitions

**User Story:** As an admin, I want to define awards that can be granted to players, so that achievements can be recognized with named badges.

#### Acceptance Criteria

1. WHEN an Admin executes `/wp award create '<award_name>' '<description>'`, THE Awards_Service SHALL create a new Award definition and persist it to the database
2. IF the award name is fewer than 3 or more than 48 characters in length, THEN THE Awards_Service SHALL reject creation with an error message indicating the allowed name length range
3. IF the description is fewer than 1 or more than 256 characters in length, THEN THE Awards_Service SHALL reject creation with an error message indicating the allowed description length range
4. IF an Award with the same name (case-insensitive comparison) already exists, THEN THE Awards_Service SHALL reject creation with message: "Награда с таким именем уже существует"
5. THE Awards_Service SHALL store for each Award definition: name, description, icon identifier (defaulting to a predefined fallback icon when not explicitly set), and creation timestamp
6. WHEN an Admin executes `/wp award delete '<award_name>'`, THE Awards_Service SHALL remove the Award definition from the database only if no players currently hold that Award
7. IF an Admin attempts to delete an Award that has been granted to one or more players, THEN THE Awards_Service SHALL reject deletion with an error message indicating the award is currently in use
8. WHEN an Admin executes `/wp award list`, THE Awards_Service SHALL display all defined awards with their names and descriptions, or an informational message indicating no awards are defined if none exist
9. THE Awards_Service SHALL log award creation and deletion operations to the audit system

### Requirement 14: Award Granting

**User Story:** As a commander or general, I want to grant awards to faction members, so that their achievements are officially recognized.

#### Acceptance Criteria

1. WHEN a Commander or General executes `/wp award grant '<award_name>' <player>`, THE Awards_Service SHALL grant the specified Award to the target player and persist the grant record (player UUID, award name, granting officer UUID, timestamp) to the database
2. IF the specified award name does not match any existing Award definition, THEN THE Awards_Service SHALL reject the grant with an error message indicating the award was not found
3. IF the target player is not found or does not have a War Project profile, THEN THE Awards_Service SHALL reject the grant with an error message indicating the player was not found
4. IF the target player does not belong to the same faction as the granting officer, THEN THE Awards_Service SHALL reject the grant with an error message indicating a faction mismatch
5. IF the target player already has the specified Award, THEN THE Awards_Service SHALL reject the grant with message: "Игрок уже имеет эту награду"
6. WHEN an Award is granted, THE Awards_Service SHALL broadcast to all online members of the faction: "<player> получил награду '<award_name>'!"
7. WHEN an Award is granted, THE Awards_Service SHALL log the award grant to the audit system

### Requirement 15: Automatic Award Triggers

**User Story:** As a player, I want to automatically receive awards for specific in-game achievements, so that my accomplishments are recognized without manual intervention.

#### Acceptance Criteria

1. WHEN a Faction_Member successfully captures an enemy's passport (CaptureResult.Success), THE Awards_Service SHALL grant the "Награда за взятие в плен врага!" award to the capturing player if the player does not already possess that award
2. THE Awards_Service SHALL support a configurable list of automatic trigger conditions defined in the server configuration, with a maximum of 20 trigger entries
3. WHEN an automatic award is triggered for an online player, THE Awards_Service SHALL display an action bar message to the player: "Вы получили награду: '<award_name>'" where <award_name> is the display name of the granted award (maximum 64 characters)
4. THE Awards_Service SHALL evaluate automatic triggers only for Faction_Members (players in ACCEPTED state with an assigned faction) who do not already possess the corresponding Award
5. THE Awards_Service SHALL evaluate trigger conditions synchronously in response to the triggering game event, not on a periodic tick schedule
6. IF the Awards_Service fails to persist an automatic award grant due to a storage error, THEN THE Awards_Service SHALL log the failure and not display the award notification to the player

### Requirement 16: Award Display

**User Story:** As a player, I want to see my awards displayed on my Military ID and in a dedicated UI, so that I can view my achievements.

#### Acceptance Criteria

1. THE Awards_Service SHALL provide award data including name, description, grant date, and badge icon reference to the Military ID display screen for rendering in the awards section, displaying up to 6 awards
2. WHEN a player executes `/wp awards`, THE Awards_Service SHALL display the player's earned awards as chat messages listing each award's name, description, and grant date formatted as dd.MM.yyyy
3. WHEN a player executes `/wp awards <player>`, THE Awards_Service SHALL display the target player's earned awards as chat messages listing each award's name, description, and grant date formatted as dd.MM.yyyy
4. IF a player executes `/wp awards <player>` and the target player is not found, THEN THE Awards_Service SHALL send an error message indicating the player was not found
5. IF a player has no earned awards, THEN THE Awards_Service SHALL display a message indicating no awards have been earned
6. THE Awards_Service SHALL render award badges as 16x16 pixel icons in the Military ID display screen, arranged sequentially within the awards section

### Requirement 17: Database Persistence for New Features

**User Story:** As a server operator, I want all new feature data persisted reliably, so that events, diplomacy state, and awards survive server restarts.

#### Acceptance Criteria

1. THE Event_Service SHALL store event data in a dedicated `events` database table with columns: id (INTEGER PRIMARY KEY auto-increment), name (VARCHAR(64) NOT NULL), status (VARCHAR(16) NOT NULL), creator_uuid (CHAR(36) NOT NULL), spawn_x (INTEGER NOT NULL), spawn_y (INTEGER NOT NULL), spawn_z (INTEGER NOT NULL), created_at (BIGINT NOT NULL, epoch milliseconds), completed_at (BIGINT, nullable)
2. THE Event_Service SHALL store event participation in a dedicated `event_participants` table with columns: event_id (INTEGER NOT NULL, foreign key to events.id ON DELETE CASCADE), player_uuid (CHAR(36) NOT NULL), joined_at (BIGINT NOT NULL, epoch milliseconds), with a composite primary key of (event_id, player_uuid)
3. THE Diplomacy_Service SHALL store truce data in a dedicated `truces` table with columns: id (INTEGER PRIMARY KEY auto-increment), proposing_faction (VARCHAR(16) NOT NULL), target_faction (VARCHAR(16) NOT NULL), duration_minutes (INTEGER NOT NULL), started_at (BIGINT NOT NULL, epoch milliseconds), ended_at (BIGINT, nullable), status (VARCHAR(16) NOT NULL)
4. THE Awards_Service SHALL store award definitions in a dedicated `award_definitions` table with columns: id (INTEGER PRIMARY KEY auto-increment), name (VARCHAR(64) NOT NULL UNIQUE), description (VARCHAR(256) NOT NULL), icon_id (VARCHAR(64) NOT NULL), created_at (BIGINT NOT NULL, epoch milliseconds)
5. THE Awards_Service SHALL store player awards in a dedicated `player_awards` table with columns: id (INTEGER PRIMARY KEY auto-increment), player_uuid (CHAR(36) NOT NULL), award_id (INTEGER NOT NULL, foreign key to award_definitions.id), granted_by_uuid (CHAR(36) NOT NULL), granted_at (BIGINT NOT NULL, epoch milliseconds)
6. THE Database layer SHALL provide new tables via a versioned SQL migration file following the existing naming convention (V{n}__{description}.sql) registered in the migrations index.txt file, using the ${AI} token for auto-increment columns to support both SQLite and MySQL
7. THE Database layer SHALL execute migrations for new tables on server startup before services initialize, within the existing transactional migration runner that commits each migration individually and records the version in schema_version
8. IF a migration for a new feature table fails during execution, THEN THE Database layer SHALL roll back that individual migration, log the failure with the migration version and error detail, and prevent the server from completing startup

### Requirement 18: Configuration for New Features

**User Story:** As a server operator, I want configurable parameters for all new features, so that I can tune behavior without code changes.

#### Acceptance Criteria

1. THE Map_System SHALL read the following parameters from the server configuration section "map": minimap radius (integer, range 16 to 512, default 128 blocks), update interval (integer, range 1 to 200, default 20 ticks), maximum cache size (integer, range 64 to 8192, default 1024 entries), and default corner position (string, one of TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, default TOP_RIGHT)
2. THE Event_Service SHALL read the following parameters from the server configuration section "events": maximum concurrent events (integer, range 1 to 50, default 10) and maximum event name length (integer, range 8 to 128, default 64 characters)
3. THE Diplomacy_Service SHALL read the following parameters from the server configuration section "diplomacy": maximum truce duration (integer, range 3600 to 604800, default 86400 seconds), minimum truce duration (integer, range 60 to 3600, default 300 seconds), and truce cooldown period (integer, range 0 to 604800, default 43200 seconds)
4. THE Awards_Service SHALL read the following parameters from the server configuration section "awards": automatic trigger definitions (list of strings, default empty list) and maximum awards per player (integer, range 1 to 1000, default 100)
5. THE WpConfig SHALL define all new configuration values within sections named "map", "events", "diplomacy", and "awards" using NeoForge ModConfigSpec defineInRange for integer values and define for string/list values, consistent with the existing section pattern
6. IF a configuration value is absent from the TOML file, THEN THE WpConfig SHALL use the specified default value for that parameter without logging an error
7. IF a numeric configuration value is set outside its defined range in the TOML file, THEN THE WpConfig SHALL clamp the value to the nearest bound as enforced by ModConfigSpec defineInRange
