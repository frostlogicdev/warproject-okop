# Changelog

All notable changes to **War Project** are documented here.

The current release scope is **mod + server for Pterodactyl**. Launcher and site/API are intentionally outside the current scope.

## [Unreleased]

### Added
- Pterodactyl deployment docs and egg for WarProject NeoForge 1.21.1.
- Server launch checklist for cracked NeoForge + Pterodactyl.
- Companion mod list for the current server pack.
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

## [3.0.0] - 2026-XX-XX

### Added
- 31 blocks: trench fortification (Okop), field camp, military base (Baza).
- Server-side RP systems for WarProject.
- Soft integration with JourneyMap.
- SQLite persistence via jarJar.
- NeoForge 21.1.x, Minecraft 1.21.1, Java 21.
