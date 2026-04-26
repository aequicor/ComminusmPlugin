---
genre: concept
title: Решение: использовать Gradle с KDSL
topic: decision-gradle-kotlin-dsl
confidence: high
source: human
updated: 2026-04
---

# Решение: использовать Gradle с KDSL

## Контекст

Необходимо было выбрать систему сборки для проекта. Альтернативы включали:
- Gradle с Groovy DSL (build.gradle)
- Gradle с Kotlin DSL (build.gradle.kts)
- Maven (pom.xml)
- Кастомные скрипты

## Решение

Использовать **Gradle с Kotlin DSL** (build.gradle.kts).

## Причины

1. **Типобезопасность** — Kotlin DSL предоставляет autocomplete и проверку типов при редактировании build-файлов

2. **Интеграция** — поскольку основной код на Kotlin, использование KDSL обеспечивает единый стиль

3. **Читаемость** — Kotlin-синтаксис понятен разработчикам проекта

4. **Мощность** — полная мощь Kotlin применима к конфигурации сборки

5. **Современный стандарт** — для Kotlin-проектов KDSL считается best practice

## Последствия

### Положительные
- ✅ Лучший developer experience при редактировании build-файлов
- ✅ Упрощенная поддержка (IDE autocomplete)
- ✅ Возможность рефакторинга build-скриптов
- ✅ Easier настройка кастомных задач (tasks)

### Отрицательные
- ⚠️ Немного больше boilerplate по сравнению с Groovy DSL
- ⚠️ Первый запуск сборки может быть медленнее (компиляция DSL)
- ⚠️ Некоторые плагины могут документироваться только для Groovy DSL

## Конфигурация

### Плагины
```kotlin
plugins {
    kotlin("jvm") version "2.3.20-RC3"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}
```

### Внешние зависимости
- Paper API: https://repo.papermc.io/repository/maven-public/
- Maven Central: стандартный репозиторий

---

**Статус**: Принято и реализовано
**Дата принятия**: 2026-04-26
**Реализация**: `build.gradle.kts`
