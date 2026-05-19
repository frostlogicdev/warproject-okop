# Launch checklist — War Project (cracked, NeoForge 1.21.1, Pterodactyl)

## T−7 дней — инфраструктура

- [ ] VPS/железо взято: ≥6 vCPU (желательно Ryzen/EPYC с высокой частотой), 16 GB RAM (з3.5 — OS, JM, RCON), NVMe ≅50 GB.
- [ ] OS Ubuntu 22.04/24.04 LTS. Pterodactyl Panel и Wings установлены по официальному гайду.
- [ ] DNS A-запись `mc.<domain>` → IP, SRV-запись `_minecraft._tcp.<domain>` → 25565.
- [ ] Firewall: 25565/tcp (game), 24454/udp (Simple Voice Chat, если установлен), 8443/tcp (панель), 22/tcp (SSH с IP-whitelist).
- [ ] RCON НЕ в публику; 25575 — только localhost / wireguard.
- [ ] Pterodactyl egg импортирован (см. `docs/pterodactyl/README.md`).

## T−7 — безопасность (CRITICAL)

- [ ] Перевести `PasswordHasher` с SHA-256 на bcrypt (cost ≥ 12). См. `docs/DEPLOY.md` § Безопасность.
- [ ] `auth.password.minLen ≥ 8`, `attempts ≤ 5` (уже в `warproject-server.toml`).
- [ ] Sudo без пароля ОТКЛЮЧИТЬ. SSH key-only.
- [ ] fail2ban для SSH + Pterodactyl panel.
- [ ] Аккаунт панели — 2FA.

## T−3 — мир и конфиги

- [ ] Простроены: choice hall, Zarnavia spawn, Chernogryad spawn, captcha-spawn (y=320).
- [ ] Реальные координаты проставлены в `warproject-server.toml` → `[factions]`, `[captcha]`, `[regions]`.
- [ ] OpenPaC: claim-зоны баз обозначены и заблокированы для не-членов фракций.
- [ ] WorldEdit разрешён только админам (permissions handler).
- [ ] FastBack или `tools/backup.sh` в cron: я рекомендую раз в 6 часов.
- [ ] Сборка `gradlew build` проходит; `warproject-3.0.0.jar` залит в `server/mods/`.
- [ ] Все companion-моды из `docs/MODS.md` в `server/mods/`. Никаких Forge-only / Fabric-only версий.

## T−1 — стресс-тест

- [ ] Проведён внутренний тест на ≈30–50 человек (друзья, тестеры).
- [ ] `/spark profiler --timeout 300` — MSPT < 50 мс под нагрузкой.
- [ ] Проверены: регистрация, логин, капча, выбор фракции, captivity, ransom, rank promote/demote, audit log.
- [ ] Работают бэкапы и restore (ручно выполнить восстановление из архива).
- [ ] Логи (`server/logs/`) и sqlite в бэкапе.

## T−0 — запуск

- [ ] `whitelist.json` или `enforce-whitelist=false` (осознанно!) — решить до запуска.
- [ ] Объявление запуска в Discord/TG с IP/SRV и правилами.
- [ ] Дежурный админ в чате.
- [ ] Сверкнуть `enforce-secure-profile=false`, `online-mode=false` (cracked).
- [ ] Первые 30 минут — следим за `/spark tps` и логами panel.

## T+24h — пост-лонч

- [ ] Разобраны инциденты (баны, роллбэки через Ledger).
- [ ] Проверяем, сколько RAM реально используется, подкручиваем `-Xmx` при необходимости.
- [ ] Aggregate-отчёт в audit-канал: сколько регистраций, фракционных баланс, пики онлайна.
- [ ] Бэкап и верификация restore выполнены ещё раз.
- [ ] Собрать feedback от игроков, завести иссьюс в GitHub.
