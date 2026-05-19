# WarProject — production deploy guide

Описывает как поднять публичный cracked-сервер NeoForge 1.21.1 с модом WarProject на Pterodactyl, до ≈50–150 онлайн.

Читай вместе с `docs/PRODUCTION_READY.md`, `docs/MODS.md`, `docs/LAUNCH_CHECKLIST.md` и `docs/pterodactyl/README.md`.

## 0. Стек

- Minecraft 1.21.1 + NeoForge 21.1.229
- Java 21 (образ `ghcr.io/pterodactyl/yolks:java_21`)
- SQLite (встроено), при желании — MySQL 8 (`server/config/warproject-server.toml` § `[db]`)
- Pterodactyl Panel + Wings (Docker)

## 1. Критические TODO перед публикой онлайна

### 1.1. bcrypt вместо SHA-256 (BLOCKER)

`src/main/java/com/frostlogic/warproject/server/PasswordHasher.java` сейчас хранит пароль как `SHA-256(salt || password)` с 16-байтной солью. Это НЕДОСТАТОЧНО для cracked-сервера: SHA-256 брутится на GPU на порядки быстрее bcrypt.

Библиотека `org.mindrot:jbcrypt:0.4` уже подключена в `build.gradle` (`jarJar.implementation`), но не используется.

План миграции:

1. Переписать `PasswordHasher.hash(plain)` на `BCrypt.hashpw(plain, BCrypt.gensalt(12))`.
2. `PasswordHasher.verify(plain, stored)` должен разбирать `stored`:
   - если начинается с `$2a$`/`$2b$`/`$2y$` → `BCrypt.checkpw(plain, stored)`.
   - иначе (legacy salt\u0001sha256) → сверить по старому алгоритму, и при успехе — перехэшировать в bcrypt и обновить в PlayerStore.
3. Обновить юнит-тесты под оба формата.
4. Проверить, что bcrypt cost=12 влезает в SLA логина (≈70–120 мс на современном CPU). Если высокое RPS — снизить до 11.

### 1.2. Координаты баз и спавнов (BLOCKER)

В `server/config/warproject-server.toml` все координаты сейчас «0,64,0» и `regions.bases = []`. Без реальных значений игроки будут спавниться на (0,64,0) и фракционные регионы не будут работать.

Что сделать:

1. Построить в мире: choice hall, Zarnavia base, Chernogryad base, captcha-platform (y ≈ 320).
2. Записать координаты в секции `[factions]`, `[captcha]`.
3. Для каждой базы добавить строку в `[regions].bases` в формате `'FACTION;dimension;minX,minY,minZ;maxX,maxY,maxZ'`.
4. Совместить с claim'ами OpenPaC для физической защиты блоков.

### 1.3. Audit notifier (желательно)

`[audit].notifier = "NONE"`. Для Discord-вебхука:

1. Создать приватный audit-канал в вашем Discord, скопировать webhook URL.
2. В моде сейчас `notifier` читается как enum (`NONE` или `DISCORD`). URL нужно зашить либо в `WpConfig`, либо читать из переменной окружения `WP_DISCORD_AUDIT_WEBHOOK` — рекомендуется второе.

## 2. Развёртывание на Pterodactyl

Подробно в `docs/pterodactyl/README.md`. Кратко:

1. **Wings**: убедиться что Docker-образ `ghcr.io/pterodactyl/yolks:java_21` доступен.
2. **Egg**: в панели Admin → Nests → Import Egg, загрузить `docs/pterodactyl/egg-warproject.json`.
3. **Server**: Create Server → Egg = WarProject NeoForge 1.21.1. Ресурсы: 14 GB RAM (из них всё равно з3.5 на OS/JM/overhead), 4–6 vCPU, 20 GB disk, Swap = 0 (или з2 GB), CPU Limit = 0 (без лимита), Block I/O = 500.
4. **Variables** (заполняете в панели):
   - `NEOFORGE_VERSION = 21.1.229`
   - `WP_VERSION = 3.0.0`
   - `WP_REPO = frostlogicdev/warproject-okop`
   - `WP_BRANCH = 01u16jspaspjgna`
   - `MAX_PLAYERS = 150`
   - `SERVER_MOTD = §c§lWar Project §8| §fMilitary RP`
   - `WHITELIST = true`
   - `RCON_PORT = 25575`
   - `RCON_PASSWORD = <сгенерируйте ≥16 символов>`
5. **Первый запуск**: egg в install-фазе сам скачает NeoForge installer, выполнит `--install-server`, сформирует `libraries/`, `user_jvm_args.txt`, и скачает мод `warproject-3.0.0.jar` из GitHub Releases.
6. **Моды поддержки**: вручную залейте в `/home/container/mods/` все моды из `docs/MODS.md` (Canary, FerriteCore, ScalableLux, Spark, OpenPaC, Ledger, FastBack, Simple Voice Chat). Через SFTP-реквизиты панели.
7. **server.properties / config**: при первом запуске файлы сгенерируются внутри volume. Залейте поверх версии из репо (`server/server.properties`, `server/user_jvm_args.txt`, `server/config/*`).

## 3. Firewall / DNS / SRV

| Порт | Протокол | Назначение | Доступ |
|---|---|---|---|
| 25565 | TCP | Minecraft | всем |
| 24454 | UDP | Simple Voice Chat | всем (если включён) |
| 25575 | TCP | RCON | только localhost / wg |
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
- **Ledger** — лог всех действий + `/co rollback` в случае рейда.
- **Audit notifier** WarProject — после настройки из § 1.3 все register/login/promote/captivity/ransom будут прилетать в Discord.

## 6. CI

`docs/ci-template.yml` — готовый workflow (Gradle build + JUnit). Скопируйте вручную в `.github/workflows/ci.yml` (GitHub App бота не может писать в `.github/workflows/`, см. `docs/PRODUCTION_READY.md`).

## 7. Когда открывать регистрацию

НЕ открывайте публично пока:

- НЕ выполнены TODO из § 1 (bcrypt, координаты).
- НЕ пройдён внутренний стресс-тест (`docs/LAUNCH_CHECKLIST.md` T−1).
- НЕТ рабочего бэкапа и проверенного restore.
- НЕТ резервного админа на связи.

Открывайте этапами:

1. Closed alpha (whitelist, ≈20–30 человек).
2. Open beta (публика, MOTD «BETA», больше логирования, ban-hammer легко поднимается).
3. Full launch (после ≈14 дней без блокеров).
