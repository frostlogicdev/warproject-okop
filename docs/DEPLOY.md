# WarProject — production deploy guide

Описывает как поднять публичный cracked-сервер NeoForge 1.21.1 с модом WarProject на Pterodactyl, до ≈50–150 онлайн.

Читай вместе с `docs/PRODUCTION_READY.md`, `docs/MODS.md`, `docs/LAUNCH_CHECKLIST.md` и `docs/pterodactyl/README.md`.

## 0. Стек

- Minecraft 1.21.1 + NeoForge 21.1.229
- Java 21 (образ `ghcr.io/pterodactyl/yolks:java_21`)
- SQLite (встроено), при желании — MySQL 8 (`server/config/warproject-server.toml` § `[db]`)
- Pterodactyl Panel + Wings (Docker)

## 1. Критические TODO перед публикой онлайна

### 1.1. Авторизация / BCrypt

Статус:

- новая DB-auth использует BCrypt cost 12;
- legacy JSON-профили после успешного входа автоматически мигрируются с SHA-256+salt на BCrypt cost 12;
- legacy registration policy поднята до minimum password length 8;
- `server/server.properties` теперь держит RCON выключенным по умолчанию.

Перед публичным онлайном всё равно нужно выполнить:

1. `./gradlew clean build`
2. `./gradlew test`
3. ручной тест входа legacy-профиля, чтобы убедиться, что миграция SHA-256 → BCrypt сохраняется в `players.json`.

### 1.2. Координаты баз и спавнов (BLOCKER)

В `server/config/warproject-server.toml` все координаты сейчас «0,64,0» и `regions.bases = []`. Без реальных значений игроки будут спавниться на (0,64,0), а фракционные регионы не будут работать.

Что сделать:

1. Построить в мире: choice hall, Zarnavia base, Chernogryad base, captcha-platform (y ≈ 320).
2. Записать координаты в секции `[factions]`, `[captcha]`.
3. Для каждой базы добавить строку в `[regions].bases` в формате `'FACTION;dimension;minX,minY,minZ;maxX,maxY,maxZ'`.
4. Совместить с claim'ами OpenPaC для физической защиты блоков.

### 1.3. RCON / секреты

`server/server.properties` хранится безопасным шаблоном: `enable-rcon=false`.

Если RCON нужен для backups/админки:

1. включайте его только в runtime-конфиге панели/сервера;
2. пароль задавайте через Pterodactyl variable/secret, не через git;
3. пароль ≥16 символов;
4. порт 25575 открывать только localhost / WireGuard / private network.

### 1.4. Audit notifier (желательно)

`[audit].notifier = "NONE"`. Для Discord-вебхука:

1. Создать приватный audit-канал в вашем Discord, скопировать webhook URL.
2. URL хранить только в env/panel secret, например `WP_DISCORD_AUDIT_WEBHOOK`.
3. Не коммитить webhook в git.

## 2. Развёртывание на Pterodactyl

Подробно в `docs/pterodactyl/README.md`. Кратко:

1. **Wings**: убедиться что Docker-образ `ghcr.io/pterodactyl/yolks:java_21` доступен.
2. **Egg**: в панели Admin → Nests → Import Egg, загрузить `docs/pterodactyl/egg-warproject.json`.
3. **Server**: Create Server → Egg = WarProject NeoForge 1.21.1. Ресурсы: 14 GB RAM (из них всё равно запас на OS/JM/overhead), 4–6 vCPU, 20 GB disk, Swap = 0 (или 2 GB), CPU Limit = 0 (без лимита), Block I/O = 500.
4. **Variables** (заполняете в панели):
   - `NEOFORGE_VERSION = 21.1.229`
   - `WP_VERSION = 3.0.0`
   - `WP_REPO = frostlogicdev/warproject-okop`
   - `WP_BRANCH = 01u16jspaspjgna`
   - `MAX_PLAYERS = 150`
   - `SERVER_MOTD = §c§lWar Project §8| §fMilitary RP`
   - `WHITELIST = true`
   - `RCON_PORT = 25575` если RCON используется
   - `RCON_PASSWORD = <сгенерируйте ≥16 символов>` если RCON используется
5. **Первый запуск**: egg в install-фазе сам скачает NeoForge installer, выполнит `--install-server`, сформирует `libraries/`, `user_jvm_args.txt`, и скачает мод `warproject-3.0.0.jar` из GitHub Releases.
6. **Моды поддержки**: вручную залейте в `/home/container/mods/` все моды из `docs/MODS.md` (Canary, FerriteCore, ScalableLux, Spark, OpenPaC, Ledger, FastBack, Simple Voice Chat). Через SFTP-реквизиты панели.
7. **server.properties / config**: при первом запуске файлы сгенерируются внутри volume. Залейте поверх версии из репо (`server/server.properties`, `server/user_jvm_args.txt`, `server/config/*`) и затем внесите реальные координаты мира.

## 3. Firewall / DNS / SRV

| Порт | Протокол | Назначение | Доступ |
|---|---|---|---|
| 25565 | TCP | Minecraft | всем |
| 24454 | UDP | Simple Voice Chat | всем (если включён) |
| 25575 | TCP | RCON | только localhost / WireGuard / private network |
| 8443 | TCP | Pterodactyl panel | ваши IP |
| 22 | TCP | SSH | ваши IP |

DNS:

```
mc.<domain>.        IN A    <ip>
_minecraft._tcp.<domain>. IN SRV 0 5 25565 mc.<domain>.
```

## 4. Бэкапы

Два варианта, выбирайте один или оба:

1. **Pterodactyl backups** — в панели. Работает «из коробки», сжимает весь volume. Недостаток: при работающем сервере возможен повреждённый chunk-файл.
2. **`tools/backup.sh`** — запускается с хоста. С RCON-паролем (`RCON_PASSWORD=...`) выполняет `save-off`/`save-all flush` перед архивацией — безопасно. Сохраняет `world*`, `warproject.db`, `config/`, whitelist/ops.

Cron пример:
```
0 */6 * * *  /opt/warproject/tools/backup.sh /var/lib/pterodactyl/volumes/<uuid> /opt/warproject/backups 14
```

Дополнительно: мод **FastBack** даёт git-style снапшоты мира без даунтайма.

## 5. Мониторинг

- **/spark tps / /spark health** — всегда.
- **panel → Resource graph** — RAM/CPU/Disk.
- **Ledger** — лог всех действий + rollback в случае рейда.
- **Audit notifier** WarProject — после настройки все register/login/promote/captivity/ransom будут прилетать в audit-канал.

## 6. CI

`docs/ci-template.yml` — готовый workflow под текущий scope репозитория (Gradle build + JUnit). Скопируйте вручную в `.github/workflows/ci.yml`: GitHub App не может писать в `.github/workflows/`, потому что нет `workflows` permission.

## 7. Когда открывать регистрацию

НЕ открывайте публично пока:

- НЕ заполнены реальные координаты мира и регионы.
- НЕ пройдены `./gradlew clean build` и `./gradlew test`.
- НЕ пройден внутренний стресс-тест (`docs/LAUNCH_CHECKLIST.md` T−1).
- НЕТ рабочего бэкапа и проверенного restore.
- НЕТ резервного админа на связи.

Открывайте этапами:

1. Closed alpha (whitelist, ≈20–30 человек).
2. Open beta (публика, MOTD «BETA», больше логирования, ban-hammer легко поднимается).
3. Full launch (после ≈14 дней без блокеров).
