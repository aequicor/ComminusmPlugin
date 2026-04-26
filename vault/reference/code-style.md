---
genre: reference
title: Стиль кода
triggers: ["code style", "kotlin style", "naming", "formatting"]
tags: ["conventions", "style", "reference"]
related: ["[[guidelines/documentation-structure]]", "[[reference/plugin-class]]"]
version: "1.0-SNAPSHOT"
updated: "2026-04"
---

# Стиль кода ComminusmPlugin

## Общие принципы

1. **Язык**: Kotlin
2. **Стиль**: следование официальным рекомендациям Kotlin
3. **Пакет**: `ru.kyamshanov.minecraft.comminusmPlugin`
4. **Файлы**: `.kt` расширение
5. **Именование**: camelCase для классов, snake_case для переменных

## Пакет и структура

### Пакет
```kotlin
package ru.kyamshanov.minecraft.comminusmPlugin
```

### Импорты
Классы импортируются в алфавитном порядке по пакетам.

### Комментарии
- Использовать `//` для строковых комментариев
- Использовать `/** */` для KDoc документации
- Комментировать сложные алгоритмы
- Избегать очевидных комментариев

## Именование

### Классы
- `PascalCase`: `ComminusmPlugin`, `PlayerManager`, `CommandHandler`

### Функции
- `camelCase`: `onEnable()`, `registerEvents()`, `calculateResource()`

### Константы
- `UPPER_SNAKE_CASE`: `MAX_RESOURCES`, `PLUGIN_NAME`

### Переменные
- `camelCase`: `resourceCount`, `playerScore`, `serverConfig`

### Пакеты
- `snake_case` (согласно Kotlin conventions)

## Форматирование

### Отступы
- 4 пробела на уровень
- Никаких табов в исходном коде

### Строки
- Максимальная длина строки: 120 символов
- Автоматический перенос при вызовах методов

### Код
```kotlin
class MyPlugin : JavaPlugin() {
    private var counter: Int = 0

    override fun onEnable() {
        val name = "MyPlugin"
        logMessage(name)
    }

    private fun logMessage(message: String) {
        logger.info(message)
    }
}
```

## KDoc документация

### Классы
```kotlin
/**
 * Основной класс плагина.
 * Отвечает за инициализацию и управление всеми компонентами.
 *
 * @author kyamshanov
 * @version 1.0-SNAPSHOT
 */
class ComminusmPlugin : JavaPlugin() {
    // ...
}
```

### Методы
```kotlin
/**
 * Регистрирует слушатели событий плагина.
 *
 * @param pluginInstance экземпляр плагина
 */
fun register Events(pluginInstance: Plugin) {
    // ...
}
```

## Exceptions

- Exceptions должны быть валидированы через `try-catch`
- Логировать все exceptions с контекстом
- Не использовать `catch (Exception: e)` без необходимости

## Test Style

- Использовать JUnit 5 для тестирования
- Именование тестов: `testMethodName_State_ExpectedResult`
- Использовать `@Test` аннотации

## Kotlin Specifix

### Использовать
- ✅ `val` вместо `var` при возможности
- ✅ `lateinit` для инициализации в `onEnable()`
- ✅ Extension functions для упрощения API
- ✅ Data classes для DTO
- ✅ Coroutines для async операций

### Избегать
- ❌ Implicit types unless clearly obvious
- ❌ Deep inheritance hierarchies
- ❌ Global state
- ❌ Blocking I/O operations

## Build Configurations

### gradle.properties
```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.daemon=true
```

## Чек-лист перед коммитом

1. ✅ Код отформатирован по стилю
2. ✅ KDoc добавлен для публичных API
3. ✅ Без логов отладки ( println )
4. ✅ Обработаны все возможные exceptions
5. ✅ Тесты запущены (`./gradlew test`)
6. ✅ Сборка прошла (`./gradlew build`)

---

**Примечание**: Stylе code не фиксирован — следуйте конвенциям Kotlin и PaperSpigot API.
