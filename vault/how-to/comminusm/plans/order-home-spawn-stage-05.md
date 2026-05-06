---
genre: how-to
module: comminusm
title: "Stage 05 — FlagEventListener + Plugin Wiring"
topic: order-home-spawn
stage: 5
status: DONE
date: 06.05.2026
related:
  - vault/concepts/comminusm/plans/order-home-spawn-plan.md
  - vault/reference/comminusm/spec/order-home-spawn.md
---

# Stage 05 — FlagEventListener + Plugin Wiring

## Goal

1. Создать `FlagEventListener` — подписка на кастомные события flag-stability для отмены таймеров.
2. Зарегистрировать все новые listener-ы и `HomeTimerManager` в `ComminusmPlugin.kt`.
3. Вызвать `homeTimerManager.onDisable()` при остановке плагина (CC-05).

## Files to Create / Modify

- `src/main/kotlin/ru/kyamshanov/comminusm/listener/FlagEventListener.kt` — **новый файл**
- `src/main/kotlin/ru/kyamshanov/comminusm/ComminusmPlugin.kt` — **модификация**: регистрация listener-ов

## Part 1: FlagEventListener

```kotlin
package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import ru.kyamshanov.comminusm.event.FlagRelocatedEvent
import ru.kyamshanov.comminusm.service.CancelReason
import ru.kyamshanov.comminusm.service.HomeTimerManager

class FlagEventListener(private val homeTimerManager: HomeTimerManager) : Listener {

    // AC-13: флаг деактивирован/уничтожен — отменить все таймеры этого ордера
    @EventHandler(priority = EventPriority.MONITOR)
    fun onFlagDeactivated(event: FlagDeactivatedEvent) {
        homeTimerManager.cancelTimersForOrder(event.orderId, CancelReason.FLAG_DEACTIVATED)
    }

    // AC-18 / AC-18a: флаг relocated
    @EventHandler(priority = EventPriority.MONITOR)
    fun onFlagRelocated(event: FlagRelocatedEvent) {
        // AC-18a: если перемещён в другой мир — отменить таймеры
        if (event.oldWorld != event.newWorld) {
            homeTimerManager.cancelTimersForOrder(event.orderId, CancelReason.FLAG_WORLD_CHANGED)
        }
        // AC-18: тот же мир — таймер НЕ прерывается; executeHomeTP прочитает новые координаты из PDC
    }
}
```

### Кастомные события от flag-stability

Необходимо убедиться, что следующие события существуют (или создать их в пакете `event/`):

**FlagDeactivatedEvent:**
```kotlin
package ru.kyamshanov.comminusm.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class FlagDeactivatedEvent(val orderId: Long) : Event() {
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
    override fun getHandlers() = HANDLERS
}
```

**FlagRelocatedEvent:**
```kotlin
package ru.kyamshanov.comminusm.event

import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class FlagRelocatedEvent(
    val orderId: Long,
    val oldWorld: String,
    val newWorld: String,
    val newLocation: Location,
) : Event() {
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
    override fun getHandlers() = HANDLERS
}
```

**Важно:** Если эти события уже существуют в flag-stability — использовать существующие. Если нет — создать в пакете `event/` и вызывать `Bukkit.getPluginManager().callEvent(FlagDeactivatedEvent(orderId))` из соответствующих методов flag-stability при деактивации/уничтожении флага.

Проверить в `FlagStabilityManager` или `BlockListener` — где именно деактивируется флаг — и добавить вызов события там.

## Part 2: Plugin Wiring (ComminusmPlugin.kt)

В `onEnable()` добавить после инициализации существующих сервисов:

```kotlin
// Инициализация HomeTimerManager
val homeTimerManager = HomeTimerManager(this)

// Регистрация listener-ов
val pluginManager = server.pluginManager
pluginManager.registerEvents(HomeTimerCancelListener(homeTimerManager), this)
pluginManager.registerEvents(OrderRespawnListener(orderService, flagStabilityManager), this)
pluginManager.registerEvents(FlagEventListener(homeTimerManager), this)

// Передать homeTimerManager в OrderMenu (через constructor injection или setter)
```

В `onDisable()` добавить:

```kotlin
homeTimerManager.onDisable()  // CC-05: отмена всех таймеров при shutdown
```

### Интеграция с OrderMenu

`OrderMenu` нужно передать `homeTimerManager` и `flagStabilityManager`. Если `OrderMenu` создаётся в listener-е (например, при правом клике по флагу) — передать через constructor или singleton.

Проверить существующий паттерн инъекции в `OrderMenu` и адаптировать.

## Part 3: FlagStabilityManager вызов событий

Найти в коде все места где флаг деактивируется / уничтожается. Добавить:
```kotlin
Bukkit.getPluginManager().callEvent(FlagDeactivatedEvent(orderId))
```

Для релокации флага — добавить:
```kotlin
Bukkit.getPluginManager().callEvent(
    FlagRelocatedEvent(orderId, oldWorld, newWorld, newFlagLocation)
)
```

Это должно быть сделано ПОСЛЕ успешного сохранения новых данных в PDC, на main thread.

## Tests to Write

| TC | Scenario | Expected |
|----|----------|---------|
| TC-13 | FlagDeactivatedEvent → cancelTimersForOrder(FLAG_DEACTIVATED) | Все таймеры ордера отменены с уведомлением |
| TC-20 | FlagRelocatedEvent (тот же мир) | Таймеры НЕ отменяются; executeHomeTP прочитает новый PDC |
| TC-21 | FlagRelocatedEvent (другой мир) | cancelTimersForOrder(FLAG_WORLD_CHANGED) |
| TC-38 | CC-05: onDisable() вызван | Все BukkitTask отменены, map очищен |

## Acceptance Criteria Covered

AC-13, AC-18, AC-18a, CC-05

## Definition of Done

- [ ] `FlagEventListener.kt` создан и компилируется
- [ ] `FlagDeactivatedEvent.kt` и `FlagRelocatedEvent.kt` созданы или подтверждены как существующие
- [ ] `ComminusmPlugin.kt` обновлён (регистрация listener-ов, onDisable)
- [ ] Места вызова `callEvent(FlagDeactivatedEvent)` найдены и добавлены в flag-stability код
- [ ] Unit-тесты TC-13, TC-20, TC-21, TC-38 написаны и проходят
- [ ] `./gradlew compileKotlin` — PASS
- [ ] `./gradlew detekt ktlintCheck` — PASS
