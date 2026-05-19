# Production-Ready Checklist — War Project

Этот документ фиксирует объём задач до полного production-ready состояния. Пункты помечены приоритетом: **C** — critical, **I** — important, **N** — nice-to-have.

## 1. Структура и качество кода

- [x] **I** README приведён к фактическому scope репозитория: сейчас здесь мод, серверные конфиги, Docker/Pterodactyl и docs; launcher/site должны поставляться отдельно или добавляться позже.
- [ ] **C** Прогон всех Java-классов мода через ревью: null-safety, неосвобождённые ресурсы, утечки `BlockEntity`/`Container`, `@Nullable`/`@Nonnull`. Фокус: `persistence/`, `network/`, `server/`, `client/jmplugin/`.
- [ ] **I** Удаление мёртвого кода в `block/` и `client`. Нужен сплошной проход с `gradlew check` + IDE-инспекции.
- [ ] **I** Проверка, что нет хардкода путей и магических чисел вне `WpConfig.java`.
- [ ] **N** ErrorProne / Checkstyle / Spotless в `build.gradle`.

## 2. Зависимости

- [ ] **C** Сканер уязвимостей (`dependencyCheckAnalyze` / Dependabot / Renovate).
- [ ] **I** Перевести `journeymap-api` со SNAPSHOT на релизный тэг, как только появится.
- [ ] **I** `sqlite-jdbc` и `jbcrypt` — жёсткий pin patch-версии.
- [ ] **N** `.github/dependabot.yml` для gradle и actions.

## 3. Конфигурация

- [x] Конфиг мода вынесен в `WpConfig.java`.
- [x] `server/server.properties`: RCON выключен по умолчанию, чтобы случайно не запустить сервер с пустым `rcon.password`.
- [ ] **C** Реальные координаты мира: `captcha`, `factions.zarnaviaSpawn`, `factions.chernogryadSpawn`, `factions.choiceHallSpawn`, `regions.bases`.
- [ ] **C** Все секреты (DB-пароли, RCON, JWT/OAuth при появлении сайта/лаунчера) — только через ENV / panel variables / `.env`, не в git.
- [ ] **I** Профили: `dev / staging / prod`, отдельные `.env.example` для будущих launcher/site.

## 4. Безопасность (WGuard / античит)

- [x] **C** Новая DB-auth использует bcrypt cost-factor 12.
- [x] **C** Legacy JSON-профили после успешного входа автоматически мигрируются с SHA-256+salt на bcrypt cost 12.
- [x] **I** Legacy login minimum password length поднят до 8 символов.
- [ ] **C** Подтвердить сборкой и тестами, что bcrypt-миграция legacy-профилей проходит без регрессий.
- [ ] **C** Подпись и проверка целостности сборки лаунчера — актуально после добавления launcher в репозиторий/релизный pipeline.
- [ ] **C** launcher ↔ site ↔ server — только HTTPS/WSS, HSTS, короткий TTL токенов — актуально после добавления site/launcher.
- [ ] **I** WGuard: HWID, server-side валидация всех команд, packet-limit.
- [ ] **I** Отдельный combat/fly/killaura античит или проверенный внешний античит под NeoForge 1.21.1.
- [ ] **N** Audit-log + алерты по подозрительным событиям.

## 5. Тесты

- [x] Test stack: JUnit5, jqwik, AssertJ, Mockito, H2.
- [ ] **C** Локально или в CI выполнить `./gradlew clean build` и `./gradlew test` на ветке `01u16jspaspjgna`.
- [ ] **C** `jacoco` + минимум 60% по `persistence/` и `network/`.
- [ ] **I** Integration-тесты сервера на H2 / встроенной SQLite.
- [ ] **N** E2E: launcher → site → server, когда launcher/site будут добавлены.

## 6. CI/CD

- [ ] **C** Активировать CI workflow: скопировать `docs/ci-template.yml` в `.github/workflows/ci.yml`. Через GitHub App/API это сделать нельзя — нет `workflows` permission.
- [x] `docs/ci-template.yml` приведён к текущему scope репозитория: только Gradle mod build/test, без несуществующих `launcher/` и `site/` jobs.
- [ ] **I** Release workflow: на тег `v*` — сборка mod jar и публикация в Releases.
- [ ] **I** SLSA-провенанс артефактов.
- [ ] **N** Codecov / Coveralls.

## 7. Производительность

- [ ] **I** Профилирование (Spark / JFR), MSPT < 25 мс при 20 игроках.
- [ ] **I** Рендер (`client/`) — нет аллокаций в `render()` / `tick()`.
- [ ] **N** LRU-кеш поверх SQLite в `persistence/`.

## 8. Документация

- [x] README расширен и синхронизирован с текущей структурой репозитория.
- [x] CHANGELOG, LICENSE.
- [ ] **I** `docs/ARCHITECTURE.md` — модули и их взаимодействие.
- [ ] **I** OpenAPI/Swagger для API сайта после добавления сайта.
- [ ] **N** Скриншоты UI.

## 9. Мониторинг и логи

- [ ] **C** Health endpoint у Java-сервера / внешний healthcheck Pterodactyl/Docker.
- [ ] **I** Структурированные логи (JSON).
- [ ] **I** Prometheus метрики (TPS, MSPT, online, latency).
- [ ] **N** Алерты в Discord / Telegram.

## 10. Deployment

- [x] `docker/Dockerfile.server` + compose.
- [x] `.jqwik-database` удалён из репозитория и добавлен в `.gitignore`.
- [ ] **C** Проверить Docker/Pterodactyl запуск на реальном сервере.
- [ ] **I** systemd-юнит для bare-metal.
- [ ] **I** Reverse-proxy (nginx/Caddy) + SSL Let's Encrypt, если появятся web-компоненты.
- [ ] **I** Pterodactyl egg import + install test.
- [ ] **N** Backup-ротация `world/` (час / 7 дней).

---

## Осталось для production-open

1. Заполнить реальные координаты мира и regions.
2. Активировать CI вручную через `.github/workflows/ci.yml`.
3. Прогнать `./gradlew clean build` и `./gradlew test`.
4. Проверить dedicated server boot.
5. Проверить Docker/Pterodactyl install.
6. Провести stress-test с Spark.
7. Настроить companion-моды, бэкапы и restore.
