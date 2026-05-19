# War Project

Военный мод для Minecraft 1.21.1 (NeoForge): окопная фортификация, полевые лагеря, военные базы и серверная RP-логика для военного Minecraft-проекта.

> Текущий репозиторий содержит **NeoForge-мод**, серверные конфиги, Docker/Pterodactyl-заготовки и документацию. Лаунчер и сайт/API пока не входят в это дерево репозитория и должны поставляться отдельно либо быть добавлены позже.

## Components

- `src/` — NeoForge-мод (Java 21).
- `server/` — шаблон dedicated-server конфигов.
- `docker/` — self-hosted server (Dockerfile + compose).
- `docs/` — production-ready чек-лист, deploy-гайд, Pterodactyl egg и CI template.
- `tools/` — вспомогательные скрипты.

## Features

### Okop — 15 блоков окопной фортификации
- Support Beams (Wooden / Iron / Reinforced)
- Camo Nets (Forest / Desert / Winter)
- Sandbags (1–4 layers)
- Horizontal Covers (Wooden / Log)
- Barbed Wire (slow + damage)
- Drainage Grate (waterloggable)
- Firing Slot, Trench Stairs, Trench Lantern, Supply Crate (27 slots)

### Baza — 8 военных блоков
- Military Concrete / Reinforced / Slab
- HESCO Barrier, Metal Gate, Checkpoint Barrier
- Tank Hedgehog, Razor Wire Fence

### Field camp + extras
Полевая кухня, радио, аптечки и другие вспомогательные блоки. Суммарно — **31 уникальный блок**.

### Инфраструктура мода
- **JourneyMap** — soft-интеграция (в `client/jmplugin/`), мод работает и без JourneyMap.
- **WGuard** — серверная авторизация, капча и базовые guard-проверки. Это не полноценный combat/fly/killaura античит.
- **Персистенс** — SQLite (jarJar-embed `sqlite-jdbc`), новые пароли — bcrypt cost 12; legacy JSON-профили мигрируются на bcrypt после успешного входа.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.229+ (см. `gradle.properties`)
- Java 21

## Quickstart

```bash
# 1. Мод
./gradlew clean build    # сборка (jarJar)
./gradlew test           # JUnit5 / jqwik / AssertJ / Mockito / H2
./gradlew runClient      # локальный клиент
./gradlew runServer      # локальный сервер

# 2. Сервер (Docker)
cd docker && EULA=true docker compose up -d
```

## Tests

```bash
./gradlew test
```

Stack: JUnit Jupiter 5, jqwik, AssertJ, Mockito, H2.

## Texture generation

Плейсхолдерские текстуры лежат в `src/main/resources/assets/warproject/textures/block/`.
Перегенерация (требует pillow):
```bash
python tools/generate_textures.py
```

## Structure

```
repo-root/
├── src/                              # NeoForge mod (Java 21)
│   └── main/java/com/frostlogic/warproject/
│       ├── WarProject.java           # entry
│       ├── WpConfig.java             # весь конфиг
│       ├── attachment/ block/ item/  # регистры и контент
│       ├── client/                   # рендер + jmplugin/
│       ├── network/                  # пакеты
│       ├── persistence/              # SQLite
│       ├── server/                   # серверная логика
│       └── env/                      # prepareServerEnvironment
├── server/                           # dedicated-server configs
├── docker/                           # self-hosted server
├── docs/                             # deploy / launch / production docs
├── tools/                            # вспомогательные скрипты
├── build.gradle / settings.gradle / gradle.properties
└── LICENSE / CHANGELOG.md / README.md
```

## Production note

Для публичного открытия обязательно выполните `docs/LAUNCH_CHECKLIST.md`: реальные координаты баз/спавнов, companion-моды, backup/restore, стресс-тест и мониторинг.

## License

MIT — см. [LICENSE](./LICENSE). Совпадает с `mod_license=MIT` в `gradle.properties`.
