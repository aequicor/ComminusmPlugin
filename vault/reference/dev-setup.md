---
genre: reference
title: Настройка среды разработки
triggers: ["dev setup", "developer environment", "build environment", "IDE"]
tags: ["setup", "devops", "reference"]
related: ["[[reference/configuration]]", "[[how-to/create-doc]]"]
version: "1.0-SNAPSHOT"
updated: "2026-04"
---

# Настройка среды разработки

## Требования

### Java
- JDK 21+ (целевая JVM: 25)
- [Download OpenJDK](https://jdk.java.net/21/)

### Gradle
- Gradle 8.5+
- Включается через Gradle wrapper (рекомендуется)

### IDE
Рекомендуемые IDE для Kotlin разработки:
- **IntelliJ IDEA** (Community Edition или Ultimate) — официальная поддержка Kotlin
- **Android Studio** — с плагином Kotlin
- **VS Code** — с расширением Kotlin

### PaperSpigot API
- Версия: 26.1.2-R0.1-SNAPSHOT
- Репозиторий: https://repo.papermc.io/repository/maven-public/

## Шаги установки

### 1. Клонирование репозитория
```bash
git clone https://github.com/kyamshanov/ComminusmPlugin.git
cd ComminusmPlugin
```

### 2. Основные команды Gradle

#### Сборка плагина
```bash
./gradlew build
```
Создает JAR файл в `build/libs/`.

#### Создание "fat JAR"
```bash
./gradlew shadowJar
```
Создает JAR со всеми зависимостями.

#### Запуск локального сервера
```bash
./gradlew runServer
```
Запускает PaperMC сервер для разработки и тестирования.

#### Очистка сборки
```bash
./gradlew clean
```
Удаляет файлы сборки.

### 3. Установка зависимостей (для IDE)

#### IntelliJ IDEA
1. Открыть проект
2. Gradle автоматически загрузит зависимости
3. Убедиться, что SDK настроен на Java 21+

#### VS Code
1. Установить расширение "Kotlin"
2. Открыть папку проекта
3. Использовать Gradle Task Runner

## Рабочий процесс

1. **Приступить** — запуск `./gradlew runServer`
2. **Кодить** — редактировать в `src/main/kotlin/`
3. **Пересобрать** — `./gradlew shadowJar` при изменении API
4. **Протестировать** — вручную или через JUnit

## Отладка

### Логирование
```
./gradlew runServer --info
```

### Дебг
```
./gradlew runServer --debug
```

### Проверка зависимостей
```bash
./gradlew dependencies
```

## Рекомендации

1. **Используйте Gradle wrapper** — избегайте глобальной установки Gradle
2. **Следуйте Kotlin конвенциям** — стиль кода, именование
3. **Тестируйте часто** — запускайте `./gradlew build` после изменений
4. **Читайте документацию** — вся документация в `vault/`
5. **Обновляйте документацию** — после каждом изменения кода

## Проблемы и решения

### Dependency not found
```bash
./gradlew clean build --refresh-dependencies
```

### Server not starting
Проверьте `papermcApiVersion` в `build.gradle.kts`

### Kotlin not recognized
Убедитесь, что установлен插件 `kotlin("jvm")`

## Конфигурация IDE

### Kotlin compiler
- Language version: 2.3
- API version: 2.3

### Gradle settings
- Use local gradle daemon: true
- Parallel execution: true
- Offline work: false (для первого запуска)

## Полезные команды

| Команда | Описание |
|---------|----------|
| `./gradlew tasks` | Список всех доступных задач |
| `./gradlew tasks --all` | Детальный список задач |
| `./gradlew properties` | Свойства проекта |
| `./gradlew build --dry-run` | Dry run сборки |

---

**Примечание**: Always consult `vault/` documentation before starting development work.
