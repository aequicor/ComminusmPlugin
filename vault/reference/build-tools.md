---
genre: reference
title: Инструменты сборки
triggers: ["build", "shadowjar", "run-paper", "gradle", "tasks"]
tags: ["build", "tools", "reference"]
related: ["[[reference/configuration]]", "[[concepts/decisions/003-gradle-kotlin-dsl]]"]
version: "1.0-SNAPSHOT"
updated: "2026-04"
---

# Инструменты сборки

## Shadow JAR

### Назначение
Создает "fat JAR" с включенными всеми зависимостями для деплоя на сервер.

### Команда
```bash
./gradlew shadowJar
```

### Результат
Файл создается в `build/libs/` с префиксом `-all.jar`

### Зависимости
- `shadowJar` задача зависит от `build`
- Автоматически запускается при `gradle build`

## Run Paper

### Назначение
Запускает локальный Minecraft сервер Paper для разработки и тестирования.

### Команда
```bash
./gradlew runServer
```

### Настройки
Конфигурируется в `build.gradle.kts`:
```kotlin
tasks.runServer {
    minecraftVersion("26.1.2")
}
```

## Сборка проекта

### Полная сборка
```bash
./gradlew build
```
Выполняет:
1. Компиляцию Kotlin кода
2. Запуск tests (если есть)
3. Создание JAR
4. Создание Shadow JAR (через зависимость)

### Очистка
```bash
./gradlew clean
```
Удаляет содержимое директории `build/`

## Gradle задачи

### Список задач
```bash
./gradlew tasks
```

### Дебаг
```bash
./gradlew --info
```

### Отладка сборки
```bash
./gradlew --debug
```

## Мобильные версии

| Версия | Команда |
|--------|---------|
| Paper 1.21 | `minecraftVersion("26.1.2")` |
| Paper 1.20.1 | `minecraftVersion("20.1")` |
| Paper 1.19.4 | `minecraftVersion("19.4")` |

---

**Примечание**: Обновляйте версию Paper API при смене целевой версии Minecraft.
