# War Project

Военный мод для Minecraft 1.21.1 (NeoForge): окопная фортификация, полевые лагеря, военные базы, античит **WGuard**, живой лаунчер и сайт в единой военной эстетике.

## Components

- `src/` — NeoForge-мод (Java 21).
- `launcher/` — Electron + React + Vite лаунчер с WGuard splash.
- `site/` — Node/Express сайт и API.
- `docker/` — self-hosted server (Dockerfile + compose).
- `docs/` — production-ready чек-лист и CI template.

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
Полевая кухня, радио, аптечки и другие вспомогательные блоки. Суммарно — **31 уникальный блок** (сверьте с `mod_blocks_count=31` в `gradle.properties`).

### Инфраструктура
- **JourneyMap** — soft-интеграция (в `client/jmplugin/`), мод работает и без.
- **WGuard** — античит с проверками на сервере (сплеш с пазл-значком в сайте и лаунчере).
- **Персистенс** — SQLite (jarJar-embed `sqlite-jdbc`), хеши паролей — bcrypt.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.229+ (см. `gradle.properties`)
- Java 21
- Node 20+ (для launcher и site)

## Quickstart

```bash
# 1. Мод
./gradlew build           # сборка (jarJar)
./gradlew test            # JUnit5 / jqwik / AssertJ / Mockito / H2
./gradlew runClient       # локальный клиент

# 2. Сайт
cd site && npm install && npm start        # дефолт :4000

# 3. Лаунчер
cd launcher && npm install && npm run dev  # vite dev на :3000

# 4. Сервер (Docker)
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
├── launcher/                         # Electron + React + Vite
├── site/                             # Express + static
├── docker/                           # self-hosted server
├── docs/                             # PRODUCTION_READY.md, ci-template.yml
├── tools/                            # вспомогательные скрипты
├── build.gradle / settings.gradle / gradle.properties
└── LICENSE / CHANGELOG.md / README.md
```

## License

MIT — см. [LICENSE](./LICENSE). Совпадает с `mod_license=MIT` в `gradle.properties`.
