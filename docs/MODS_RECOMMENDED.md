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
| **Simple Voice Chat** | C+S | Proximity voice with group channels. Required for any believable military RP. Configure groups per faction. | <https://modrinth.com/mod/simple-voice-chat> |
| **Open Parties and Claims (OPAC)** | C+S | Faction-friendly land claims and parties. Use **claims** to lock storage / blocks inside bases, but **keep PvP enabled** in claims (open PvP is the design). | <https://modrinth.com/mod/open-parties-and-claims> |

### OPAC configuration notes

* In `serverconfig/open_partiesandclaims-server.toml`:
  * `claimsEnabled = true`
  * `playerClaimsAdminMode = false`
  * `claimsServerDefaultProtectedFromPvp = false` — **keep PvP on inside bases** as the user requested.
  * `claimsServerDefaultProtectedFromPlayers = true` — still block griefing/looting inside claims.

---

## 2. Nice-to-have (recommended for the beta)

| Mod | Side | Why |
|---|---|---|
| **JourneyMap** or **Xaero's World Map + Minimap** | C+S | Map and waypoints. Pair with **Xaero's Better PvP** server config to disable cave radar / entity radar (anti-radar). |
| **Sit!** | S | `/sit` and chair stairs. Tiny but huge for RP immersion (briefings, mess halls). |
| **Custom NPCs** or **Easy NPC** | C+S | Quest givers, faction lore NPCs, training-room dummies. |
| **Cobblemon-style trade panels** — already covered by your `RansomTradeMenu`. Don't add a third-party trade mod. | — | Your own menu is the source of truth. |
| **Drippy Loading Screen** | C | Lets you brand the loading screen with the faction logo / Discord link. |
| **Mod Menu / Catalogue** | C | Lets players see what's installed without alt-tabbing. |
| **Resourceful Config** | C+S | Required dependency for several of the items above; safe to bundle preemptively. |

---

## 3. Optional — pick by RP flavour

### Guns / military gear (pick ONE base mod, don't stack)

| Mod | Notes |
|---|---|
| **Timeless and Classics Zero (TaCZ)** | Best-maintained NeoForge gun mod. Modern + WW2 + Cold-War packs available. Recoil, attachments, mags, ADS. |
| **MrCrayfish's Guns** | Lighter, more arcadey. Easier balancing for 10–30 player base. Pairs with **MrCrayfish's Vehicle Mod** for transport. |
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
* **Mods that add a second anti-cheat** (Vulcan, NoCheatPlus ports, Matrix). They will fight WGuard and produce false positives.
* **Mods that change combat mechanics** (Better Combat, Epic Fight, Combatify) unless you are committing to a melee RP. Guns + Epic Fight is a known crash combo.

---

## 5. Installation order (server side)

1. NeoForge 21.1.229.
2. War Project + all transitive deps (auto-resolved via JarJar).
3. Performance tier: FerriteCore, Canary, ScalableLux, Spark.
4. Admin tier: Ledger, OPAC.
5. RP tier: Simple Voice Chat, the gun mod of your choice, Macaw's + Supplementaries.
6. Restart, run `/spark profiler start` for 5 minutes under load, verify no mod is in the top-10 of self-time before opening to players.

---

## 6. Client modpack notes

All **C+S** mods above must match versions exactly between client and server. Ship a CurseForge/Modrinth modpack to players — do not rely on them assembling the list manually. Pin the modpack version in `docs/DEPLOY.md` once you cut a 1.0 release.
