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

## 4. Античит

WGuard в составе `warproject-*.jar` отвечает за авторизацию, капчу и guard-flow, но **не** за полноценную защиту от flyhack/killaura/combat cheats.

Текущий план: **писать собственный модуль античита позже**.

До появления зрелого античита:

- `allow-flight=false` уже выставлено;
- держать whitelist на alpha/beta;
- использовать Ledger/логи для расследований;
- держать дежурного админа на запуске;
- включить ручной ban/mute workflow.

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

## 6. Опционально

- **MrCrayfish's Gun Mod** (NeoForge 1.21.1) — огнестрел.
- **MTS / Immersive Vehicles** — техника.
- **Custom Loading Screen** — брендированная заставка.

## Размер модпака

Сейчас в `server/mods/` уже есть крупные jar-файлы. Для Pterodactyl это нормально при ручной загрузке/SFTP, но для GitHub Releases лучше публиковать итоговую сборку и список companion-модов отдельно, а не полагаться только на содержимое `server/mods/` в репозитории.
