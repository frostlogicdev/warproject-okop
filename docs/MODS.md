# Companion mods — War Project NeoForge 1.21.1

Моды, которые **обязательно** ложатся рядом с `warproject-*.jar` в `server/mods/` для production-онлайна до 150 игроков. Все версии — для **NeoForge 1.21.1**.

> ⚠️ Проверяйте совместимость с NeoForge 1.21.1 перед скачиванием. Forge/Fabric версии НЕ подойдут.

## 1. Производительность сервера (CRITICAL)

| Мод | Назначение | Источник |
|---|---|---|
| **Canary** | Server-side оптимизация (Lithium-аналог для NeoForge). MSPT −15..30 %. | modrinth.com/mod/canary |
| **FerriteCore** | Снижение RAM на больших мирах (−20..40 %). | modrinth.com/mod/ferrite-core |
| **ScalableLux** | Многопоточный движок освещения. | modrinth.com/mod/scalablelux |
| **Spark** | Профайлер. Команды `/spark profiler`, `/spark tps`, `/spark health`. Без него лаги ловить вслепую. | spark.lucko.me/download |

## 2. Защита и регионы (CRITICAL)

| Мод | Назначение |
|---|---|
| **Open Parties and Claims (OpenPaC)** | Клейм-чанков + партии (идеально ложится на фракции). Без него любой OP-игрок разнесёт базы. modrinth.com/mod/open-parties-and-claims |
| **Ledger** | Лог всех player-действий (place/break/click/chat) с откатом. modrinth.com/mod/ledger |
| **FastBack** | Git-style снапшоты мира без даунтайма. modrinth.com/mod/fastback |

## 3. Игровой контент (IMPORTANT — для военного RP)

| Мод | Назначение |
|---|---|
| **Simple Voice Chat** | Голосовой чат с групповыми каналами для фракций / подразделений. UDP порт 24454. modrinth.com/plugin/simple-voice-chat |
| **JourneyMap** | Уже в модпаке. Карта + веб-UI. |
| **WorldEdit** | Уже в модпаке. Только для админов — ограничьте через permissions. |

## 4. Анти-чит (CRITICAL)

WGuard в составе `warproject-*.jar` отвечает за авторизацию и капчу, но **не** за flyhack/killaura.

Варианты для NeoForge 1.21.1 (проверить свежую версию перед установкой):

- **Vulcan AntiCheat** (если есть NeoForge-порт) — пакетный анализ.
- **Polymart anti-cheat NeoForge** (платные варианты).
- Собственный модуль внутри `warproject` (для долгосрочного развития — рекомендуется).

До появления зрелого античита: `allow-flight=false` (уже выставлено), активный мониторинг Ledger, ручные баны.

## 5. Опционально

- **MrCrayfish's Gun Mod** (NeoForge 1.21.1) — огнестрел.
- **MTS / Immersive Vehicles** — техника.
- **Custom Loading Screen** — брендированная заставка.

## Размер модпака

Серверный budget: до ~250 МБ суммарно. JM ≈6 МБ, WE ≈5 МБ, Canary ≈1 МБ, FerriteCore ≈0.5 МБ, Spark ≈3 МБ, OpenPaC ≈2 МБ, Ledger ≈3 МБ, FastBack ≈2 МБ, SVC ≈1.5 МБ, WP 16 МБ → ≈40 МБ. С запасом.
