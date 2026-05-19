# War Project

Военный RP-мод для Minecraft 1.21.1 (NeoForge): окопная фортификация, полевые лагеря, военные базы и серверная логика для запуска Minecraft-сервера.

Текущий scope репозитория: **мод + сервер под Pterodactyl**. Лаунчер и сайт/API сейчас не входят в релизный scope.

## Components

- `src/` — NeoForge-мод (Java 21).
- `server/` — шаблон dedicated-server конфигов и локальная папка `mods/`.
- `docs/DEPLOY.md` — развёртывание на Pterodactyl.
- `docs/LAUNCH_CHECKLIST.md` — чек-лист перед открытием.
- `docs/MODS.md` — список модов в сборке и рекомендации.
- `docs/pterodactyl/` — egg и инструкция импорта.
- `docs/ci-template.yml` — CI-шаблон, который нужно вручную скопировать в `.github/workflows/ci.yml`.
- `tools/` — вспомогательные скрипты.

## Уже лежит в `server/mods/`

- `warproject-3.0.0.jar`
- `journeymap-neoforge-1.21.1-6.0.0-beta.74.jar`
- `worldedit-mod-7.3.5.jar`
- `multiverse-1.21.1-4.3.1.jar`

## Features

### Okop — окопная фортификация

- Support Beams (Wooden / Iron / Reinforced)
- Camo Nets (Forest / Desert / Winter)
- Sandbags (1–4 layers)
- Horizontal Covers (Wooden / Log)
- Barbed Wire (slow + damage)
- Drainage Grate (waterloggable)
- Firing Slot, Trench Stairs, Trench Lantern, Supply Crate

### Baza — военные блоки

- Military Concrete / Reinforced / Slab
- HESCO Barrier
- Metal Gate
- Checkpoint Barrier
- Tank Hedgehog
- Razor Wire Fence

### Серверная RP-логика

- WGuard: авторизация, регистрация, captcha-flow, базовые guard-проверки.
- BCrypt cost 12 для новых паролей.
- Legacy JSON-профили мигрируются с SHA-256+salt на BCrypt после успешного входа.
- SQLite persistence через jarJar `sqlite-jdbc`.
- Поддержка нескольких миров через Multiverse.
- JourneyMap soft-интеграция.

> WGuard сейчас **не заменяет полноценный combat/fly/killaura античит**. Собственный античит планируется позже.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.229+
- Java 21
- Pterodactyl Panel + Wings для production-хостинга

## Quickstart для разработки

```bash
./gradlew clean build
./gradlew test
./gradlew runClient
./gradlew runServer
```

## Production deploy

Основные документы:

1. `docs/DEPLOY.md` — как поднять сервер на Pterodactyl.
2. `docs/LAUNCH_CHECKLIST.md` — что проверить перед открытием.
3. `docs/MODS.md` — какие companion-моды уже есть и что желательно добавить.
4. `docs/pterodactyl/README.md` — как импортировать egg.

Перед открытием обязательно нужны:

- реальные координаты миров/баз/спавнов в `server/config/warproject-server.toml`;
- успешные `./gradlew clean build` и `./gradlew test`;
- проверенный запуск на Pterodactyl;
- RCON только через secret панели и без публичного доступа;
- backup + restore test;
- stress-test хотя бы на closed alpha.

## Texture generation

Плейсхолдерские текстуры лежат в `src/main/resources/assets/warproject/textures/block/`.

```bash
python tools/generate_textures.py
```

## License

MIT — см. [LICENSE](./LICENSE).
