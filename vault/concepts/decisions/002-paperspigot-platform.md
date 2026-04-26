---
genre: concept
title: Решение: выбор PaperSpigot как платформы
topic: decision-paperspigot-platform
confidence: high
source: human
updated: 2026-04
---

# Решение: выбор PaperSpigot как платформы

## Контекст

При определении целевой платформы для ComminusmPlugin требовалось выбрать между разными серверными реализациями Minecraft:
- Vanilla (оригинальный сервер Mojang)
- Spigot (оптимизированный форк)
- Paper (оптимизированный форк от PaperMC)
- Кастомные реализации (CatServer, Pufferfish и др.)

## Решение

Выбрана **PaperSpigot** как целевая платформа.

## Причины

1. **Производительность** — Paper предоставляет значительную оптимизацию по сравнению с Vanilla

2. **API-стабильность** — Paper поддерживает стабильный API с backward compatibility

3. **Community поддержка** — широкое сообщество, активная разработка

4. **Совместимость** — Paper 1.21+ поддерживает нужную версию API (26.1.2-R0.1-SNAPSHOT)

5. **Run Paper plugin** — наличие `run-paper` Gradle plugin для удобной разработки

## Последствия

### Положительные
- ✅ Поддержка современных версий Minecraft (26.1.2)
- ✅ Хорошая документация и сообщество
- ✅ Поддержка API 1.21 (для будущих расширений)
- ✅ Легкая миграция на другие Paper-совместимые серверы

### Отрицательные
- ⚠️ Не поддерживает Fabric/Quilt моды
- ⚠️ Требует Java 21+ (но это уже в 2026 году норма)
- ⚠️ Paper API иногда имеет breaking changes между версиями

## Зависимости

Текущая конфигурация:
- Paper API: `io.papermc.paper:paper-api:26.1.2-R0.1-SNAPSHOT`
- PaperMC Maven: https://repo.papermc.io/repository/maven-public/

---

**Статус**: Принято и реализовано
**Дата принятия**: 2026-04-26
**Реализация**: `build.gradle.kts`, `src/main/resources/plugin.yml`
