---
genre: reference
title: Конфигурация проекта
triggers: ["config", "build", "gradle", "settings"]
tags: ["configuration", "setup", "reference"]
related: ["[[concepts/architecture]]"]
version: "1.0-SNAPSHOT"
updated: "2026-04"
---

# Конфигурация проекта

## build.gradle.kts

Основной файл конфигурации сборки. Основные настройки:

### Плагины
```kotlin
plugins {
    kotlin("jvm") version "2.3.20-RC3"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}
```

### Зависимости
```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}
```

### Репозитории
- Maven Central (標準)
- PaperMC Maven: https://repo.papermc.io/repository/maven-public/

### Запуск сервера
```kotlin
tasks.runServer {
   minecraftVersion("26.1.2")
}
```

## settings.gradle.kts

Конфигурация корневого проекта:

```kotlin
rootProject.name = "ComminusmPlugin"
```

## plugin.yml

Конфигурация плагина Minecraft:

```yaml
name: ComminusmPlugin
version: '1.0-SNAPSHOT'
main: ru.kyamshanov.minecraft.comminusmPlugin.ComminusmPlugin
api-version: '1.21'
```

## Ключевые переменные

| Переменная | Значение | Описание |
|------------|----------|----------|
| `group` | `ru.kyamshanov.minecraft` | Группа Maven координат |
| `version` | `1.0-SNAPSHOT` | Версия проекта |
| `papermcApiVersion` | `26.1.2-R0.1-SNAPSHOT` | Версия Paper API |
| `targetJavaVersion` | `25` | Целевая версия JVM |

## Структура Maven Coordinates

```
GroupId: ru.kyamshanov.minecraft
ArtifactId: ComminusmPlugin
Version: 1.0-SNAPSHOT
```
