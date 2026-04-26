---
genre: guideline
title: minecraft-plugin-architecture
topic: minecraft-plugin-architecture
library: Minecraft Paper API
confidence: high
source: agent
updated: 2026-04
related:
  - concepts/architecture
---

# Архитектура Minecraft плагина

## Общие принципы

- Пакеты именуются по reverse domain: `io.github.{username}.{project}`
- Главный класс расширяет `JavaPlugin`
- `plugin.yml` располагается в `src/main/resources/`

## Рекомендуемая структура пакетов

```
src/main/kotlin/{group}/{project}/
├── plugin/                    # Главный класс плагина
├── command/                   # Команды (/команда)
├── event/                     # Обработчики событий
├── data/                      # Работа с данными (BDC, YAML)
├── config/                    # Конфигурация
├── api/                       # Публичный API (если есть)
└── util/                      # Утилиты, хелперы
```

## Пример для ComminusmPlugin

```
ru/kyamshanov/comminusm/
├── ComminusmPlugin.kt         # plugin/ - главный класс
├── command/                   # command/
│   └── SimpleCommand.kt
├── event/                     # event/
│   └── PlayerJoinHandler.kt
├── config/                    # config/
│   └──PluginConfig.kt
└── util/                      # util/
    └── MessageUtils.kt
```

## Требования к именованию

| Сущность | Префикс | Пример |
|----------|---------|--------|
| Команда | *Command | GiveCommand |
| Событие | *Handler | PlayerJoinHandler |
| Конфиг | *Config | PluginConfig |
| Событие | *Event | BlockBreakEvent |
## Связанные документы
- [[concepts/architecture]]