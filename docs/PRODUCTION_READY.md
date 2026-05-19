# Production-Ready Checklist — War Project

Этот документ фиксирует объём задач до полного production-ready состояния. Пункты помечены приоритетом: **C** — critical, **I** — important, **N** — nice-to-have.

## 1. Структура и качество кода

- [x] **I** Военный редизайн UI лаунчера и сайта; WGuard splash.
- [ ] **C** Прогон всех Java-классов мода через ревью: null-safety, неосвобождённые ресурсы, утечки `BlockEntity`/`Container`, `@Nullable`/`@Nonnull`. Фокус: `persistence/`, `network/`, `server/`, `client/jmplugin/`.
- [ ] **I** Удаление мёртвого кода в `block/` и `client/`. Сейчас не выполнено — нужен сплошной проход с `gradlew check` + IDE-инспекции.
- [ ] **I** Проверка, что нет хардкода путей и магических чисел вне `WpConfig.java`.
- [ ] **N** ErrorProne / Checkstyle / Spotless в `build.gradle`.

## 2. Зависимости

- [ ] **C** Сканер уязвимостей (`dependencyCheckAnalyze` / Dependabot / Renovate).
- [ ] **I** Перевести `journeymap-api` со SNAPSHOT на релизный тэг, как только появится.
- [ ] **I** `sqlite-jdbc` и `jbcrypt` — жёсткий pin patch-версии.
- [ ] **N** `.github/dependabot.yml` для gradle, npm (launcher, site), actions.

## 3. Конфигурация

- [x] Конфиг мода вынесен в `WpConfig.java`.
- [ ] **C** Все секреты (DB-пароли, JWT, OAuth) — только через ENV / `.env`. Проверить `persistence/` и `server/`.
- [ ] **I** Профили: `dev / staging / prod`, отдельные `.env.example` для launcher и site.

## 4. Безопасность (WGuard / античит)

- [ ] **C** Подпись и проверка целостности сборки лаунчера (`electron-builder` + nsis sign).
- [ ] **C** launcher ↔ site ↔ server — только HTTPS/WSS, HSTS, короткий TTL токенов.
- [ ] **C** bcrypt cost-factor >= 12.
- [ ] **I** CSP в `launcher/src/index.html` — добавлен; `connect-src` под прод-домен.
- [ ] **I** Rate-limit, CSRF, XSS, SQLi в `site/server.js` (helmet, express-rate-limit).
- [ ] **I** WGuard: HWID, server-side валидация всех команд, packet-limit.
- [ ] **N** Audit-log + алерты по подозрительным событиям.

## 5. Тесты

- [x] Test stack: JUnit5, jqwik, AssertJ, Mockito, H2.
- [ ] **C** `jacoco` + минимум 60% по `persistence/` и `network/`.
- [ ] **I** Integration-тесты сервера на H2 / встроенной SQLite.
- [ ] **N** E2E: launcher → site → server (Playwright).

## 6. CI/CD

- [ ] **C** Активировать CI workflow: скопируйте `docs/ci-template.yml` в `.github/workflows/ci.yml`. Через API этого сделать нельзя — у GitHub App нет права `workflows`.
- [ ] **I** Release workflow: на тег `v*` — сборка mod jar + лаунчер (win/mac/linux), публикация в Releases.
- [ ] **I** SLSA-провенанс артефактов.
- [ ] **N** Codecov / Coveralls.

## 7. Производительность

- [ ] **I** Профилирование (Spark / JFR), MSPT < 25 мс при 20 игроках.
- [ ] **I** Рендер (`client/`) — нет аллокаций в `render()` / `tick()`.
- [ ] **N** LRU-кеш поверх SQLite в `persistence/`.

## 8. Документация

- [x] README расширен.
- [x] CHANGELOG, LICENSE.
- [ ] **I** `docs/ARCHITECTURE.md` — модули и их взаимодействие.
- [ ] **I** OpenAPI/Swagger для API сайта.
- [ ] **N** Скриншоты UI.

## 9. Мониторинг и логи

- [ ] **C** Health endpoint у `site/server.js` и Java-сервера (`/healthz`, `/readyz`).
- [ ] **I** Структурированные логи (JSON).
- [ ] **I** Prometheus метрики (TPS, MSPT, online, latency).
- [ ] **N** Алерты в Discord / Telegram.

## 10. Deployment

- [x] `docker/Dockerfile.server` + compose.
- [ ] **I** systemd-юнит для bare-metal.
- [ ] **I** Reverse-proxy (nginx/Caddy) + SSL Let's Encrypt.
- [ ] **I** Pterodactyl egg.
- [ ] **N** Backup-ротация `world/` (час / 7 дней).

---

## Что НЕ было сделано в текущем пакете и почему

1. **Глубокий аудит всех Java-классов** — в `src/main/java/com/frostlogic/warproject/` десятки файлов. Без `gradlew check` и IDE-инспекций рискуем сломать registry/mixin/рефлексию — файлы остались как есть.
2. **Удаление `.jqwik-database`** — локальный кэш jqwik. Следует добавить в `.gitignore` и удалить отдельным коммитом.
3. **Активация CI workflow** — GitHub App не имеет `workflows` permission, файл лежит в `docs/ci-template.yml`, перенести в `.github/workflows/ci.yml` нужно вручную.
4. **Обновление всех компонентов лаунчера/сайта** — часть сделана, часть требует увеличения бюджета или push_files (запрещён интеграцией).
5. **Один большой коммит** — невозможен: `push_files` API запрещён интеграцией (403 Resource not accessible by integration). Каждый файл приходится сохранять отдельным коммитом.
