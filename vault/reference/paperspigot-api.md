---
genre: reference
title: PaperSpigot API
triggers: ["paper", "spigot", "bukkit", "api", "minecraft"]
tags: ["api", "reference", "paper"]
related: ["[[concepts/architecture]]", "[[reference/configuration]]"]
version: "26.1.2-R0.1-SNAPSHOT"
updated: "2026-04"
---

# PaperSpigot API参考

## Основные пакеты

### org.bukkit
Базовый API для работы с Minecraft объектами:
- `org.bukkit.Bukkit` — основной доступ к серверу
- `org.bukkit.entity.*` — все типы сущностей
- `org.bukkit.block.*` — блоки и строения
- `org.bukkit.material.*` — материалы блоков

### io.papermc.paper
Дополнительные фичи PaperSpigot API:
- `io.papermc.paper.player.*` — расширенные возможности игроков
- `io.papermc.paper.util.*` — утилиты и вспомогательные классы

### org.bukkit.plugin
Разработка плагинов:
- `org.bukkit.plugin.java.JavaPlugin` — базовый класс плагина
- `org.bukkit.plugin.Plugin` — интерфейс плагина
- `org.bukkit.command.*` — работа с командами

## Основные классы

### JavaPlugin
Базовый класс всех плагинов:

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        // Initialization
    }
    
    override fun onDisable() {
        // Cleanup
    }
}
```

### Плагин
Интерфейс всех плагинов:

```kotlin
interface Plugin {
    val name: String
    fun onEnable()
    fun onDisable()
}
```

## Работа с событиями

### Listener
Интерфейс для слушателей событий:

```kotlin
class MyListener : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Обработка
    }
}
```

### Регистрация
```kotlin
server.pluginManager.registerEvents(MyListener(), pluginInstance)
```

## Рекомендации

1. **Используйте Kotlin** — все новые коды на Kotlin
2. **Обрабатывайте Exception** — всегда логируйте ошибки
3. **Избегайте blocking** — не используйте同步 I/O операции
4. **Следуйте API** — не полагайтесь на internal API
5. **Тестируйте** — всегда тестируйте на PaperSpigot

## Ресурсы

- [PaperAPI Docs](https://papermc.io/javadocs/)
- [Spigot Docs](https://hub.spigotmc.org/javadocs/bukkit/)
- [PaperMC GitHub](https://github.com/PaperMC/Paper)
