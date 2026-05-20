# Changelog

All notable changes to **War Project** are documented here.

The current release scope is **mod + server for Pterodactyl**. Launcher and site/API are intentionally outside the current scope.

## [Unreleased]

### Fixed
- `server.properties`: `allow-flight=true` (captcha sky-cage requires flight).
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
- Flight default in `server.properties` no longer contradicts the anti-cheat baseline.
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
