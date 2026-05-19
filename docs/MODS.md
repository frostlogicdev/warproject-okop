# Companion mods — War Project NeoForge 1.21.1

Формат релиза сейчас: **только мод + сервер**. Launcher/site не входят в текущий production scope.

Моды рядом с `warproject-*.jar` в `server/mods/` для production-онлайна до 150 игроков. Все версии должны быть именно для **NeoForge 1.21.1**.

> ⚠️ Проверяйте совместимость с NeoForge 1.21.1 перед скачиванием. Forge/Fabric версии НЕ подойдут.

## 0. Уже добавлено в `server/mods/`

| Мод | Файл | Статус |
|---|---|---|
| **WarProject** | `warproject-3.0.0.jar` | Основной мод проекта |
| **JourneyMap** | `journeymap-neoforge-1.21.1-6.0.0-beta.74.jar` | Добавлен |
| **WorldEdit** | `worldedit-mod-7.3.5.jar` | Добавлен; разрешать только админам |
| **Multiverse** | `multiverse-1.21.1-4.3.1.jar` | Добавлен; используется для нескольких миров |

## 1. Производительность сервера (CRITICAL)

| Мод | Назначение | Источник | Статус |
|---|---|---|---|
| **Canary** | Server-side оптимизация (Lithium-аналог для NeoForge). MSPT −15..30 %. | modrinth.com/mod/canary | Рекомендуется добавить |
| **FerriteCore** | Снижение RAM на больших мирах (−20..40 %). | modrinth.com/mod/ferrite-core | Рекомендуется добавить |
| **ScalableLux** | Многопоточный движок освещения. | modrinth.com/mod/scalablelux | Рекомендуется добавить |
| **Spark** | Профайлер. Команды `/spark profiler`, `/spark tps`, `/spark health`. Без него лаги ловить вслепую. | spark.lucko.me/download | Очень желательно добавить до stress-test |

## 2. Защита и регионы (CRITICAL)

| Мод | Назначение | Статус |
|---|---|---|
| **Open Parties and Claims (OpenPaC)** | Клейм-чанков + партии. Нужен, чтобы базы нельзя было разнести обычным игрокам. | Рекомендуется добавить |
| **Ledger** | Лог всех player-действий (place/break/click/chat) с откатом. | Рекомендуется добавить |
| **FastBack** | Git-style снапшоты мира без даунтайма. | Опционально, если хватает `tools/backup.sh` + Pterodactyl backups |

## 3. Игровой контент / инфраструктура RP

| Мод | Назначение | Статус |
|---|---|---|
| **JourneyMap** | Карта + веб-UI. | Добавлен |
| **WorldEdit** | Строительство и правки мира. | Добавлен; только для админов |
| **Multiverse** | Несколько миров/измерений, чтобы не размещать все зоны в одном мире. | Добавлен |
| **Simple Voice Chat** | Голосовой чат с групповыми каналами для фракций / подразделений. UDP порт 24454. | Желательно для RP |

## 4. Античит — WGuard (в `warproject-*.jar`)

Античит входит в основной мод (`server/wguard/`). Отдельные anti-cheat моды ставить не нужно.

Проверки:

| Check | Что ловит | VL вес |
|---|---|---|
| `SPEED` | Горизонтальное движение выше `wguard.speedMaxBlocksPerTick / 10` блоков за тик (с учётом speed-эффекта ×1.5). | 10 |
| `FLY` | Длительный полёт без mayfly / spectator / creative / NoGravity и вне воды. | 5 |
| `REACH` | Атака с расстояния выше `wguard.reachMaxDistance / 10`. | 15 |
| `KILL_AURA` | Слишком частые атаки (`killAuraMaxAttacksPerSecond`) или резкое вращение головы (`killAuraMaxRotationPerTick`). | 25 |
| `FAST_BREAK` | Пачка блоков за меньше, чем `fastBreakMinTicks` на блок. | 10 |
| `NUKER` | Много разных блоков в очень узком окне (< 40 тиков). | 20 |
| `NO_FALL` | Падение > 4 блоков без урона. | 10 |

Действия при нарушениях (суммарный VL):

- `≥ wguard.violationKickThreshold` — кик (по умолчанию 50);
- `≥ wguard.violationBanThreshold` — бан через `BansDao` (по умолчанию 150);
- VL падает на 1 каждые `wguard.violationDecayTicks` тика (по умолчанию 100 = 5 с).

Исключения:

- OP с пермишн ≥ 2 полностью скипаются.
- Игроки в транспорте из `wguard.vehicleModNamespaces` (по умолчанию `tacz`, `mts`, `immersivevehicles`, `vehicle`, `mrcrayfishvehicle`) получают `vehicleSpeedMultiplier` × лимит скорости и пропуск fly/no-fall.
- Игроки с оружием из `wguard.weaponModNamespaces` (по умолчанию `tacz`, `mrcrayfishgun`, `gun`) получают ×4 attack-rate и ×3 rotation-rate на KILL_AURA.
- Игроки в состояниях NEW / LOGIN_PENDING / CAPTCHA не проверяются, пока не выйдут в ACCEPTED.

Все флаги пишутся в `audit_log` (`WGUARD_FLAG` / `WGUARD_BAN`), и все онлайн-OP получают уведомление в чат. Полностью отключить античит можно `wguard.enabled = false` в `warproject-server.toml`.

## 5. Multiverse и координаты

Так как проект будет использовать несколько миров, все координаты в `server/config/warproject-server.toml` должны указывать правильный dimension/world id.

Особенно важно заполнить позже:

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

Если базы находятся в разных мирах, у каждой строки `regions.bases` должен быть свой корректный `<dimension>`.

> ⚠️ Если `factions.*Spawn` оставлены на дефолте `[0, 64, 0]`, сервер логгирует WARN при старте и новые игроки будут спавниться в void.

## 6. Опционально

- **MrCrayfish's Gun Mod** (NeoForge 1.21.1) — огнестрел. WGuard уже расслабляет KILL_AURA для этого namespace.
- **MTS / Immersive Vehicles** — техника. WGuard уже знает об этих namespace для speed/fly-исключений.
- **Custom Loading Screen** — брендированная заставка.

## Размер модпака

Сейчас в `server/mods/` уже есть крупные jar-файлы. Для Pterodactyl это нормально при ручной загрузке/SFTP, но для GitHub Releases лучше публиковать итоговую сборку и список companion-модов отдельно, а не полагаться только на содержимое `server/mods/` в репозитории.
