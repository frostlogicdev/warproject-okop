# Changelog

All notable changes to **War Project** are documented here.

The current release scope is **mod + server for Pterodactyl**. Launcher and site/API are intentionally outside the current scope.

## [Unreleased]

### Fixed
- **Faction-choice teleport crossed dimensions silently.** `FactionChoiceHandler.teleportToFactionSpawn` used `player.serverLevel()`, but at the moment of faction selection the player is standing inside `multiworld:choicehall`. Players would have been teleported to the configured XYZ inside the choice-hall dimension (void / unreachable) instead of the overworld base. Now resolves `Level.OVERWORLD` explicitly via `MinecraftServer.getLevel`.
- Same cross-dimension bug fixed in `EventService.teleportParticipantsToFactionSpawns` (post-event recall) and in `ServerPayloadHandler.onRpName` (RP-name → choice-hall step now resolves `multiworld:choicehall` explicitly, matching the already-correct logic in `RpNameCommand`).
- `server/config/warproject-server.toml`: real spawn coordinates wired in — `factions.zarnaviaSpawn = [2035, -20, 2431]`, `factions.chernogryadSpawn = [2649, -29, 1245]`, `factions.choiceHallSpawn = [51, -1944, -282]`. Captcha sky-cage stays at `[0, 320, 0]` in the overworld.
- `server/config/warproject-server.toml`: `regions.bases` populated with the two faction bases as overworld AABBs (Zarnavia 1982,-29,2401 → 2136,-2,2555 and Chernogryad 2598,-34,1214 → 2768,-7,1377). Without these `RegionGuard` would not have protected the bases from grief.
- ~87 missing translation keys added to `en_us.json` and `ru_ru.json`. Without them, players saw raw IDs like `wp.captivity.error.too_far` and `wp.auth.error.invalid_credentials` instead of localized error messages — affected captivity flow, auth/login flow, awards, diplomacy, events, passport guard and rank promote/demote.
- `server.properties`: `max-players=30`, `view-distance=10`, `simulation-distance=8` realigned with `docs/DEPLOY.md §2.5` open-beta tuning (previously drifted to 150 / 8 / 6).
- `server/user_jvm_args.txt`: `-Xms4G -Xmx4G` realigned with `docs/DEPLOY.md §4` (previously `-Xms3G -Xmx4G`; DEPLOY.md claimed `-Xmx6G`). Doc updated to match the safer 4 GB heap that leaves ≈2 GB for native / metaspace on a 6 GB Pterodactyl plan.
- `allow-flight=false` is the documented anti-cheat baseline; the captcha sky-cage works through the player's `mayfly` ability (set by `CaptchaManager`), so vanilla flight does not need to be globally enabled.
- `server.properties`: `enforce-whitelist=false` / `white-list=false` for open beta.
- `server.properties`: `player-idle-timeout=0` (prevent idle kick during captcha).
- `RegionGuard` now resolves faction from attachments (new DB pipeline) before falling back to legacy profile.
- `FactionRespawnHandler` now resolves faction from attachments before legacy profile.
- `AcceptCommandHandler` now syncs legacy `WarPlayerProfile` after acceptance (new→legacy bridge).
- `/wp captcha` now routes new-pipeline (DB) players to `CaptchaService` instead of legacy `CaptchaManager`.
- `FreezeService` no longer zeroes delta movement for CAPTCHA players in sky-cage (NoGravity conflict).
- **WGuard anti-cheat is now actually wired in.** `WGuardService` was fully implemented (`server/wguard/`) but `WGuardEventHandler.init(service)` was never called, so every `service` reference resolved to `null` and every check short-circuited. Now instantiated in `ServerEvents.onServerAboutToStart` and registered against the event handler.
- WGuard translation keys for `KILL_AURA` / `FAST_BREAK` / `NO_FALL` now match the lang files. The service previously built keys like `wp.wguard.kill_aura`, but `en_us.json` / `ru_ru.json` declare `wp.wguard.killaura` / `wp.wguard.fastbreak` / `wp.wguard.nofall`. Players flagged by those three checks would otherwise see the raw key string as the warning.
- Server boot now logs WARN if any `factions.*Spawn` in `warproject-server.toml` is still left at the placeholder `[0, 64, 0]` (catches the most common pre-launch misconfiguration).
- JourneyMap sample admin identities removed so a fresh deploy does not ship with unknown OP slots.

- `docker/entrypoint.sh` install path now includes `netcat`, so the TCP HEALTHCHECK can actually run.
- `.gitignore` covers dev-run artifacts (`runs/`, `.warproject-write-probe`).

### Added
- Pterodactyl deployment docs and egg for WarProject NeoForge 1.21.1.
- Server launch checklist for cracked NeoForge + Pterodactyl.
- Companion mod list for the current server modpack.
- Multiverse added to the documented server modpack plan.
- Anti-cheat section in `docs/DEPLOY.md` and updated WGuard description in `docs/MODS.md` reflecting the actual implementation (CheckType weights, kick/ban thresholds, decay, OP exemption, vehicle/weapon-mod namespace exemptions).
- `LAUNCH_CHECKLIST.md` now has explicit T−3 step that fails the launch if `factions.*Spawn` is still on the placeholder, and T−1 step to verify `audit_log` for WGuard false positives during stress-test.

### Changed
- README simplified for the current mod + server release scope.
- Deployment documentation simplified around Pterodactyl instead of multiple hosting targets.
- **WGuard auth (authentication system)** hardened:
  - DB auth uses BCrypt cost 12.
  - Legacy JSON profiles migrate from SHA-256+salt to BCrypt after successful login.
  - Legacy minimum password length is 8.
- **WGuard anti-cheat (new in this batch):** activated in `ServerEvents`, configurable via `[wguard]` in `warproject-server.toml` (`enabled`, kick/ban/decay thresholds, per-check limits, vehicle/weapon namespace exemptions). See `docs/MODS.md §4` and `docs/DEPLOY.md §2.4`.
- `Database.initialize()` applies `journal_mode=WAL` and `synchronous=NORMAL` once at startup; `getConnection()` applies `busy_timeout=5000` and `foreign_keys=ON` per connection. Eliminates SQLITE_BUSY storms under concurrent WGuard / DiplomacyTickHandler / AuditLogger writes.
- `build.gradle`: `-Xlint:deprecation` expanded to `-Xlint:all -Xlint:-serial -Xlint:-processing`. Surfaces unchecked / rawtypes / fallthrough / etc. without failing the build.
- `server/user_jvm_args.txt`: added `-XX:+AlwaysPreTouch` and aligned `G1HeapRegionSize` (4M→8M) with the docker-compose default so a heap reload does not show different MSPT/GC behaviour between bare-metal and containerised runs.
- `server/server.properties` keeps RCON disabled by default; production RCON must be configured through Pterodactyl secrets/private networking.
- CI template now covers only the existing Gradle mod build/test scope.

### Removed
- Outdated production checklist document that referenced components outside the current release scope.
- Local `.jqwik-database` cache from the repository.

## [3.0.0] - 2025-07

### Added
- 31 blocks: trench fortification (Okop), field camp, military base (Baza).
- Server-side RP systems for WarProject.
- Soft integration with JourneyMap.
- SQLite persistence via jarJar.
- NeoForge 21.1.x, Minecraft 1.21.1, Java 21.
