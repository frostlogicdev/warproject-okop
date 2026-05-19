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

### Added
- Pterodactyl deployment docs and egg for WarProject NeoForge 1.21.1.
- Server launch checklist for cracked NeoForge + Pterodactyl.
- Companion mod list for the current server modpack.
- Multiverse added to the documented server modpack plan.

### Changed
- README simplified for the current mod + server release scope.
- Deployment documentation simplified around Pterodactyl instead of multiple hosting targets.
- WGuard authentication hardened:
  - DB auth uses BCrypt cost 12.
  - Legacy JSON profiles migrate from SHA-256+salt to BCrypt after successful login.
  - Legacy minimum password length is 8.
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
