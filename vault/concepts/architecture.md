---
genre: concept
title: Архитектура проекта ComminusmPlugin
topic: architecture
confidence: high
source: human
updated: 2026-04
---

# Архитектура ComminusmPlugin

## Обзор

ComminusmPlugin — это сатирический плагин для серверов Minecraft на платформе PaperSpigot, реализующий геймификацию идеологии коммунизма в игровом мире. Плагин разработан на Kotlin с использованием современного стека зависимостей через Gradle.

## Стек технологий

### Язык программирования
- **Kotlin** (версия 2.3.20-RC3) — основной язык для бизнес-логики
- Java 25 — целевая версия JVM

### Фреймворк и API
- **PaperSpigot API** (версия 26.1.2-R0.1-SNAPSHOT) — основной API для Minecraft серверов
- **JavaPlugin** — базовый класс плагина

### Сборка и развертывание
- **Gradle** — система сборки
- **Shadow plugin** (8.3.0) — для созданияFat JAR со всеми зависимостями
- **Run paper plugin** (2.3.1) — инструмент для локального запуска сервера в开发-среде

## Структура проекта

```
ComminusmPlugin/
├── src/main/
│   ├── kotlin/ru/kyamshanov/minecraft/comminusmPlugin/
│   │   └── ComminusmPlugin.kt     # Главный класс плагина
│   └── resources/
│       ├── plugin.yml              # Конфигурация плагина
│       └── (доп. ресурсы)
├── vault/                          # Документация (Diátaxis)
│   ├── _INDEX.md
│   ├── concepts/                   # Концепции и ADR
│   │   ├── architecture.md
│   │   └── decisions/
│   ├── guidelines/                 # Правила и гайдлайны
│   ├── how-to/                     # Пошаговые инструкции
│   └── reference/                  # Справочная информация
├── build.gradle.kts
└── settings.gradle.kts
```

## Основные компоненты

### ComminusmPlugin.kt
 Главный класс, унаследованный от `JavaPlugin`. Реализует круговой жизненный цикл плагина через методы `onEnable()` и `onDisable()`.

### plugin.yml
Минимальная конфигурация плагина с указанием:
- Имени: ComminusmPlugin
- Версии: 1.0-SNAPSHOT
- Главного класса: ru.kyamshanov.minecraft.comminusmPlugin.ComminusmPlugin
- API-версии: 1.21

## Архитектурные принципы

1. **Один источник истины** — весь код находится в `src/main/kotlin/`
2. **Без зависимостей на уровне кода** — только PaperSpigot API
3. **Управление через Build System** — все зависимости описываются в `build.gradle.kts`
4. **Документированность** — все важные решения и концепции фиксируются в `vault/`

## Связанные документы

- [[concepts/decisions/001-kotlin-implementation|Решение: Kotlin]] — выбор языка программирования
- [[concepts/decisions/002-paperspigot-platform|Решение: PaperSpigot]] — выбор серверной платформы
- [[concepts/decisions/003-gradle-kotlin-dsl|Решение: Gradle KDSL]] — система сборки
- [[guidelines/documentation-structure|Структура документации]] — фреймворк Diátaxis
- [[reference/configuration|Конфигурация]] — файлы build и settings

Проект находится на начальной стадии: заготовка плагина с базовой структурой. Функциональность пока отсутствует.

---

*Дисклеймер: Плагин является сатирой и не предназначен для серьезного использования на продакшн-серверах.*
