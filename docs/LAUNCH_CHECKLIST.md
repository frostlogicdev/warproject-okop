# Launch checklist — War Project (mod + server, cracked, NeoForge 1.21.1, Pterodactyl)

Формат релиза: **только мод + сервер**. Launcher/site не входят в текущий запуск.

## T−7 дней — инфраструктура

- [ ] VPS/железо взято: ≥6 vCPU (желательно Ryzen/EPYC с высокой частотой), 16 GB RAM, NVMe ≈50 GB+.
- [ ] OS Ubuntu 22.04/24.04 LTS. Pterodactyl Panel и Wings установлены по официальному гайду.
- [ ] DNS A-запись `mc.<domain>` → IP, SRV-запись `_minecraft._tcp.<domain>` → 25565.
- [ ] Firewall: 25565/tcp (game), 24454/udp (Simple Voice Chat, если установлен), 8443/tcp (панель), 22/tcp (SSH с IP-whitelist).
- [ ] RCON нужен для backup/админки, но **НЕ в публику**: 25575 только localhost / WireGuard / private network.
- [ ] Pterodactyl egg импортирован (см. `docs/pterodactyl/README.md`).
- [ ] В Pterodactyl задан `RCON_PASSWORD` длиной ≥16 случайных символов.

## T−7 — безопасность (CRITICAL)

- [x] BCrypt cost 12 для новой auth-системы.
- [x] Legacy JSON-профили мигрируются на BCrypt после успешного входа.
- [x] `auth.password.minLen ≥ 8`, `attempts ≤ 5`.
- [ ] Sudo без пароля ОТКЛЮЧИТЬ. SSH key-only.
- [ ] fail2ban для SSH + Pterodactyl panel.
- [ ] Аккаунт панели — 2FA.
- [ ] Так как собственный античит будет позже: запускать alpha/beta только с активным админ-наблюдением и логами.

## T−3 — мир и конфиги

- [ ] Multiverse установлен и проверен на сервере.
- [ ] Созданы/проверены нужные миры под зоны проекта.
- [ ] Простроены: choice hall, Zarnavia spawn, Chernogryad spawn, captcha-spawn (y≈320 или выбранная безопасная высота).
- [ ] Реальные координаты проставлены в `warproject-server.toml` → `[factions]`, `[captcha]`, `[regions]`.
- [ ] Для `regions.bases` указан правильный dimension/world id для каждого мира Multiverse.
- [ ] WorldEdit разрешён только админам (permissions handler / panel policy).
- [ ] FastBack или `tools/backup.sh` в cron: рекомендуется раз в 6 часов.
- [ ] Сборка `./gradlew clean build` проходит.
- [ ] `./gradlew test` проходит.
- [ ] `warproject-3.0.0.jar` залит в `server/mods/`.
- [x] JourneyMap добавлен.
- [x] WorldEdit добавлен.
- [x] Multiverse добавлен.
- [ ] Остальные companion-моды добавлены по выбранному production/minimal плану. Никаких Forge-only / Fabric-only версий.

## T−1 — стресс-тест

- [ ] Проведён внутренний тест на ≈20–50 человек (друзья, тестеры).
- [ ] Желательно поставить Spark и выполнить `/spark profiler --timeout 300`.
- [ ] MSPT < 50 мс под нагрузкой.
- [ ] Проверены: регистрация, логин, legacy login migration, капча, выбор фракции, captivity, ransom, rank promote/demote, audit log.
- [ ] Проверены переходы/телепорты между Multiverse-мирами, если они участвуют в gameplay flow.
- [ ] Работают бэкапы и restore (ручно выполнить восстановление из архива).
- [ ] Логи (`server/logs/`) и sqlite в бэкапе.

## T−0 — запуск

- [ ] `whitelist.json` или `enforce-whitelist=false` (осознанно!) — решить до запуска.
- [ ] Объявление запуска в Discord/TG с IP/SRV и правилами.
- [ ] Дежурный админ в чате.
- [ ] Сверкнуть `enforce-secure-profile=false`, `online-mode=false` (cracked).
- [ ] Сверкнуть, что RCON недоступен с публичного интернета.
- [ ] Первые 30 минут — следим за `/spark tps` или логами panel, если Spark ещё не установлен.

## T+24h — пост-лонч

- [ ] Разобраны инциденты (баны, роллбэки через Ledger/ручные логи).
- [ ] Проверяем, сколько RAM реально используется, подкручиваем `-Xmx` при необходимости.
- [ ] Aggregate-отчёт в audit-канал: сколько регистраций, фракционный баланс, пики онлайна.
- [ ] Бэкап и верификация restore выполнены ещё раз.
- [ ] Собрать feedback от игроков, завести issues в GitHub.
