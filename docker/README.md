# War Project — Docker server pack

Лёгкая обёртка для self-hosted сервера. Образ не содержит NeoForge и War Project mod — вы монтируете их сами, чтобы не нарушать лицензии и контролировать версии.

## Подготовка

```
mkdir -p data/world data/config data/logs data/mods
# Положите рядом server.jar (NeoForge installer-сборка) и сам мод в data/mods/warproject-*.jar
cp build/libs/warproject-*.jar data/mods/
```

## Запуск

```
EULA=true docker compose up -d
```

Ключевые тома:

- `data/world`   — мир (бэкапить!)
- `data/config`  — `server.properties`, NeoForge configs
- `data/mods`    — JARы модов (минимум — `warproject-*.jar`)
- `data/logs`    — логи

Для больших серверов укажите `JVM_OPTS` явно — стандартные флаги в `entrypoint.sh` рассчитаны на 4-8 ГБ хипа.
