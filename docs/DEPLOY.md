# WarProject — Pterodactyl deploy

Краткий deploy-гайд для текущего scope: **мод + сервер** на Pterodactyl.

## 0. Стек

- Minecraft 1.21.1
- NeoForge 21.1.229
- Java 21
- Pterodactyl Panel + Wings
- SQLite по умолчанию (journal_mode=WAL, synchronous=NORMAL, busy_timeout=5 с, foreign_keys=ON — выставляются в `Database.initialize()` и `getConnection()`)
- Cracked mode: `online-mode=false`, `enforce-secure-profile=false`

## 1. Что уже есть в репозитории

- `server/mods/warproject-3.0.0.jar`
- `server/mods/journeymap-neoforge-1.21.1-6.0.0-beta.74.jar`
- `server/mods/worldedit-mod-7.3.5.jar`
- `server/mods/multiverse-1.21.1-4.3.1.jar`
- `docs/pterodactyl/egg-warproject.json`
- `server/server.properties`
- `server/user_jvm_args.txt`
- `server/config/warproject-server.toml`

## 2. Critical перед публичным открытием

### 2.1 Координаты миров

Пока координаты не заполнены — публично не открывать. При старте сервер логгирует WARN, если любой из `factions.*Spawn` остаётся на плейсхолдере `[0, 64, 0]`.

Нужно будет заполнить в `server/config/warproject-server.toml`:

```toml
[captcha]
spawnX = ...
spawnY = ...
spawnZ = ...

[factions]
zarnaviaSpawn = [...]
chernogryadSpawn = [...]
choiceHallSpawn = [...]

[regions]
bases = [
  'ZARNAVIA;<dimension>;minX,minY,minZ;maxX,maxY,maxZ',
  'CHERNOGRYAD;<dimension>;minX,minY,minZ;maxX,maxY,maxZ'
]
```

Так как используется Multiverse, для каждого региона нужен правильный `<dimension>` / world id.

**Стороны фиксированы.** На сервере ровно две игровые фракции: **Zarnavia** и **Chernogryad**. Создание пользовательских фракций не предусмотрено по дизайну. Subdivisions внутри этих двух сторон создаются через `SubdivisionsDao` и менеджмент команды.

### 2.2 RCON

В git-шаблоне RCON выключен:

```properties
enable-rcon=false
```

На production включать RCON только в runtime-конфиге Pterodactyl:

- `RCON_PASSWORD` — secret в панели, не в git;
- пароль ≥ 16 случайных символов;
- `25575/tcp` доступен только localhost / WireGuard / private network;
- не открывать RCON в публичный интернет.

### 2.3 Авторизация

Уже сделано:

- новая auth-система использует BCrypt cost 12;
- legacy JSON-профили мигрируются на BCrypt после успешного входа;
- minimum password length = 8.

Перед релизом проверить:

```bash
./gradlew clean build
./gradlew test
```

И вручную проверить legacy-login migration на тестовом профиле.

### 2.4 Античит — WGuard

Античит входит в `warproject-*.jar` (пакет `server/wguard/`) и инициализируется в `ServerEvents.onServerAboutToStart`. Покрывает SPEED / FLY / REACH / KILL_AURA / FAST_BREAK / NUKER / NO_FALL с эскалацией violation → kick → ban и записью в `audit_log`.

Перед релизом:

- убедитесь, что в `warproject-server.toml` выставлен `wguard.enabled = true` (это дефолт);
- проверьте `wguard.vehicleModNamespaces` и `wguard.weaponModNamespaces` — в списке должны быть намеспейсы всех gun/vehicle-модов, которые реально стоят на сервере;
- после внутреннего стресс-теста проверьте `SELECT * FROM audit_log WHERE action LIKE 'WGUARD_%' LIMIT 50;` на false positives. Подробнее — `docs/MODS.md §4`.

### 2.5 Open-beta tuning (10–30 игроков)

Эти параметры зафиксированы для текущей открытой беты. Менять их нужно одновременно в репе и в panel-environment, иначе поведение расходится.

**`server/server.properties`:**

```properties
max-players=30
white-list=false
player-idle-timeout=0
online-mode=false
enforce-secure-profile=false
view-distance=10
simulation-distance=8
spawn-protection=0
```

* `player-idle-timeout=0` — AFK-кик отключён по выбору владельца сервера. Игроки не кикаются вовсе (важно для RP-караульных точек, баз без движения, и т.д.).
* `white-list=false` — доступ полностью открыт. WGuard + newbie-protection — единственный барьер.
* `spawn-protection=0` — регионы WarProject (`RegionGuard`) решают это сами; vanilla spawn-protection ломает систему капчи.

**Панель Pterodactyl:**

```text
MAX_PLAYERS=30
WHITELIST=false
```

**Мод-левел настройки (автоматически из кода):**

| Параметр | Значение | Где выставляется |
|---|---|---|
| Combat-tag | 60 с | `CombatTagService.COMBAT_TAG_DURATION_MS` |
| Soft-RP death — ничего не дропается | `keepInventory=true` в каждом измерении | `SoftRpGameRulesHandler.onServerStarted` |
| PvP-защита непринятых | взаимно отменяет урон при любой стороне вне `PlayerState.ACCEPTED` | `NewbieProtectionHandler` (priority HIGHEST) |
| Death респавн | 60-с кинематика на спавне фракции | `RealisticDeathHandler` |
| Античит | enabled, OP и не-ACCEPTED исключены | `WGuardEventHandler` |

**Плен (captivity):**

* Игрок в `PlayerState.CAPTURED` — сняты оружие и броня, паспорт у захватчика (`CaptivityService.capture`).
* Освобождение — вручную через `RansomTradeMenu` или админ-команду (`CaptivityService.ransom`).
* **TODO open-beta:** автоматическое освобождение по истечении 30 минут (требует новой колонки `passports.captured_at` + scheduled `CaptivityTimeoutService`). Не включать открытую бету, пока не реализовано — иначе пленный может висеть вечно если захватчик оффлайн.
* **TODO open-beta:** механика побега (шансовая попытка из зоны базы захватчика).

## 3. Импорт egg

1. Pterodactyl Panel → Admin → Nests.
2. Создать/выбрать nest `WarProject`.
3. Import Egg → `docs/pterodactyl/egg-warproject.json`.
4. Проверить переменные:

```text
NEOFORGE_VERSION=21.1.229
WP_VERSION=3.0.0
WP_REPO=frostlogicdev/warproject-okop
WP_BRANCH=01u16jspaspjgna
MAX_PLAYERS=30
WHITELIST=false
RCON_PORT=25575
RCON_PASSWORD=<secret>
```

## 4. Создание сервера

Рекомендуемые ресурсы для открытой беты (10–30 игроков):

| Параметр | Значение |
|---|---|
| RAM | 6 GB (по выбору владельца; alpha-тесты достаточны с этим объёмом) |
| CPU | 4–6 vCPU, желательно высокая частота |
| Disk | 30–50 GB NVMe |
| Swap | 0–2 GB |
| Docker image | `ghcr.io/pterodactyl/yolks:java_21` |

JVM-флаги в `server/user_jvm_args.txt` уже настроены под 6 GB хип (`-Xmx6G -Xms6G`) + G1 + `AlwaysPreTouch` + `G1HeapRegionSize=8M`.

## 5. Моды

Сейчас в `server/mods/` уже есть WarProject, JourneyMap, WorldEdit, Multiverse.

Рекомендуется добавить перед open beta:

- Spark — profiling/TPS/MSPT;
- Ledger — расследования и rollback;
- Open Parties and Claims — защита баз/claim-зоны (PvP внутри claim-а ОСТАВИТЬ ВКЛЮЧЁННЫМ);
- FerriteCore / Canary / ScalableLux — производительность;
- Simple Voice Chat — военный RP без голоса не работает.

Полный мод-сет + версии + чёрный список: `docs/MODS_RECOMMENDED.md`.
Мод-взаимодействия и white/blacklist namespaces: `docs/MODS.md`.

## 6. Бэкапы

Минимум:

- Pterodactyl backups;
- регулярный backup world/config/db;
- тест restore до открытия.

Если используете `tools/backup.sh`, RCON должен быть настроен безопасно и недоступен извне.

## 7. Перед запуском

Обязательно:

1. `./gradlew clean build`
2. `./gradlew test`
3. Pterodactyl install test
4. Проверка входа, регистрации, капчи
5. Проверка Multiverse-миров и телепортов
6. Проверка координат фракций и регионов (нет WARN о плейсхолдерах в логах)
7. Проверка WGuard в audit\_log
8. Проверка backup + restore
9. Проверка newbie-protection: новый игрок (NEW/CANDIDATE) не получает урон от ACCEPTED и не может ударить ACCEPTED
10. Проверка soft-RP death: умер → инвентарь сохранён, XP сохранён, ничего не выпало
11. Stress-test closed alpha

Полный чек-лист: `docs/LAUNCH_CHECKLIST.md`.
