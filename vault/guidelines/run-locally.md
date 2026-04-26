---
genre: guideline
title: run-locally
topic: run-locally
confidence: high
source: agent
updated: 2026-04
---

---
genre: how-to
title: Запуск плагина локально
triggers: ["запуск", "local", "тестирование", "run server", "разработка"]
tags: ["testing", "development", "run"]
related: [[reference/dev-setup], [reference/configuration]]
version: "1.0"
updated: "2026-04"
---

# Запуск плагина локально

## Предварительные требования

1. **JDK 21+** — [скачать OpenJDK](https://jdk.java.net/21/)
2. **Gradle Wrapper** — уже включён в проект
3. **Оперативная память** — минимум 4GB свободно

## Быстрый старт

### Шаг 1: Запуск сервера

Выполните команду:

```bash
./gradlew runServer
```

Это запустит локальный PaperMC сервер с установленным плагином.

### Шаг 2: Подключение к серверу

1. Откройте Minecraft (версия 1.21+)
2. Перейдите в multiplayer
3. Добавьте сервер: `localhost:25565`
4. Подключитесь

## Конфигурация

### Изменение версии Minecraft

Отредактируйте `build.gradle.kts`:

```kotlin
tasks.runServer {
    minecraftVersion("1.21.4") // или другая версия
}
```

### Настройка памяти

Добавьте в `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4G -XX:+UseG1GC
```

### Автоперезагрузка плагина

При каждом запуске `./gradlew runServer` плагин автоматически компилируется и загружается.

## Остановка сервера

В консоли сервера введите:

```
stop
```

## Устранение проблем

### Сервер не запускается

- Убедитесь, что порт 25565 свободен
- Проверьте версию Java: `java -version`

### Плагин не загружается

- Проверьте логи в консоли
- Убедитесь, что JAR собран: `./gradlew shadowJar`

### Ошибки памяти

Увеличьте выделенную память в `gradle.properties`.

## Рекомендации

1. Используйте отдельный профиль Minecraft для тестирования
2. Регулярно перезапускайте сервер для чистого состояния
3. Используйте `/reload` в игре для быстрой перезагрузки (если поддерживается)