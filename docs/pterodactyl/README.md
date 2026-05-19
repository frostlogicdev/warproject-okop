# Pterodactyl — импорт egg и создание сервера WarProject

Эта папка содержит Pterodactyl egg для WarProject NeoForge 1.21.1 + инструкцию по развёртыванию в panel + Wings.

## 0. Что вы должны иметь

- Работающий Pterodactyl Panel + Wings (хотя бы один node).
- Docker image `ghcr.io/pterodactyl/yolks:java_21` (Pterodactyl скачает сам при создании сервера).
- DNS A-запись и открытый порт 25565.

## 1. Импорт egg

1. Откройте Pterodactyl Panel → **Admin** → **Nests**.
2. Создайте новый nest **WarProject** (или выберите существующий).
3. В нём нажмите **Import Egg** и загрузите файл `egg-warproject.json` из этой папки.
4. После импорта проверьте, что в egg подхватились переменные NEOFORGE_VERSION, WP_VERSION, WP_REPO, WP_BRANCH, MAX_PLAYERS, SERVER_MOTD, WHITELIST, RCON_PORT, RCON_PASSWORD.

## 2. Создание сервера

Admin → Servers → **Create New**.

### Ресурсы (для 100–150 онлайн)

| Параметр | Значение |
|---|---|
| RAM | 14 GB (`-Xms10G -Xmx10G` + 4 GB overhead) |
| Swap | 0 (или з2 GB) |
| Disk | 20 GB (мир + логи + bcakups) |
| CPU Limit | 0 (без жёсткого лимита) |
| CPU Pinning | 4–6 выделенных ядер |
| Block I/O | 500 |
| OOM Killer | enabled |

### Egg & docker image

- **Nest**: WarProject
- **Egg**: WarProject NeoForge 1.21.1
- **Docker Image**: `ghcr.io/pterodactyl/yolks:java_21`
- **Startup Command**: подставяется из egg автоматически.

### Variables

| Переменная | Значение |
|---|---|
| NEOFORGE_VERSION | `21.1.229` |
| WP_VERSION | `3.0.0` |
| WP_REPO | `frostlogicdev/warproject-okop` |
| WP_BRANCH | `01u16jspaspjgna` |
| MAX_PLAYERS | `150` |
| SERVER_MOTD | `§c§lWar Project §8\| §fMilitary RP` |
| WHITELIST | `true` |
| RCON_PORT | `25575` |
| RCON_PASSWORD | сгенерируйте, ≥16 символов |

## 3. Инсталляция (install script egg)

Egg в install-фазе самостоятельно:

1. Скачивает `neoforge-${NEOFORGE_VERSION}-installer.jar` с Maven NeoForged.
2. Запускает `java -jar ...installer.jar --install-server` → создаются `libraries/`, `user_jvm_args.txt`, `run.sh`, `unix_args.txt`.
3. Скачивает из GitHub Releases (`${WP_REPO}/releases/download/v${WP_VERSION}/warproject-${WP_VERSION}.jar`) и кладёт в `mods/`.
4. Кладёт `eula=true`.

Дальше вы вручную:

- Докидываете companion-моды в `mods/` через SFTP (`docs/MODS.md`).
- Копируете поверх свои `server.properties`, `user_jvm_args.txt`, `config/*.toml` из репо (`server/...`).

## 4. Запуск и обновления

- **Restart**: кнопка в панели или `Power → Restart` (отправляет SIGTERM → graceful save).
- **Обновление мода**: в панели поменяйте `WP_VERSION`, выжмите **Reinstall** (при этом мир/db сохраняются — install трогает только mods/libraries).
- **Обновление NeoForge**: поменяйте `NEOFORGE_VERSION`, Reinstall, проверьте совместимость модов.

## 5. Бэкапы

Pterodactyl backups (`Backups` tab) сжимают весь volume — это простой, но не самый быстрый вариант. Для production я рекомендую дополнительно запускать `tools/backup.sh` с хоста в cron с RCON-flush (см. `docs/DEPLOY.md` § 4).

## 6. Частые проблемы

- **«Failed to download NeoForge installer»**: проверьте, что Wings имеет выход в интернет и `NEOFORGE_VERSION` правильная (`https://maven.neoforged.net/releases/net/neoforged/neoforge/<v>/`).
- **OOMKill после 30 минут**: выделите серверу 16 GB RAM в панели. JVM в `user_jvm_args.txt` Д3ёржит ровно 10G, остальное — metaspace/direct/native.
- **Нет прав на mods/ из панели**: перезапустите сервер — yolks восстановит chown.
- **Pterodactyl обрывает подключения через 90 секунд**: увеличьте `stop_timeout` egg до 120, подпишите в panel proxy timeout.

## 7. Как egg связан с репо

`egg-warproject.json` в этой папке берёт мод всегда из GitHub Releases репо `WP_REPO` (по умолчанию `frostlogicdev/warproject-okop`). Если вы форкнули или переименовали — поменяйте `WP_REPO` на ваш slug.

Для publish релиза: `gradlew build` → файл `build/libs/warproject-3.0.0.jar` → GitHub Releases → tag `v3.0.0` → залить jar (имя обязательно `warproject-3.0.0.jar`).
