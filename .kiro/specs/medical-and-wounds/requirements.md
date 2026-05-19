# Requirements Document — Combat & Medical

## Introduction

This document specifies the medical and combat depth subsystem for the War Project
mod. It introduces a wound model (LIGHT/HEAVY/CRITICAL) with bleeding, the medic
role with healing tools, a stretcher entity for evacuation, posture (prone/crouched/
standing), stamina, dehydration, friendly-fire policy, PTSD/contusion debuff,
diseases, and blood types for transfusion. Together these turn combat from one-shot
deathmatch into a slower, role-driven loop where field medics matter.

## Glossary

- **Wound_Service**: server-side service tracking wounds and bleeding ticks.
- **Wound_State**: enum `NONE / LIGHT / HEAVY / CRITICAL`.
- **Bleeding_Tick**: per-tick HP loss applied to wounded players.
- **Medic**: a player whose `Role` is `SOLDIER` and who has a `MEDIC` tag in the
  `medical_loadout` table; or holds the medic kit item.
- **Stretcher**: entity that two players carry to transport an unconscious or
  CRITICAL teammate to a field hospital.
- **Posture**: enum `STANDING / CROUCHED / PRONE`.
- **Stamina**: per-player floating-point bar (0–100) consumed by sprinting,
  jumping and crouch-walking; regenerates while idle.
- **Dehydration**: per-player thirst counter; decremented over time, restored by
  drinking water item or canteen.
- **PTSD_State**: temporary debuff applied after witnessing N teammate deaths in T
  seconds.
- **Disease**: persistent state (`FLU`, `DYSENTERY`, `WOUND_INFECTION`) with
  duration and effect.
- **Blood_Type**: enum `O_NEG, O_POS, A_NEG, A_POS, B_NEG, B_POS, AB_NEG, AB_POS`,
  assigned at first acceptance and never changed.
- **Field_Hospital**: a multi-block structure detected at runtime by a center
  block; provides full healing and disease cure.

## Requirements

### Requirement 1: Wound state and bleeding

**User Story:** As a soldier, I want to be wounded by lethal damage rather than
instant-killed, so that combat has more nuance and medics matter.

#### Acceptance Criteria

1. WHEN a player would die from non-environmental damage AND their `Wound_State` is
   `NONE`, THE Wound_Service SHALL cancel the death event, set `Wound_State` to
   `LIGHT`, restore HP to 50%, and apply slowness II for 5 seconds.
2. WHEN a wounded player takes damage AND `Wound_State` is `LIGHT`, THE
   Wound_Service SHALL escalate to `HEAVY` if remaining HP would drop below 4 (2
   hearts), and apply blindness for 3 seconds.
3. WHEN a `HEAVY`-wounded player takes damage that would reduce HP below 1, THE
   Wound_Service SHALL cancel death, escalate to `CRITICAL`, freeze the player in
   place (no movement, no attack), and start a 180-second death countdown.
4. WHILE in `CRITICAL` state, THE Wound_Service SHALL apply darkness, prevent block
   interaction, prevent item use, and broadcast position to all friendly Faction_Members.
5. IF the death countdown expires while in `CRITICAL`, THEN THE Wound_Service SHALL
   apply real death.
6. WHILE `Wound_State` is `LIGHT` or higher, THE Wound_Service SHALL apply a
   bleeding tick of 1 HP every `BLEEDING_TICK_INTERVAL_SEC` seconds (default 8).
7. WHEN bandage item is used on self or other player, THE Wound_Service SHALL stop
   bleeding and decrement wound severity by 1 step (CRITICAL→HEAVY→LIGHT→NONE).
8. THE Wound_Service SHALL persist current `Wound_State` per player in the
   `wound_log` table on every state transition with timestamp, source, and severity.

### Requirement 2: Medical items and the medic role

**User Story:** As a medic, I want specialized healing items, so that I can stabilize
and revive teammates.

#### Acceptance Criteria

1. THE module SHALL register four new items: `bandage`, `tourniquet`, `medic_kit`,
   `defibrillator`.
2. WHEN any player right-clicks a wounded teammate while holding `bandage`, THE
   Wound_Service SHALL stop bleeding and reduce severity by 1 step. Bandage stack
   decrements by 1.
3. WHEN any player right-clicks a wounded teammate while holding `tourniquet`, THE
   Wound_Service SHALL stop bleeding without changing severity. Useful for limb-wound
   triage; reusable.
4. WHEN a player tagged `MEDIC` right-clicks a teammate with `medic_kit`, THE
   Wound_Service SHALL set `Wound_State` to `NONE`, restore HP to full, and consume
   the kit (single-use).
5. WHEN a player tagged `MEDIC` right-clicks a `CRITICAL` teammate with
   `defibrillator` AND the teammate's death countdown has not expired, THE
   Wound_Service SHALL revive the teammate to `HEAVY` state with 4 HP and stop the
   countdown. Defibrillator has 60-second cooldown.
6. IF a non-medic uses `medic_kit` or `defibrillator`, THEN THE Wound_Service SHALL
   reject usage with `wp.medical.role_required` and not consume the item.
7. WHEN a General executes `/wp medic tag <player>` or `/wp medic untag <player>`,
   THE module SHALL set or unset the `MEDIC` tag in `medical_loadout` for that
   player. Limit per faction: `MAX_MEDICS_PER_FACTION` (default 8).

### Requirement 3: Stretcher and field hospital

**User Story:** As a medic, I want to evacuate critically wounded teammates, so
that they survive long enough to be treated.

#### Acceptance Criteria

1. WHEN a player crafts and places a Stretcher item, THE module SHALL spawn a
   `StretcherEntity` at the click location.
2. WHEN two players sneak-right-click the StretcherEntity (one at each end), THE
   module SHALL allow the entity to move at walking speed, controlled by the head
   carrier's direction.
3. WHEN a `CRITICAL` wounded player is within 2 blocks of an empty Stretcher AND a
   medic right-clicks the Stretcher with the wounded selected, THE module SHALL
   place the wounded onto the Stretcher and pause the death countdown.
4. WHEN the Stretcher reaches a Field_Hospital center block, THE module SHALL
   automatically dismount the wounded, set `Wound_State` to `NONE`, restore HP to
   full, and cure all diseases.
5. THE Field_Hospital center block SHALL be a recognized item; it requires the
   surrounding 3×3×3 region to contain at least 5 dedicated medical blocks
   (`hospital_bed`, `medical_supplies`) for activation.
6. IF a Stretcher is destroyed or despawned while carrying a wounded, THEN the
   module SHALL place the wounded at the destruction position and resume the
   death countdown.

### Requirement 4: Posture

**User Story:** As a soldier, I want to choose my stance, so that I can balance
mobility against accuracy and cover.

#### Acceptance Criteria

1. WHEN a player presses the configurable posture-cycle keybind, THE module SHALL
   cycle the player's `Posture` `STANDING → CROUCHED → PRONE → STANDING`.
2. WHILE `Posture` is `CROUCHED`, THE module SHALL apply -25% movement speed and
   +15% projectile accuracy (reduced cone) compared to `STANDING`.
3. WHILE `Posture` is `PRONE`, THE module SHALL apply -60% movement speed,
   +30% accuracy, and reduce hitbox height to 0.6 blocks.
4. THE module SHALL synchronize current posture from server to all clients within
   the same tick via `PostureChangedPayload`.
5. WHEN a player takes lethal damage while `PRONE`, THE Wound_Service SHALL apply
   the same wound logic as standing (no special bonus).
6. THE module SHALL persist last-used posture across logout via
   `WpAttachmentTypes.POSTURE`.

### Requirement 5: Stamina

**User Story:** As a soldier, I want stamina to limit endless sprinting, so that
movement choices have weight.

#### Acceptance Criteria

1. THE module SHALL maintain a per-player float `stamina ∈ [0, 100]`. Initial value
   on login is 100.
2. WHILE the player is sprinting, THE module SHALL drain stamina at
   `SPRINT_DRAIN_PER_SEC` (default 8) per second.
3. WHILE the player is jumping, THE module SHALL drain stamina by `JUMP_COST`
   (default 4) per jump.
4. WHILE the player is idle (not sprinting, not attacking, not jumping), THE module
   SHALL regenerate stamina at `REGEN_PER_SEC` (default 6) per second.
5. WHEN stamina reaches 0, THE module SHALL force-stop sprint and apply slowness I
   for 3 seconds, then enable regeneration.
6. THE current stamina value SHALL be transmitted to the client every 5 ticks via
   `StaminaPayload` for HUD rendering.

### Requirement 6: Dehydration

**User Story:** As a server operator, I want a hydration mechanic, so that long
patrols require water management.

#### Acceptance Criteria

1. THE module SHALL maintain a per-player int `thirst ∈ [0, 20]`. Initial value on
   login is 20.
2. THE module SHALL decrement thirst by 1 every `THIRST_DECAY_INTERVAL_SEC` seconds
   of online time (default 360 seconds = 6 minutes).
3. WHEN a player drinks `water_canteen` or vanilla `water_bottle`, THE module SHALL
   restore thirst by 5 and 3 respectively.
4. WHEN thirst is 0, THE module SHALL apply slowness I and prevent stamina
   regeneration until thirst > 0.
5. THE module SHALL render thirst as a HUD bar similar to vanilla hunger.

### Requirement 7: Friendly-fire toggle

**User Story:** As a general, I want to toggle friendly fire for my faction, so
that we can balance training rigor and casualty risk.

#### Acceptance Criteria

1. THE module SHALL maintain a per-faction `friendly_fire_enabled` boolean stored
   in the `medical_loadout` table (default `false`).
2. WHEN a General executes `/wp medic ff <on|off>`, THE module SHALL toggle the
   friendly-fire flag for the General's faction and broadcast a chat notification
   to all online faction members.
3. WHEN player A damages player B AND both belong to the same faction AND the
   faction's `friendly_fire_enabled` is `false`, THE module SHALL cancel the damage
   event entirely.
4. THE flag change SHALL be audit-logged with action `FF_TOGGLE` and
   `extra_json: {faction, new_value}`.

### Requirement 8: PTSD / contusion

**User Story:** As a player who survived a brutal firefight, I want lingering
psychological effects, so that combat has emotional weight.

#### Acceptance Criteria

1. THE module SHALL track per-player `witnessed_deaths_window` — friendly deaths
   within 32 blocks within the last `PTSD_WINDOW_SEC` seconds (default 60).
2. WHEN witnessed_deaths_window reaches `PTSD_THRESHOLD` (default 3), THE module
   SHALL apply a `PTSD_State` for `PTSD_DURATION_MIN` minutes (default 15).
3. WHILE `PTSD_State` is active, THE module SHALL apply: nausea I, mining fatigue I,
   and randomly play a heartbeat sound to the affected player every 10–30 seconds.
4. WHEN a player takes damage from a high-explosive source (TNT, grenade, etc.),
   THE module SHALL apply a 10-second contusion debuff: nausea II, mining fatigue II,
   and screen blur via client-side shader if available.
5. PTSD SHALL be cleared by visiting a Field_Hospital center block.

### Requirement 9: Diseases

**User Story:** As a server operator, I want diseases that motivate hygiene and
field medicine, so that the world feels grittier.

#### Acceptance Criteria

1. THE module SHALL register three diseases: `FLU`, `DYSENTERY`, `WOUND_INFECTION`,
   each with duration in minutes and a per-tick effect.
2. WHILE a player stands in a configured "trench" or "wet" biome for ≥
   `DISEASE_EXPOSURE_MIN` minutes (default 30) without hot drink consumed, THE
   module SHALL roll a 5% chance per minute to apply `FLU`.
3. WHILE a player has `WOUND_INFECTION`, THE module SHALL apply weakness I and slow
   bleeding (2 HP every 30s) until cured.
4. WHEN a player has `Wound_State = HEAVY` or `CRITICAL` AND no medical attention
   received within `INFECTION_WINDOW_MIN` (default 10), THE module SHALL roll a
   30% chance to apply `WOUND_INFECTION` on the next bleeding tick.
5. WHEN a player consumes `antibiotics_pill` (item), THE module SHALL clear the
   active disease.
6. THE module SHALL persist active disease state in the `disease_state` table.

### Requirement 10: Blood types and transfusion

**User Story:** As a medic, I want blood-type compatibility for transfusion, so
that field medicine has depth and lore.

#### Acceptance Criteria

1. WHEN a player first reaches `ACCEPTED`, THE module SHALL deterministically assign
   a blood type from `Blood_Type` enum based on hash of `playerUuid`. Distribution
   matches real-world frequencies (`O_POS=37%`, `A_POS=36%`, `B_POS=8%`, etc.).
2. THE blood type SHALL be persisted in the `blood_types` table and SHALL never
   change.
3. WHEN a medic right-clicks a wounded teammate with `blood_bag` item, THE module
   SHALL check that the bag's blood type is compatible with the recipient's type
   per ABO/Rh compatibility rules.
4. IF the blood types are incompatible, THEN THE Wound_Service SHALL apply
   `WOUND_INFECTION`-equivalent debuff (slow bleeding, weakness) for 60 seconds and
   notify the medic via chat.
5. IF the blood types are compatible, THEN THE Wound_Service SHALL restore 6 HP and
   stop bleeding.
6. WHEN a player executes `/wp medic blood`, THE module SHALL display the player's
   own blood type. Without OP permission, the executor SHALL only see their own.

### Requirement 11: Persistence and configuration

**User Story:** As a server operator, I want all medical state persisted reliably,
so that wounds, diseases, and blood types survive restart.

#### Acceptance Criteria

1. THE module SHALL ship `V4__medical_and_wounds.sql` adding tables: `wound_log`,
   `medical_loadout`, `disease_state`, `blood_types`.
2. ALL DAOs SHALL accept a `Connection` parameter for transaction composition.
3. THE migration SHALL register in `index.txt` after `V3__economy_and_supply.sql`.
4. THE module SHALL add `WpConfig.medical` section with all timing/threshold
   parameters listed in `design.md`.
5. THE config SHALL be hot-reloadable via `/wp reload`.

## Out of scope

- Surgical skill checks beyond use-item interaction.
- Realistic medical detail (broken bones, organ damage).
- Vehicle-mounted ambulances (covered by the vehicles module if pursued).
- Patient consent/refusal flows.
