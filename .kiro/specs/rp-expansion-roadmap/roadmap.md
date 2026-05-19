# RP Expansion Roadmap (v2 — Unified)

## Vision

Transform War Project from a faction-RP foundation into a complete, production-quality
military roleplay server. The roadmap covers **18 modules across 5 priority tiers** layered
on top of the existing v3.0 architecture without breaking changes. Each module slots into
the existing patterns: `Service` + `Dao` + `Commands` + payloads + migration + spec.

This roadmap is opinionated. It picks defaults so the project owner can react ("yes",
"no", "change X") instead of authoring from scratch. See **Open questions** at the end —
answer any subset, the rest stays as the default.

## Architectural principles

1. **No regressions.** Every new module is additive. Existing tables/registries are
   extended via new migrations only; no `ALTER TABLE … DROP`.
2. **Service-oriented.** Each module exposes a single `Service` class registered in
   `ServiceRegistry`. Cross-module access goes through `ServiceRegistry.get(...)`,
   never via static singletons.
3. **`Result<T>` error model** is reused everywhere; no checked exceptions across module
   boundaries. Failure paths log via `AuditLogger` and return `Result.failure(messageKey)`.
4. **Audit-first.** Every state-changing operation calls `AuditLogger.log(action, …)`
   with no PII. Money amounts and message bodies are summarized, never echoed verbatim.
5. **Spec-driven.** Each module gets its own `.kiro/specs/<name>/` with
   `requirements.md` (EARS), `design.md`, and `tasks.md` before code is written.
6. **Config-first.** No magic constants. All limits live in `WpConfig` sections, loaded
   on server start, hot-reloaded via `/wp reload`.
7. **i18n.** Every player-facing string is added to both `en_us.json` and `ru_ru.json`
   in the same commit; covered by `LangCoverageTest`.
8. **Testable.** Each module ships at least: 1 codec round-trip property, 1 service
   invariant property, 1 command-table test, 1 DAO round-trip with H2.

## Module catalog

### P0 — foundational drivers (must come first)

#### 1. Economy & supply — `economy-and-supply`

- Wallets, treasury, salaries, taxes, quartermaster shop, supply drops, black market.
- **Tables**: `wallets`, `wallet_tx`, `treasury`, `treasury_tx`, `shop_items`,
  `shop_orders`, `tax_policy`.
- **Depends on**: nothing structural.
- **Drives**: 3, 5, 7, 9, 14 (every economic decision).

#### 2. Combat & medical — `medical-and-wounds`

- Wounds (LIGHT/HEAVY/CRITICAL), bleeding, medic role, stretcher, posture,
  stamina, dehydration, friendly-fire toggle, PTSD/contusion, diseases, blood types.
- **Tables**: `wound_log`, `medical_loadout`, `disease_state`, `blood_types`.
- **Depends on**: ServiceRegistry, existing `CombatTagService`.

### P1 — gameplay loops (after foundation)

#### 3. Operations & orders — `operations-and-orders`

- Order types (`CAPTURE_POINT`, `DEFEND_BASE`, `PATROL_ROUTE`, `ESCORT_VIP`, `RECON`),
  capture-point block, tactical markers, briefing room, squad waypoints.
- **Tables**: `orders`, `order_assignments`, `capture_points`, `capture_progress`.
- **Depends on**: 1 (rewards), 4 (markers/comms).

#### 4. Communication & intel — `communication-and-intel`

- Radio item & block, channel encryption, jammer block, drones, signals intelligence.
- **Tables**: `radio_channels`, `radio_keys`, `intercept_log`.
- **Depends on**: existing `field_radio` block.

#### 5. Logistics & supply chain — `logistics-and-supply-chain` *(new)*

- Resource types (fuel, ammo, rations, meds), warehouse blocks with spoilage,
  NPC supply convoys with hijack mechanics, field kitchens with cook role,
  ration items replacing infinite vanilla food.
- **Tables**: `supply_resources`, `warehouses`, `warehouse_inv`, `convoy_routes`,
  `convoy_state`.
- **Depends on**: 1, 7.

#### 6. Combat depth — `combat-depth` *(new)*

- Heavy weapon mounts (LMG/AGS) with two-man crews, posture-based accuracy,
  artillery/mortar with spotter+gunner, air-support call-ins, stealth model
  (noise/visibility), ammunition variants.
- **Tables**: `heavy_weapon_mounts`, `artillery_fire_missions`, `air_support_credit`.
- **Depends on**: 2, 4.

#### 7. Engineering corps — `engineering-corps` *(new)*

- Mines/IEDs with detection and disarming, blueprint-based fortification building,
  pontoon bridges, vehicle/structure repair with health, barbed-wire cutters.
- **Tables**: `placed_mines`, `blueprints`, `repair_jobs`, `damaged_blocks`.
- **Depends on**: 1, existing fortification blocks.

### P2 — political / progression depth

#### 8. Hierarchy & state — `hierarchy-and-state`

- Positions (`PRESIDENT`, `MINISTER_OF_DEFENSE`, `FOREIGN_MINISTER`,
  `CHIEF_OF_STAFF`), decrees, war-state machine (`PEACE/TENSION/WAR/CEASEFIRE`),
  casus belli ledger, treaties, optional elections, internal political parties,
  referendums.
- **Tables**: `positions`, `decrees`, `war_state`, `casus_belli`, `treaties`,
  `treaty_signatures`, `election_terms`, `votes`, `political_parties`,
  `referendums`.
- **Depends on**: 1, existing `DiplomacyService`.

#### 9. Service record & XP — `service-record`

- Per-player record (kills, captures, missions, hours, decorations, disciplinaries),
  XP curve gating promotions, biographies, family/dependents pension on death,
  field letters, dog-tag drops, burial-team task.
- **Tables**: `service_record`, `xp_log`, `disciplinaries`, `bios`, `family_links`,
  `letters`, `dog_tags`, `burial_jobs`.
- **Depends on**: 1, existing rank system, awards.

#### 10. Recon & spec-ops — `recon-and-spec-ops` *(new)*

- Recon role with camouflage and ambush bonuses, spotting model (only recon sees
  far enemies), prisoner-of-conscience capture (knockout from behind), interrogation
  GUI, sabotage missions, double agents.
- **Tables**: `spotted_enemies`, `interrogation_log`, `sabotage_missions`,
  `double_agents`.
- **Depends on**: 2, 4, existing `CaptivityService`.

#### 11. Military justice — `military-justice` *(new)*

- Desertion auto-detection, war-crime ledger (POW execution, civilian kills,
  banned-weapon use), court-martial GUI with judge/prosecutor/defense/jury,
  sentences (demotion, prison block, execution), bounty/wanted system.
- **Tables**: `desertion_records`, `war_crimes`, `court_martial_cases`,
  `court_martial_votes`, `sentences`, `wanted_list`.
- **Depends on**: 8, 9, audit.

### P3 — bases, infrastructure, integrations

#### 12. Bases & infrastructure — `bases-and-infrastructure`

- Base flag block with claim/raid windows, power grid (generator → cable →
  consumers), watch-tower, sentry turret, vehicle assignment, hangar block.
- **Tables**: `base_flags`, `power_links`, `vehicle_assignments`, `hangar_spawns`.
- **Depends on**: 1, 8.

#### 13. Admin tools & integrations — `admin-tooling`

- Discord webhook (one-way) and bot (two-way commands), `/report` system,
  spectator OP mode, web dashboard (live faction map, leaderboards, news,
  archive of decrees), replay viewer, basic anti-cheat heuristics, automated
  DB backups before each migration + on schedule.
- **Tables**: `reports`, `tickets`, `webhook_subscriptions`, `anticheat_events`.
- **Depends on**: 8, audit.

#### 14. Civilian roles — `civilian-roles` *(new)*

- Non-combat classes (journalist with photo-item and news posts, chaplain/political-officer
  with morale aura, merchant/smuggler with cross-faction trade during truce),
  refugee NPCs spawned on territory loss, neutral villages affecting casus belli.
- **Tables**: `civilian_classes`, `news_photos`, `merchant_inventory`, `refugees`,
  `neutral_villages`.
- **Depends on**: 1, 8, 9.

#### 15. Server infrastructure — `server-infrastructure` *(new)*

- Whitelist + RP application form via web, two-way Discord bot commands,
  expanded web dashboard (frontline, lineage, archives), basic anti-cheat,
  automatic DB and world backups (S3 or local), monitoring metrics (Prometheus).
- **Tables**: `whitelist_applications`, `backup_log`, `metrics_snapshots`.
- **Depends on**: 13.

### P4 — polish, lore, retention

#### 16. Immersion & lore — `immersion-and-lore`

- Memorial wall, news board, propaganda leaflets, faction anthem on accept,
  banner emboss, tomb of unknown soldier, uniforms tied to rank (slot
  restrictions), military music (march/anthem/requiem), parades and ceremonies,
  scripted radio broadcasts, wartime censorship word filter (RP, not moderation).
- **Tables**: `news_articles`, `propaganda_leaflets`, `parades`, `radio_scripts`,
  `censored_words`.
- **Depends on**: existing audit, 8.

#### 17. Seasons & meta — `seasons-and-meta`

- Season schema (`season_id` FK on stat tables), end-of-season snapshot, scripted
  server-wide events, hidden achievements, holiday calendar (Victory Day,
  Liberation Day) with bonuses.
- **Tables**: `seasons`, `season_archive`, `holiday_calendar`.
- **Depends on**: 9.

#### 18. War meta & frontline — `war-meta-and-frontline` *(new)*

- World partitioned into regions, frontline visualization, weekly war reports
  auto-generated to news board, war chronicle with major events
  (base captures, generals killed, truces), forced peace periods after long
  wars with reconstruction events.
- **Tables**: `war_regions`, `frontline_state`, `weekly_reports`, `war_chronicle`,
  `peace_periods`.
- **Depends on**: 3, 8, 16.

## Priority matrix

| #  | Module                         | Priority | Effort | Unlocks    | Risk   |
|----|--------------------------------|----------|--------|------------|--------|
| 1  | Economy & supply               | P0       | M      | 3,5,7,8,9  | Low    |
| 2  | Combat & medical               | P0       | M      | 6,7,9,10   | Low    |
| 3  | Operations & orders            | P1       | L      | 6,9,18     | Medium |
| 4  | Communication & intel          | P1       | M      | 3,8,10,13  | Medium |
| 5  | Logistics & supply chain       | P1       | M      | 6,7,12,14  | Medium |
| 6  | Combat depth                   | P1       | L      | —          | High   |
| 7  | Engineering corps              | P1       | M      | 12         | Medium |
| 8  | Hierarchy & state              | P2       | L      | 11,12,16,18| Medium |
| 9  | Service record & XP            | P2       | M      | 11,16,17   | Low    |
| 10 | Recon & spec-ops               | P2       | M      | 11         | Medium |
| 11 | Military justice               | P2       | M      | —          | Medium |
| 12 | Bases & infrastructure         | P3       | L      | 18         | High   |
| 13 | Admin tools                    | P3       | M      | 15         | Low    |
| 14 | Civilian roles                 | P3       | M      | 16         | Low    |
| 15 | Server infrastructure          | P3       | M      | —          | Medium |
| 16 | Immersion & lore               | P4       | S      | 18         | Low    |
| 17 | Seasons & meta                 | P4       | S      | —          | Low    |
| 18 | War meta & frontline           | P4       | M      | —          | Medium |

Effort: **S** ≤ 1 sprint, **M** ≈ 2 sprints, **L** ≥ 3 sprints.

## Dependency graph (textual)

```
1 ─┬─► 3 ──► 6, 9, 18
   ├─► 5 ──► 6, 7, 12, 14
   ├─► 7 ──► 12
   ├─► 8 ──► 11, 12, 14, 16, 18
   ├─► 9 ──► 11, 14, 16, 17
   └─► 14

2 ─┬─► 6
   ├─► 7
   ├─► 9
   └─► 10

3 ─► 6, 9, 18

4 ─► 3, 8, 10, 13

8 ─► 11, 12, 16, 18

9 ─► 11, 16, 17

13 ─► 15
16 ─► 18
```

The hard ordering constraint is **P0 → P1 → P2 → P3 → P4**. Within a tier, modules
without internal cross-edges can be developed in parallel.

## Cross-cutting concerns

- **Localization**: every new string in both `en_us.json` and `ru_ru.json` in the
  same commit. `LangCoverageTest` enforces parity.
- **Audit**: every state mutation calls `AuditLogger.log(...)` with no PII. Money
  amounts and message bodies are summarized (length, recipient count), never echoed
  verbatim.
- **Permissions**: a single `WpPermission` enum (Position + Role + OpLevel) added
  in module 8 centralizes all access checks afterwards.
- **Backward-compat**: all migrations are forward-only; older saves remain loadable.
- **Tests** per module:
  - 1 codec round-trip property (jqwik)
  - 1 service invariant property (jqwik)
  - 1 command-table test (RoleCommandTableTest pattern)
  - 1 DAO round-trip (H2 in-memory)
  - 1 GameTest scenario where it reasonably maps to gameplay

## Integration points with existing code

| Existing surface                | New responsibility |
|---------------------------------|--------------------|
| `ServiceRegistry`               | Register all new services here. |
| `WarProjectServerEvents`        | Wire tick handlers (medical bleeding, salary, season expiry, frontline tick). |
| `WpCommandRoot`                 | Mount new command nodes (`/wp money`, `/wp shop`, `/wp medic`, `/wp order`, `/wp radio`, `/wp decree`, `/wp report`, `/wp recon`, `/wp justice`, `/wp logistics`, `/wp news`, …). |
| `WpPayloadRegistrar`            | Register new payloads in c2s/s2c subpackages. |
| `Migrations` + `index.txt`      | Append `V3..V20` per module. |
| `WpConfig`                      | Append a section per module. |
| `LegacyAttachmentBridge`        | Extend if any new attachment migrates from legacy. |
| `MapBroadcastService`           | Extended by Operations (markers), Bases (claim flags), War-meta (frontline). |
| `JourneymapServerConfigPatcher` | Add new icon sets per module. |
| `AuditLogger` + `AuditAction`   | Append new enum members per module. |
| `FactionNpcEntity`              | Subclassed by Quartermaster, Banker, Black-marketeer, Recruiter, Convoy driver, Refugee, Merchant, Journalist, Chaplain. |

## File layout convention (per module)

```
src/main/java/com/frostlogic/warproject/server/<module>/
├── <Module>Service.java            — public API, ServiceRegistry-registered
├── <Module>Data.java               — immutable record(s)
├── <Module>State.java              — enum if applicable
├── handler/                        — event handlers (login, tick, death, …)
└── ...

src/main/java/com/frostlogic/warproject/server/command/nodes/
└── <Module>Commands.java           — command tree

src/main/java/com/frostlogic/warproject/persistence/dao/
└── <Module>Dao.java                — JDBC, takes Connection

src/main/java/com/frostlogic/warproject/network/payload/{c2s,s2c}/
└── <Module>*Payload.java

src/main/resources/db/migrations/
└── V{N}__<module>.sql

.kiro/specs/<module-kebab-case>/
├── requirements.md                 — EARS form
├── design.md                       — sequence diagrams, data model
└── tasks.md                        — checkbox plan
```

## Recommended sequencing

A realistic delivery cadence assuming one developer or one small team. Modules
within a tier without internal cross-edges can be parallelized.

| Sprint | Module(s)                     | Outcome |
|--------|-------------------------------|---------|
| 1–2    | 1 economy-and-supply          | wallets, treasury, shop, salaries |
| 3–4    | 2 medical-and-wounds          | wounds, bleeding, medic, posture, stamina |
| 5      | 5 logistics-and-supply-chain  | warehouses, convoys, kitchens, rations |
| 6–7    | 7 engineering-corps           | mines, blueprints, repair, bridges |
| 8–10   | 6 combat-depth                | heavy weapons, artillery, stealth |
| 11–13  | 3 operations-and-orders       | orders, capture-points, briefing |
| 14–15  | 4 communication-and-intel     | radio, jammer, drones, intercept |
| 16–18  | 8 hierarchy-and-state         | positions, decrees, war-state, treaties |
| 19–20  | 9 service-record              | XP, biographies, dog-tags, letters |
| 21–22  | 10 recon-and-spec-ops         | recon role, sabotage, double agents |
| 23–24  | 11 military-justice           | desertion, war crimes, court-martial |
| 25–27  | 12 bases-and-infrastructure   | flags, power, sentries, hangars |
| 28–29  | 13 admin-tooling              | discord webhook+bot, reports, spy mode |
| 30–31  | 14 civilian-roles             | journalist, chaplain, merchant, refugees |
| 32–33  | 15 server-infrastructure      | whitelist app, dashboard, anticheat, backups |
| 34     | 16 immersion-and-lore         | memorial, news, propaganda, anthem, parades |
| 35     | 17 seasons-and-meta           | seasons, holidays, hidden achievements |
| 36–37  | 18 war-meta-and-frontline     | regions, frontline, chronicle, weekly reports |
| 38–40  | polish, balancing, content    | bug-fix sprint, content pass |

This is a **target schedule**, not a contract — modules can drop, swap, or merge
based on real feedback from playtesting.

## Open questions for the project owner

The roadmap is opinionated. Before any module's full spec is written, please confirm
or override (defaults shown in **bold**):

1. **Currency naming.** A single unit "**WP credits**", or per-faction (rubles for
   Zarnavia, marks for Chernogryad)?
2. **Vehicle stack.** Stay with **Superb Warfare** for vehicles, or build our own
   abstraction?
3. **Voice or text radio?** **Text-only** to start. Voice would need Simple Voice
   Chat integration later. Confirm?
4. **Discord integration scope.** **Webhook + two-way bot** (default in module 13)
   or webhook-only?
5. **Map technology.** Stay on **JourneyMap** for fullscreen + minimap, or build
   native (military-features-expansion already drafted a native map; pick one)?
6. **Web dashboard.** Embedded HTTP listener (port configurable, **default
   disabled**), or skip entirely?
7. **Season cadence.** **90 days default**, configurable, opt-in by admin?
8. **Class loadouts.** **Hard-locked** (rifleman cannot equip sniper rifle) or
   only soft-suggested (warning, no enforcement)?
9. **NPC framework.** Extend the existing `FactionNpcEntity`, or introduce a
   pluggable **`WpNpcRole` enum** (banker, quartermaster, recruiter, propagandist,
   journalist, convoy_driver, refugee, merchant)?
10. **Localization priority.** **Russian-first** with English as parity, or both
    equal weight?
11. **Vehicle ownership policy.** Faction-only (any member of faction can drive),
    rank-gated, or assigned-only (1 driver per vehicle)?
12. **Death penalty.** **Item drop on death** (current vanilla), or partial-loss
    + insurance via wallet?
13. **Capture-point cadence.** Continuous tick income, or one-time reward on
    capture + holding bonus?
14. **Court-martial threshold.** What punishments require a vote (currently
    proposed: anything stronger than `REPRIMAND`)?
15. **Mod scope.** Solo project or expecting other contributors? (Affects how
    much abstraction is worth front-loading.)
16. **Civilian/non-combat roles allowed mid-war?** **Yes** (journalist follows
    troops, chaplain blesses) or restricted to peace?
17. **Mines and IEDs in PvP.** Allow real damage, or RP-flagged "dummy" with chat
    notification only?
18. **Disease and morale dynamics.** Hard mechanic (slows you down) or soft
    (stat-only, no gameplay effect)?
19. **Anti-cheat depth.** Heuristic logging to audit, or active kick on detection?
20. **Whitelist mode.** Open server, or RP application required (default
    proposed: **application required**)?

Answer any subset; everything else stays at the default. From your choices the
spec for each module will be tuned before code starts.

## Status

| Module | Spec | Implementation |
|--------|------|----------------|
| 1 Economy & supply           | **DRAFT-FULL** (`economy-and-supply/`)            | Not started |
| 2 Combat & medical           | **DRAFT-FULL** (`medical-and-wounds/`)            | Not started |
| 3 Operations & orders        | **DRAFT-REQ** (`operations-and-orders/`)          | Not started |
| 4 Communication & intel      | **DRAFT-REQ** (`communication-and-intel/`)        | Not started |
| 5 Logistics & supply chain   | **DRAFT-FULL** (`logistics-and-supply-chain/`)    | Not started |
| 6 Combat depth               | **DRAFT-REQ** (`combat-depth/`)                   | Not started |
| 7 Engineering corps          | **DRAFT-REQ** (`engineering-corps/`)              | Not started |
| 8 Hierarchy & state          | **DRAFT-REQ** (`hierarchy-and-state/`)            | Not started |
| 9 Service record & XP        | **DRAFT-REQ** (`service-record/`)                 | Not started |
| 10 Recon & spec-ops          | **DRAFT-REQ** (`recon-and-spec-ops/`)             | Not started |
| 11 Military justice          | **DRAFT-REQ** (`military-justice/`)               | Not started |
| 12 Bases & infrastructure    | **DRAFT-REQ** (`bases-and-infrastructure/`)       | Not started |
| 13 Admin tools               | **DRAFT-REQ** (`admin-tooling/`)                  | Not started |
| 14 Civilian roles            | **DRAFT-REQ** (`civilian-roles/`)                 | Not started |
| 15 Server infrastructure     | **DRAFT-REQ** (`server-infrastructure/`)          | Not started |
| 16 Immersion & lore          | **DRAFT-REQ** (`immersion-and-lore/`)             | Not started |
| 17 Seasons & meta            | **DRAFT-REQ** (`seasons-and-meta/`)               | Not started |
| 18 War meta & frontline      | **DRAFT-REQ** (`war-meta-and-frontline/`)         | Not started |

Legend:
- **DRAFT-FULL** = `requirements.md` + `design.md` + `tasks.md` complete, ready to implement
- **DRAFT-REQ** = `requirements.md` complete; `design.md` and `tasks.md` to be written before that module's sprint starts
