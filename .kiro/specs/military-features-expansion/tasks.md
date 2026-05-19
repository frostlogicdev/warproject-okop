# Implementation Plan: Military Features Expansion

## Overview

This plan implements five interconnected systems for the War Project NeoForge 1.21.1 mod: 3D Map & Minimap, Military ID Card, Event System, Diplomacy System, and Awards/Medals System. Tasks are ordered to build foundational layers (database, config, data models) first, then services, then commands, then client-side rendering, with integration wiring at the end.

## Tasks

- [x] 1. Database migration and configuration setup
  - [x] 1.1 Create V2 database migration file
    - Create `V2__military_features.sql` migration file with tables: `events`, `event_participants`, `truces`, `award_definitions`, `player_awards`
    - Include all indexes defined in the design schema
    - Use `${AI}` token for auto-increment columns (SQLite/MySQL compatibility)
    - Register the migration in the existing `index.txt` migrations file
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8_

  - [x] 1.2 Add configuration sections to WpConfig
    - Add "map" section: minimapRadius, updateInterval, maxCacheSize, defaultCorner
    - Add "events" section: maxConcurrent, maxNameLength
    - Add "diplomacy" section: maxTruceDuration, minTruceDuration, truceCooldown
    - Add "awards" section: autoTriggers, maxPerPlayer
    - Use `defineInRange` for integers and `define`/`defineListAllowEmpty` for string/list values
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7_

- [x] 2. Data Access Objects (DAOs)
  - [x] 2.1 Implement EventsDao and EventParticipantsDao
    - Create `EventsDao.java` in `persistence/dao/` with CRUD operations for events table
    - Create `EventParticipantsDao.java` with insert/query/count operations for event_participants table
    - All methods accept `Connection` parameter for transaction composition
    - _Requirements: 17.1, 17.2, 9.5_

  - [x] 2.2 Implement TrucesDao
    - Create `TrucesDao.java` in `persistence/dao/` with insert, update status, query active truce operations
    - Accept `Connection` parameter for transaction composition
    - _Requirements: 17.3, 10.6_

  - [x] 2.3 Implement AwardDefinitionsDao and PlayerAwardsDao
    - Create `AwardDefinitionsDao.java` with CRUD operations for award_definitions table
    - Create `PlayerAwardsDao.java` with insert, query by player, check existence, count holders operations
    - Accept `Connection` parameter for transaction composition
    - _Requirements: 17.4, 17.5, 13.5, 13.6, 14.1_

  - [ ]* 2.4 Write DAO integration tests with in-memory SQLite
    - Create `EventsDaoTest.java`, `TrucesDaoTest.java`, `AwardDefinitionsDaoTest.java`, `PlayerAwardsDaoTest.java`
    - Test CRUD operations, foreign key constraints, unique constraints
    - Verify V2 migration applies cleanly on in-memory SQLite
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_

- [x] 3. Network payloads
  - [x] 3.1 Implement server-to-client payloads
    - Create `AllyPositionsPayload.java` (List<AllyPosition> with uuid, x, y, z, factionId; max 100 entries)
    - Create `EnemyVisiblePayload.java` (List<EnemyPosition> with x, y, z, factionId)
    - Create `MapChunkPayload.java` (ChunkPos, int[] heightmap 256 entries, int[] blockColors)
    - Create `MilitaryIdSnapshotPayload.java` (MilitaryIdData record)
    - All implement `CustomPacketPayload` with proper Codec/StreamCodec
    - _Requirements: 3.5, 1.4, 1.5, 6.1_

  - [x] 3.2 Implement client-to-server payload
    - Create `MapChunkRequestPayload.java` (ChunkPos) implementing `CustomPacketPayload`
    - Register all payloads in the existing network registration system
    - _Requirements: 3.1_

- [x] 4. Military ID data model and component registration
  - [x] 4.1 Implement MilitaryIdData record and component type
    - Create `MilitaryIdData.java` record with fields: passportIdRef, faction, rankId, subdivision, awards, enlistmentDate, acceptingOfficer
    - Implement `CODEC` (RecordCodecBuilder) and `STREAM_CODEC` (RegistryFriendlyByteBuf)
    - Enforce max 64 chars per string field, max 32 entries for awards list in codec
    - Create `MilitaryIdComponentTypes.java` with DeferredRegister registration
    - _Requirements: 4.2, 4.3, 4.4_

  - [ ]* 4.2 Write property test for MilitaryIdData codec round-trip
    - **Property 5: MilitaryIdData codec round-trip**
    - Generate random valid MilitaryIdData instances (strings ≤ 64 chars, awards ≤ 32 entries)
    - Verify StreamCodec encode/decode produces equal instance
    - Verify Codec NBT serialize/deserialize produces equal instance
    - **Validates: Requirements 4.2, 4.4**

  - [ ]* 4.3 Write property test for card field update consistency
    - **Property 6: Card field update consistency**
    - Generate random MilitaryIdData + random new rank/subdivision strings
    - Verify updating one field leaves all other fields unchanged
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [ ]* 4.4 Write property test for awards list invariants
    - **Property 7: Awards list invariants**
    - Generate random award lists (0–35 entries) + random new award name
    - Verify: duplicate not added, list grows by 1 if under cap and not duplicate, unchanged if at cap
    - **Validates: Requirements 5.4, 5.8**

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Military ID Service
  - [x] 6.1 Implement MilitaryIdService
    - Create `MilitaryIdService.java` with methods: issueCard, updateRank, updateSubdivision, appendAward, syncOnLogin
    - Implement `findCard` private method scanning inventory for matching passportIdRef
    - Implement card placement algorithm: main inventory [9,35] first, then hotbar [0,8], then drop as entity
    - Handle offline player case by deferring to login sync
    - _Requirements: 4.1, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [ ]* 6.2 Write property test for card placement priority
    - **Property 4: Military ID card placement priority**
    - Generate random inventory states (empty/occupied slots)
    - Verify placement in first empty slot [9,35], fallback to [0,8], fallback to drop
    - Verify no non-empty slot is overwritten
    - **Validates: Requirements 4.1, 4.5**

  - [ ]* 6.3 Write property test for card inventory scan correctness
    - **Property 8: Card inventory scan correctness**
    - Generate random inventory with card at random slot
    - Verify scan returns correct slot when card exists, null when not
    - **Validates: Requirements 5.5**

  - [ ]* 6.4 Write property test for Military ID display completeness
    - **Property 9: Military ID display completeness**
    - Generate random MilitaryIdData with mix of empty/non-empty fields
    - Verify all non-empty fields appear in output, empty fields are omitted, no blank lines
    - **Validates: Requirements 6.2, 6.3**

- [x] 7. Event System Service and Commands
  - [x] 7.1 Implement EventData record and EventState enum
    - Create `EventState.java` enum: PENDING, ACTIVE, COMPLETED
    - Create `EventData.java` record with id, name, status, creatorUuid, spawnPos, dimension, createdAt, completedAt, participants
    - _Requirements: 7.1, 8.1, 9.1_

  - [x] 7.2 Implement EventService
    - Create `EventService.java` with methods: create, setSpawn, start, stop, join, delete, getActiveEvent
    - Implement event name validation (3–64 chars, no duplicate PENDING/ACTIVE names)
    - Implement state transitions: PENDING → ACTIVE → COMPLETED
    - Implement participant tracking (in-memory Set<UUID> during ACTIVE)
    - Implement stop logic: teleport participants to faction spawns, persist completion data in single transaction
    - Use `Result<T>` pattern for error handling
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_

  - [x] 7.3 Implement EventCommands
    - Create `EventCommands.java` with command nodes: `/wp ivent create`, `/wp ivent setspawn`, `/wp ivent start`, `/wp ivent stop`, `/wp ivent delete`, `/wp join ivent`
    - Wire commands to EventService methods
    - Add permission checks (Admin for create/setspawn/start/stop/delete, any player for join)
    - _Requirements: 7.1, 7.4, 8.1, 8.3, 9.1, 9.6_

  - [ ]* 7.4 Write property test for event name validation
    - **Property 10: Event name validation**
    - Generate random strings (0–100 chars) and random existing event names
    - Verify creation succeeds only if 3 ≤ length ≤ 64 AND no duplicate PENDING/ACTIVE name
    - **Validates: Requirements 7.2, 7.3**

  - [ ]* 7.5 Write property test for event participant idempotence
    - **Property 11: Event participant idempotence**
    - Generate random UUID sets + random join sequences
    - Verify UUID appears exactly once regardless of join count
    - **Validates: Requirements 8.9**

  - [ ]* 7.6 Write property test for faction spawn selection on event stop
    - **Property 12: Faction spawn selection on event stop**
    - Generate random participants with random factions
    - Verify teleport destination matches configured faction spawn
    - **Validates: Requirements 9.4**

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Diplomacy System Service and Commands
  - [x] 9.1 Implement TruceState record and ExchangeProposal record
    - Create `TruceState.java` record with proposingFaction, targetFaction, durationMinutes, startedAt, dbId, isExpired() method
    - Create `ExchangeProposal.java` record with proposing faction, own prisoner, enemy prisoner, creation timestamp
    - Create `TruceProposal.java` record for pending proposals with timeout tracking
    - _Requirements: 10.1, 10.6, 11.1_

  - [x] 9.2 Implement DiplomacyService
    - Create `DiplomacyService.java` with truce methods: proposeTruce, acceptTruce, breakTruce, isTruceActive, tickTruce
    - Implement exchange methods: proposeExchange, acceptExchange, rejectExchange
    - Implement negotiation channel: sendDiplomacyMessage
    - Implement PvP hook: shouldCancelPvP
    - Implement proposal timeout (60s truce, 300s exchange) via tickTruce
    - Validate truce duration (5–120 minutes), state guards, faction checks
    - Integrate with CaptivityService for prisoner exchange execution
    - Use `Result<T>` pattern for error handling
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9, 10.10, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_

  - [x] 9.3 Implement DiplomacyCommands
    - Create `DiplomacyCommands.java` with command nodes: `/wp diplomacy truce propose`, `/wp diplomacy truce accept`, `/wp diplomacy truce break`, `/wp diplomacy exchange propose`, `/wp diplomacy exchange accept`, `/wp diplomacy exchange reject`, `/wp diplomacy msg`
    - Wire commands to DiplomacyService methods
    - Add permission checks (GENERAL for truce/exchange, GENERAL/OP for msg)
    - _Requirements: 10.1, 10.2, 10.7, 11.1, 11.2, 11.5, 12.1, 12.2_

  - [ ]* 9.4 Write property test for truce duration validation
    - **Property 13: Truce duration validation**
    - Generate random integers (-1000..1000)
    - Verify proposal succeeds only if 5 ≤ duration ≤ 120
    - **Validates: Requirements 10.1, 10.4**

  - [ ]* 9.5 Write property test for PvP cancellation during active truce
    - **Property 14: PvP cancellation during active truce**
    - Generate random player pairs with factions, random truce state
    - Verify shouldCancelPvP returns true when truce active and players from different factions
    - **Validates: Requirements 10.3**

  - [ ]* 9.6 Write property test for truce proposal state guard
    - **Property 15: Truce proposal state guard**
    - Generate random combinations of truce/proposal state
    - Verify proposals rejected when truce active OR pending proposal exists
    - **Validates: Requirements 10.8**

  - [ ]* 9.7 Write property test for exchange prisoner validation
    - **Property 16: Exchange prisoner validation**
    - Generate random prisoner/faction ownership combinations
    - Verify proposal valid only when correct factions hold correct prisoners
    - **Validates: Requirements 11.1**

  - [ ]* 9.8 Write property test for diplomacy channel access control
    - **Property 17: Diplomacy channel access control**
    - Generate random roles × truce states
    - Verify message permitted only if truce active AND role is GENERAL or OP
    - **Validates: Requirements 12.1, 12.5**

  - [ ]* 9.9 Write property test for diplomacy message length validation
    - **Property 18: Diplomacy message length validation**
    - Generate random strings (0–300 chars, including whitespace-only)
    - Verify acceptance only if 1 ≤ trimmed length ≤ 200
    - **Validates: Requirements 12.3**

- [x] 10. Awards System Service and Commands
  - [x] 10.1 Implement AwardDefinition record and AwardTrigger
    - Create `AwardDefinition.java` record with id, name, description, iconId, createdAt
    - Create `AwardTrigger.java` for automatic trigger evaluation logic
    - _Requirements: 13.5, 15.1, 15.2_

  - [x] 10.2 Implement AwardsService
    - Create `AwardsService.java` with admin methods: createAward, deleteAward, listAwards
    - Implement granting: grantAward with faction check, duplicate check, persistence
    - Implement automatic triggers: evaluateTrigger, onCaptureSuccess
    - Integrate with MilitaryIdService to append awards to card
    - Implement award display data provider (max 6 most recent for Military ID)
    - Use `Result<T>` pattern for error handling
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8, 13.9, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 16.1_

  - [x] 10.3 Implement AwardCommands
    - Create `AwardCommands.java` with command nodes: `/wp award create`, `/wp award delete`, `/wp award list`, `/wp award grant`, `/wp awards`, `/wp awards <player>`
    - Wire commands to AwardsService methods
    - Add permission checks (Admin for create/delete/list, Commander/General for grant, any player for awards view)
    - _Requirements: 13.1, 13.6, 13.8, 14.1, 16.2, 16.3, 16.4, 16.5_

  - [ ]* 10.4 Write property test for award definition input validation
    - **Property 19: Award definition input validation**
    - Generate random name/description strings and random existing names
    - Verify creation succeeds only if 3 ≤ name ≤ 48, 1 ≤ description ≤ 256, no case-insensitive duplicate
    - **Validates: Requirements 13.2, 13.3, 13.4**

  - [ ]* 10.5 Write property test for cross-faction award grant rejection
    - **Property 20: Cross-faction award grant rejection**
    - Generate random faction pairs
    - Verify grant rejected when granting officer faction ≠ target player faction
    - **Validates: Requirements 14.4**

  - [ ]* 10.6 Write property test for award grant idempotence
    - **Property 21: Award grant idempotence**
    - Generate random player award sets + grant attempts
    - Verify duplicate grant rejected without modifying award list
    - **Validates: Requirements 14.5, 15.1, 15.4**

  - [ ]* 10.7 Write property test for award display truncation
    - **Property 22: Award display truncation**
    - Generate random award lists (0–50 entries)
    - Verify exactly 6 most recent returned when N > 6, all returned when N ≤ 6
    - **Validates: Requirements 16.1**

- [x] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Client-side Map System
  - [x] 12.1 Implement ChunkCache and MapChunkData
    - Create `MapChunkData.java` record for cached heightmap + block color data per chunk
    - Create `ChunkCache.java` with LRU eviction using ConcurrentLinkedHashMap, configurable max size
    - Implement thread-safe get/put/invalidate operations
    - _Requirements: 3.1, 3.4_

  - [ ]* 12.2 Write property test for LRU cache eviction order
    - **Property 3: LRU cache eviction order**
    - Generate random sequences of put/get operations on cache with small max size
    - Verify evicted entry is the one with oldest last-access timestamp
    - **Validates: Requirements 3.4**

  - [x] 12.3 Implement MinimapRenderer
    - Create `MinimapRenderer.java` rendering 128×128 minimap widget on HUD
    - Implement height-based terrain shading from cached heightmap data
    - Render ally markers (faction-colored), enemy markers (raycast-validated), territory borders
    - Implement configurable corner positioning
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8_

  - [x] 12.4 Implement FullscreenMapScreen
    - Create `FullscreenMapScreen.java` extending Screen with isPauseScreen() returning false
    - Implement zoom (3+ levels via mouse scroll, closest=64 blocks, farthest=full radius)
    - Implement pan (mouse drag, clamped to 2× view radius)
    - Display ally markers with name labels (max 50, nearest priority)
    - Display territory overlays at 30-50% opacity
    - Display player coordinates and facing direction
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10_

  - [ ]* 12.5 Write property test for ally selection capacity limits
    - **Property 1: Ally selection respects capacity limits**
    - Generate random lists of ally positions (1–200 entries) and random player position
    - Verify at most 50 returned for fullscreen (sorted by distance), at most 100 for network payload
    - **Validates: Requirements 2.7, 3.5**

  - [ ]* 12.6 Write property test for pan offset clamping
    - **Property 2: Pan offset clamping**
    - Generate random doubles for dx/dz (-10000..10000), random radius (64..512)
    - Verify clamped magnitude ≤ 2R
    - **Validates: Requirements 2.9**

  - [x] 12.7 Implement MapSystem orchestrator
    - Create `MapSystem.java` managing keybinds (toggle minimap, open fullscreen), tick updates, renderer lifecycle
    - Register event handlers: onClientTick, onRenderHud, onChunkLoad, onChunkUnload
    - Handle AllyPositionsPayload and MapChunkPayload from server
    - Implement async chunk data loading on background thread
    - _Requirements: 1.7, 2.1, 3.1, 3.2, 3.3, 3.6_

- [x] 13. Military ID Display Screen
  - [x] 13.1 Implement MilitaryIdScreen
    - Create `MilitaryIdScreen.java` extending Screen with isPauseScreen() returning false
    - Display labeled fields: faction, rank, subdivision, awards, enlistment date, accepting officer
    - Omit empty fields (empty subdivision, empty awards list) without blank lines
    - Render award badges as 16×16 pixel icons next to award names
    - Use same color scheme as existing passport display (dark background, bordered panel, gold title)
    - Handle right-click interaction on Military ID card item to open screen
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 16.1, 16.6_

- [x] 14. Integration wiring and event listeners
  - [x] 14.1 Wire MilitaryIdService to faction membership events
    - Hook into faction acceptance flow to trigger issueCard
    - Hook into rank change events to trigger updateRank
    - Hook into subdivision assignment/removal to trigger updateSubdivision
    - Hook into player login to trigger syncOnLogin
    - _Requirements: 4.1, 5.1, 5.2, 5.3, 5.7_

  - [x] 14.2 Wire AwardsService automatic triggers
    - Register event listener on CaptivityService capture success to call onCaptureSuccess
    - Wire award grant to MilitaryIdService.appendAward for card synchronization
    - Implement action bar notification for automatic awards
    - _Requirements: 15.1, 15.3, 15.5_

  - [x] 14.3 Wire DiplomacyService PvP hook
    - Register event listener on LivingAttackEvent or equivalent to call shouldCancelPvP
    - Cancel damage event when truce is active between attacker and victim factions
    - _Requirements: 10.3_

  - [x] 14.4 Wire server-side position broadcasting
    - Implement periodic (configurable tick interval) ally position collection and S2C packet dispatch
    - Implement server-side raycast validation for enemy visibility
    - Send MapChunkPayload in response to MapChunkRequestPayload
    - _Requirements: 3.5, 1.5, 1.6_

  - [x] 14.5 Register all services in mod initialization
    - Instantiate and wire EventService, DiplomacyService, AwardsService, MilitaryIdService during server setup
    - Register DiplomacyService.tickTruce on server tick event
    - Register all command nodes in the command registration event
    - Register MilitaryIdComponentTypes DeferredRegister
    - _Requirements: 7.1, 10.1, 13.1, 4.3_

- [x] 15. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (22 properties total)
- Unit tests validate specific examples and edge cases
- All services follow the existing `Result<T>` sealed interface pattern for error handling
- All DAOs accept `Connection` parameter for transaction composition
- jqwik is used for property-based testing (already present in project)
- The project uses Java 21 with NeoForge 1.21.1 modding framework

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "3.1", "3.2"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "4.1"] },
    { "id": 2, "tasks": ["2.4", "4.2", "4.3", "4.4", "7.1", "9.1", "10.1"] },
    { "id": 3, "tasks": ["6.1", "7.2", "9.2", "10.2"] },
    { "id": 4, "tasks": ["6.2", "6.3", "6.4", "7.3", "7.4", "7.5", "7.6", "9.3", "9.4", "9.5", "9.6", "9.7", "9.8", "9.9", "10.3", "10.4", "10.5", "10.6", "10.7"] },
    { "id": 5, "tasks": ["12.1"] },
    { "id": 6, "tasks": ["12.2", "12.3", "12.4"] },
    { "id": 7, "tasks": ["12.5", "12.6", "12.7", "13.1"] },
    { "id": 8, "tasks": ["14.1", "14.2", "14.3", "14.4", "14.5"] }
  ]
}
```
