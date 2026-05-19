# Changelog

All notable changes to **War Project** are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **WGuard Splash** — анимированный сплеш с пазл-значком, обводкой по периметру и подписью `WGuard Protected` на старте сайта и лаунчера.
- Полный военный редизайн сайта (`site/`) и лаунчера (`launcher/`): тёмная палитра, янтарные акценты, моноширинный/угловатый набор шрифтов (Rajdhani + Share Tech Mono).
- `LICENSE` (MIT) на уровне репозитория — выравнено с `gradle.properties`.
- `CHANGELOG.md`, `.editorconfig`.
- GitHub Actions CI (`.github/workflows/ci.yml`): сборка и тесты мода, сборка лаунчера, lint сайта.
- `docker/` — Dockerfile + docker-compose + entrypoint для серверной поставки (с разумными G1 JVM-флагами).
- `docs/PRODUCTION_READY.md` — полная сводка production-ready чек-листа.

### Changed
- README приведён к единой лицензии (MIT) и расширен инструкциями по запуску сайта, лаунчера и сервера.
- Сайт: статический `index.html` заменён на современный лендинг с навигацией, секциями контента, статусом сервера, мониторингом и футером.
- Лаунчер: компоненты `App/TitleBar/Sidebar/LoginOverlay/HomePage/SettingsPage` переписаны под новую дизайн-систему.

### Notes
- Дальнейшие шаги по чистке мёртвого Java-кода, аудиту багов мода и снятию TODO см. в `docs/PRODUCTION_READY.md`.

## [3.0.0] - 2026-XX-XX
- 31 блок: окопная фортификация (Okop), полевой лагерь, военная база (Baza).
- Soft-интеграция с JourneyMap.
- NeoForge 21.1.x, Minecraft 1.21.1, Java 21.
