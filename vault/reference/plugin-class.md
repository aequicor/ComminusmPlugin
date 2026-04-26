---
genre: reference
title: Основной класс плагина
triggers: ["plugin class", "main class", "comminusmplugin", "javaplugin"]
tags: ["code", "api", "reference"]
related: ["[[concepts/architecture]]", "[[reference/configuration]]"]
version: "1.0-SNAPSHOT"
updated: "2026-04"
---

# Основной класс ComminusmPlugin

## Расположение
```
src/main/kotlin/ru/kyamshanov/minecraft/comminusmPlugin/ComminusmPlugin.kt
```

## Пакет
```kotlin
package ru.kyamshanov.minecraft.comminusmPlugin
```

## Класс
```kotlin
class ComminusmPlugin : JavaPlugin()
```

## Методы

### onEnable()
```kotlin
override fun onEnable() {
    // Логика запуска плагина
}
```
- Выполняется при включении плагина
- Используется для инициализации команд, слушателей событий и其它 компонентов

### onDisable()
```kotlin
override fun onDisable() {
    // Логика выключения плагина
}
```
- Выполняется при отключении плагина
- Используется для очистки ресурсов, сохранения данных и других финализаций

## Структура

```kotlin
package ru.kyamshanov.minecraft.comminusmPlugin

import org.bukkit.plugin.java.JavaPlugin

class ComminusmPlugin : JavaPlugin() {

    override fun onEnable() {
        // Plugin startup logic
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
```

## Текущее состояние

Проект находится на начальной стадии: базовая структура определена, но логика методов `onEnable()` и `onDisable()` еще не реализована.

---

**Совет**: При добавлении функциональности, следуйте принципу "одна ответственность" и разделяйте логику по отдельным классам.
