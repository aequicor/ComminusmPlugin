---
genre: how-to
module: comminusm
title: "Stage 04 — OrderRespawnListener"
topic: order-home-spawn
stage: 4
status: DONE
date: 06.05.2026
related:
  - vault/concepts/comminusm/plans/order-home-spawn-plan.md
  - vault/reference/comminusm/spec/order-home-spawn.md
---

# Stage 04 — OrderRespawnListener

## Goal

Создать listener, который переопределяет точку спавна игрока при смерти: приоритет флаг ордера → кровать → мировой спавн.

## Files to Create

- `src/main/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListener.kt`

## Critical Corner Cases в этом этапе

| CC | Описание | Обработка |
|----|----------|-----------|
| CC-01 | Флаг уничтожается в том же тике что и смерть | Re-check isFlagActive перед установкой respawnLocation |
| CC-02 | Stale PDC (флаг деактивирован, запись осталась) | isFlagActive() как обязательная проверка |
| CC-03 | Смерть в Nether, флаг в Overworld | Передать world из flagLoc в respawnLocation |
| CC-04 | Chunk не загружен для точки флага | `world.getChunkAt(flagLoc).load(true)` до set |

## Implementation

```kotlin
package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.OrderService

class OrderRespawnListener(
    private val orderService: OrderService,
    private val flagStabilityManager: FlagStabilityManager,
) : Listener {

    // EventPriority.HIGH — после обработки кровати, перед HIGHEST/MONITOR
    // Это позволяет нам переопределить respawnLocation поверх кровати (AC-27)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        try {
            val player = event.player

            // Step 1: Найти ордер игрока через PDC (не через DB — Q4 из spec)
            // Читаем orderId из PDC игрока или его home flag chunk
            val order = orderService.findByOwner(player.uniqueId) ?: return  // AC-11: нет ордера → фолбэк

            // Step 2: Получить актуальную точку флага (CC-06: fresh read, не кэш)
            val flagLoc = flagStabilityManager.getFlagLocation(order.id) ?: return  // AC-10/AC-22: null → фолбэк

            // Step 3: Проверить активность флага (CC-01, CC-02: race condition guard)
            if (!flagStabilityManager.isFlagActive(order.id)) return  // фолбэк на кровать/мировой спавн

            // Step 4: Загрузить chunk синхронно (CC-04 — единственное допустимое исключение из правила no blocking)
            val world = flagLoc.world ?: return
            world.getChunkAt(flagLoc).load(true)

            // Step 5: Проверить мир (CC-03: смерть в Nether, флаг в Overworld — разрешено, явно указываем world)
            // Примечание: Paper поддерживает respawn в другом мире через PlayerRespawnEvent.respawnLocation.world
            // Мы используем flagLoc напрямую — он уже содержит правильный world.

            // Step 6: Установить точку спавна — переопределяет кровать (AC-27, EventPriority.HIGH)
            event.respawnLocation = flagLoc

        } catch (e: Exception) {
            // Никогда не re-throw — Bukkit применит стандартный respawn (фолбэк) (CC-01)
            // Логируем для диагностики
            plugin.logger.severe("OrderRespawnListener error for ${event.player.name}: ${e.message}")
        }
    }
}
```

### Почему EventPriority.HIGH?

- `NORMAL` — стандартная обработка (кровать)
- `HIGH` — наш listener переопределяет поверх кровати (AC-27: флаг > кровать)
- `HIGHEST` — зарезервирован для критичных override-ов других плагинов
- Если другой плагин на `HIGHEST` переопределит нашу точку — это штатное поведение серверной конфигурации

### PDC vs DB для получения orderId

Согласно Q4 из spec: `PlayerRespawnEvent` — синхронный handler на main thread. DB lookup здесь запрещён. Варианты:

**Вариант A:** `orderService.findByOwner(playerUuid)` — если OrderService хранит данные в памяти (cache), это допустимо.
**Вариант B:** Читать orderId из PDC на теле игрока или из chunk PDC.

Если `OrderService.findByOwner` выполняет синхронный DB-запрос — необходимо использовать in-memory cache. Это нужно проверить в существующем `OrderService` при реализации.

## Tests to Write

| TC | Scenario | Expected |
|----|----------|---------|
| TC-09 | Игрок (владелец) умирает, флаг активен | respawnLocation = flagLoc |
| TC-10 | Флаг уничтожен (getFlagLocation=null) | respawnLocation не изменён (Bukkit default) |
| TC-11 | Игрок без ордера умирает | respawnLocation не изменён |
| TC-25 | AC-22: isFlagActive=false, данные в PDC есть | respawnLocation не изменён (фолбэк) |
| TC-31 | AC-27: у игрока есть кровать И флаг | respawnLocation = flagLoc (флаг приоритетнее) |
| TC-34 | CC-01: флаг уничтожается в том же тике | isFlagActive() re-check → фолбэк |
| TC-35 | CC-02: stale PDC (флаг деактивирован) | isFlagActive()=false → фолбэк |
| TC-36 | CC-03: смерть в Nether, флаг в Overworld | respawnLocation.world = Overworld |
| TC-37 | CC-04: chunk не загружен | chunk.load(true) вызван до установки respawnLocation |

## Acceptance Criteria Covered

AC-09, AC-10, AC-11, AC-22, AC-27, CC-01, CC-02, CC-03, CC-04

## Definition of Done

- [ ] `OrderRespawnListener.kt` создан и компилируется
- [ ] Unit-тесты TC-09..11, TC-25, TC-31, TC-34..37 написаны и проходят
- [ ] `./gradlew compileKotlin` — PASS
- [ ] `./gradlew detekt ktlintCheck` — PASS
