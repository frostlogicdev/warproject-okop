# Pterodactyl — WarProject server

Инструкция для текущего релизного scope: **мод + сервер** на Pterodactyl.

## 1. Что нужно заранее

- Рабочий Pterodactyl Panel + Wings.
- Docker image `ghcr.io/pterodactyl/yolks:java_21`.
- Открытый игровой порт `25565/tcp`.
- Закрытый от публичного интернета RCON-порт `25575/tcp`, если RCON используется.
- Java 21 server environment.

## 2. Импорт egg

1. Panel → Admin → Nests.
2. Создать nest `WarProject` или выбрать существующий.
3. Import Egg → загрузить `docs/pterodactyl/egg-warproject.json`.
4. Проверить переменные:

```text
NEOFORGE_VERSION
WP_VERSION
WP_REPO
WP_BRANCH
MAX_PLAYERS
SERVER_MOTD
WHITELIST
RCON_PORT
RCON_PASSWORD
```

## 3. Рекомендуемые ресурсы

Для closed alpha можно стартовать скромнее, но под публичную beta/opening держите запас.

| Параметр | Значение |
|---|---|
| RAM | 12–16 GB |
| CPU | 4–6 vCPU, желательно высокая частота |
| Disk | 30–50 GB NVMe |
| Swap | 0–2 GB |
| Docker image | `ghcr.io/pterodactyl/yolks:java_21` |

## 4. Variables

| Переменная | Значение |
|---|---|
| `NEOFORGE_VERSION` | `21.1.229` |
| `WP_VERSION` | `3.0.0` |
| `WP_REPO` | `frostlogicdev/warproject-okop` |
| `WP_BRANCH` | `01u16jspaspjgna` |
| `MAX_PLAYERS` | `150` или меньше для alpha |
| `SERVER_MOTD` | `§c§lWar Project §8\| §fMilitary RP` |
| `WHITELIST` | `true` для alpha/beta |
| `RCON_PORT` | `25575` |
| `RCON_PASSWORD` | secret, минимум 16 случайных символов |

Не коммитьте RCON-пароль в git.

## 5. Установка

Egg install script:

1. скачивает NeoForge installer;
2. выполняет `--install-server`;
3. создаёт `libraries/`, `run.sh`, `user_jvm_args.txt`;
4. пытается скачать `warproject-${WP_VERSION}.jar` из GitHub Releases;
5. принимает EULA.

После установки вручную проверьте/докиньте в `mods/`:

```text
warproject-3.0.0.jar
journeymap-neoforge-1.21.1-6.0.0-beta.74.jar
worldedit-mod-7.3.5.jar
multiverse-1.21.1-4.3.1.jar
```

Остальные companion-моды — по `docs/MODS.md`.

## 6. Конфиги

После первого запуска проверьте:

- `server.properties`;
- `config/warproject-server.toml`;
- `user_jvm_args.txt`;
- whitelist/ops;
- RCON runtime-настройки.

Координаты Multiverse-миров и регионов будут заполнены позже, когда будут готовы реальные локации.

## 7. Бэкапы

Минимум перед открытием:

- включить Pterodactyl backups;
- проверить restore вручную;
- если используется `tools/backup.sh`, RCON должен быть доступен только из private network/localhost.

## 8. Перед opening

Сверить `docs/LAUNCH_CHECKLIST.md`.

Минимум:

```bash
./gradlew clean build
./gradlew test
```

Затем проверить в панели:

- server boot;
- WarProject loaded;
- JourneyMap loaded;
- WorldEdit loaded and admin-only;
- Multiverse loaded;
- registration/login/captcha;
- backup + restore;
- RCON not public.
