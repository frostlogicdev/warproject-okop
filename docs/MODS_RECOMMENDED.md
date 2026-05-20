# Recommended companion mods for War Project (Minecraft 1.21.1 / NeoForge 21.1.x)

This is the curated mod-set that pairs well with War Project for a 10–30 player military RP server. All entries are verified compatible with NeoForge 21.1.229 and War Project's own WGuard anti-cheat / soft-RP death rules.

Legend:

* **C** = required on client
* **S** = required on server
* **C+S** = required on both sides

---

## 1. Must-have (install before opening beta)

| Mod | Side | Why | Source |
|---|---|---|---|
| **FerriteCore** | C+S | Cuts RAM usage 30–40% by deduplicating block-state and model data. Essentially free performance. | <https://modrinth.com/mod/ferrite-core> |
| **Canary** | S | Server-side fork of Lithium for NeoForge. General-purpose tick optimizations. | <https://modrinth.com/mod/canary> |
| **ScalableLux** | C+S | Async light engine. Massive chunk-gen speed-up, especially during raids and base building. | <https://modrinth.com/mod/scalablelux> |
| **Spark** | C+S | Profiler. Use `/spark profiler` to find lag spikes; mandatory for any production server. | <https://modrinth.com/mod/spark> |
| **Ledger** | S | Block-place / block-break / container / death audit log. Essential for rolling back griefing during RP events. | <https://modrinth.com/mod/ledger> |
| **Simple Voice Chat** | C+S | Proximity voice. **Configure tight: 24-block radius, NO faction filter** — enemies hear you and you hear them ("sealed-radio realism"). See dedicated section below. | <https://modrinth.com/mod/simple-voice-chat> |
| **Open Parties and Claims (OPAC)** | C+S | Land claims and parties. Use **claims only on faction territory** — grief protection is gated to faction land, wilderness is open. | <https://modrinth.com/mod/open-parties-and-claims> |

### Simple Voice Chat configuration (very important)

In `serverconfig/voicechat-server.properties`:

```properties
# 24-block proximity — you only hear shouts at very close range
voice_chat_distance=24
max_voice_chat_distance=48

# Crucially: groups are PLAYER-CREATED only, not faction-auto-joined.
# This is what "врагов слышно и они нас слышат" means — nobody is
# filtered by faction. Proximity is proximity.
allow_groups=true
group_only=false
# Do NOT auto-join players into a faction-wide group. Anyone who wants
# closed comms uses a hand-held radio (Tacz/Mr.Crayfish radio attachment).

opus_mode=VOIP
voice_activation_threshold=-50.0
```

Rationale: a 48-block proximity already turns into "yelling across a base" — 24 blocks forces players to physically move to communicate, which is exactly the RP feel we want. No faction filter means flanking, eavesdropping, and shouted warnings all work like real combat.

### OPAC configuration notes

In `serverconfig/open_partiesandclaims-server.toml`:

* `claimsEnabled = true`
* `playerClaimsAdminMode = false`
* `claimsServerDefaultProtectedFromPvp = false` — **keep PvP on inside bases**.
* `claimsServerDefaultProtectedFromPlayers = true` — block griefing/looting inside claims.
* `wildernessClaimEnabled = false` — player claims are NOT allowed in wilderness. Only faction territories grant grief protection.
* `worldDimensionAllowList` — keep claims to the overworld only; nether/end are wild zones.

---

## 2. Nice-to-have (recommended for the beta)

| Mod | Side | Why |
|---|---|---|
| **JourneyMap** or **Xaero's World Map + Minimap** | C+S | Map and waypoints. Pair with **Xaero's Better PvP** server config to disable cave radar / entity radar (anti-radar). |
| **Multiverse / Bluemap** | S | Live web map for admins to plot faction spawns and territory borders without joining the server. |
| **Sit!** | S | `/sit` and chair stairs. Tiny but huge for RP immersion (briefings, mess halls). |
| **Custom NPCs** or **Easy NPC** | C+S | Quest givers, faction lore NPCs, training-room dummies. |
| **Drippy Loading Screen** | C | Lets you brand the loading screen with the faction logo / Discord link. |
| **Mod Menu / Catalogue** | C | Lets players see what's installed without alt-tabbing. |
| **Resourceful Config** | C+S | Required dependency for several of the items above; safe to bundle preemptively. |

---

## 3. Optional — pick by RP flavour

### Guns / military gear (pick ONE base mod, don't stack)

| Mod | Notes |
|---|---|
| **Timeless and Classics Zero (TaCZ)** | Best-maintained NeoForge gun mod. Modern + WW2 + Cold-War packs available. Recoil, attachments, mags, ADS. Already whitelisted in `wguard.weaponModNamespaces`. |
| **MrCrayfish's Guns** | Lighter, more arcadey. Easier balancing for 10–30 player base. Pairs with **MrCrayfish's Vehicle Mod** for transport. Already whitelisted (`cgm`). |
| **MTS / Immersive Vehicles** | Heavier sim. Tanks, planes, ships. Overkill for 10–30 players unless that's the central RP fantasy. |

### Building / military aesthetic

* **Macaw's Bridges / Doors / Roofs / Fences / Windows** — cheap, no perf cost, huge visual upgrade for bases.
* **Decorative Blocks (Lyra)** — sandbags, barbed wire, crates.
* **Engineer's Decor** — industrial decor (panels, lamps, pipes) for bunkers and HQs.
* **Supplementaries** — flags, ash piles, signs, ropes — perfect for RP set-dressing.

### Voice / chat polish

* **Voice Chat Interaction OpenAI** — only if you have a budget; lets NPCs respond via AI. Not recommended for beta.
* **Discord Integration (NeoForge)** — mirror in-game chat to a Discord channel; essential for community building.

---

## 4. Do NOT install

* **Any client-side cheat-like utility mod** (XaeroPlus radars, MiniHUD entity tracker, baritone, meteor, etc.). WGuard does not detect them, but they break the RP fairness contract.
* **Mods that change vanilla death drops** (Corpse, Gravestone, etc.). They conflict with the `keepInventory=true` rule we set in `SoftRpGameRulesHandler`.
* **Mods that re-enable vanilla death messages in chat** (Death Counter broadcasters etc.). War Project hides them via the `showDeathMessages=false` game-rule and replaces them with a private notice in `CustomDeathMessageHandler` (only killer + victim see). A loud broadcaster mod will undo that.
* **Mods that disable join/leave message suppression** — the open-beta UX wants join/leave silent. Add `quietness`-style mods only if they're configurable to ONLY suppress vanilla messages, not the WP system ones.
* **Mods that add a second anti-cheat** (Vulcan, NoCheatPlus ports, Matrix). They will fight WGuard and produce false positives.
* **Mods that change combat mechanics** (Better Combat, Epic Fight, Combatify) unless you are committing to a melee RP. Guns + Epic Fight is a known crash combo.

---

## 5. Installation order (server side)

1. NeoForge 21.1.229.
2. War Project + all transitive deps (auto-resolved via JarJar).
3. Performance tier: FerriteCore, Canary, ScalableLux, Spark.
4. Admin tier: Ledger, OPAC.
5. RP tier: Simple Voice Chat (configured per section 1), the gun mod of your choice, Macaw's + Supplementaries.
6. Restart, run `/spark profiler start` for 5 minutes under load, verify no mod is in the top-10 of self-time before opening to players.

---

## 6. Game-rules already enforced by War Project

`SoftRpGameRulesHandler` re-applies these every server start — do NOT undo them with an operator command unless you really know why:

| Rule | Value | Effect |
|---|---|---|
| `keepInventory` | `true` | Soft RP death — no drops on death, XP preserved. |
| `doMobSpawning` | `false` | Pure PvP/RP server, no hostile mob clutter at night. |
| `playersSleepingPercentage` | `101` | Beds never skip night; full day/night cycle stays intact. |
| `showDeathMessages` | `false` | Vanilla broadcast off; `CustomDeathMessageHandler` privately notifies killer + victim only. |
| `announceAdvancements` | `false` | Advancement spam off. |

---

## 7. Client modpack notes

All **C+S** mods above must match versions exactly between client and server. Ship a CurseForge/Modrinth modpack to players — do not rely on them assembling the list manually. Pin the modpack version in `docs/DEPLOY.md` once you cut a 1.0 release.
