---
genre: reference
title: Версионирование
triggers: ["version", "semver", "release", "tag"]
tags: ["process", "versioning", "reference"]
related: ["[[reference/build-tools]]", "[[concepts/architecture]]"]
version: "1.0-SNAPSHOT"
updated: "2026-04"
---

# Версионирование ComminusmPlugin

## Система версионирования

Проект использует **Semver 2.0.0** с небольшими изменениями для совместимости с Minecraft плагинами.

## Формат версии

```
MAJOR.MINOR.PATCH(-SNAPSHOT)
```

### MAJOR
- Изменения с breaking changes
- Обновление версии API PaperSpigot

### MINOR
- Новые функции backwards-compatible
- Добавление новых команд или конфигов
- Расширение API

### PATCH
- Баг-фиксы backwards-compatible
- Улучшения производительности
- Обновление зависимостей

### -SNAPSHOT
- Для разрабатываемых версий
- Не для production use
- Могут содержать breaking changes

## Примеры

| Версия | Описание |
|--------|----------|
| `1.0.0` | Первый стабильный релиз |
| `1.0.1-SNAPSHOT` | Разработка баг-фиксов |
| `1.1.0` | Новые функции, без breaking changes |
| `2.0.0` | Breaking changes |
| `1.0-SNAPSHOT` | Текущая разрабатываемая версия |

## Версия проекта в Gradle

Указана в `build.gradle.kts`:
```kotlin
version = "1.0-SNAPSHOT"
```

## Версия PaperSpigot API

Указана в `build.gradle.kts`:
```kotlin
val papermcApiVersion = "26.1.2-R0.1-SNAPSHOT"
```

## Версия Minecraft

Указана в `plugin.yml`:
```yaml
api-version: '1.21'
```

## Нумерация релизов

### Релизная версия
1. Обновить `version` в `build.gradle.kts`
2. Убрать `-SNAPSHOT` суффикс
3. Создать Git тег `vX.Y.Z`
4. Создать Release на GitHub
5. Обновить документацию

### Beta версия
1. Оставить `-SNAPSHOT` суффикс
2. Создать Git тег `vX.Y.Z-beta`
3. Создать Release с пометкой "Pre-release"

## Обновление версии

### Минорное обновление
```bash
./gradlew build -Dversion=1.1.0
./gradlew shadowJar -Dversion=1.1.0
```

### Патч версия
```bash
./gradlew build -Dversion=1.0.1
./gradlew shadowJar -Dversion=1.0.1
```

## Tagging стратегия

###主ные теги
- `vX.Y.Z` — стабильный релиз
- `vX.Y.Z-rc.N` — release candidate
- `vX.Y.Z-beta` — beta версия

### CI теги
- `vX.Y.Z-ci.N` — continuous integration сборка

## Backwards compatibility

### Гарантированы
- API плагина
- Конфигурационные файлы
- Базы данных

### Не гарантированы
- Internal API
- Загрузка旧 версий плагина
- Файлы из `build/`

## Release process

1. Создать ветку `release/vX.Y.Z`
2. Обновить версию и CHANGELOG
3. Создать PR и объединить в main
4. Создать Git тег и Release
5. Обновить документацию
6. Сообщить сообществу

---

**Примечание**: Всегда обновляйте документацию при изменении API или конфигурации.
