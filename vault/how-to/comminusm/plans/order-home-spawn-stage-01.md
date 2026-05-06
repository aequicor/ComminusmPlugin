---
genre: how-to
module: comminusm
title: "Stage 01 — HomeTimerManager + HomeTimerState"
topic: order-home-spawn
stage: 1
status: DONE
date: 06.05.2026
related:
  - vault/concepts/comminusm/plans/order-home-spawn-plan.md
  - vault/reference/comminusm/spec/order-home-spawn.md
---

# Stage 01 — HomeTimerManager + HomeTimerState

## Goal

Создать сервис управления таймерами телепортации: модель состояния `HomeTimerState` и синглтон `HomeTimerManager`.

## Files to Create

- `src/main/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManager.kt`

## Implementation

### HomeTimerState

```kotlin
package ru.kyamshanov.comminusm.service

import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class HomeTimerState(
    val playerUuid: UUID,
    val orderId: Long,
    val taskId: Int,
    var remainingSeconds: Int = 30,
    val cancelled: AtomicBoolean = AtomicBoolean(false),
)
```

**Важно:** флаг отмены через `AtomicBoolean` для корректной работы при concurrent cancel.

### HomeTimerManager

```kotlin
package ru.kyamshanov.comminusm.service

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HomeTimerManager(private val plugin: Plugin) {

    private val timers = ConcurrentHashMap<UUID, HomeTimerState>()
    private val mm = MiniMessage.miniMessage()

    fun startTimer(playerUuid: UUID, orderId: Long): Boolean { ... }
    fun cancelTimer(playerUuid: UUID, reason: CancelReason, silent: Boolean = false) { ... }
    fun cancelTimersForOrder(orderId: Long, reason: CancelReason) { ... }
    fun hasActiveTimer(playerUuid: UUID): Boolean = timers.containsKey(playerUuid)
    fun onDisable() { ... }

    private fun tick(playerUuid: UUID) { ... }
    private fun executeHomeTP(playerUuid: UUID) { ... }
    private fun sendCancelMessage(playerUuid: UUID, reason: CancelReason) { ... }
}

enum class CancelReason {
    MOVEMENT, DAMAGE, ATTACK, DISCONNECT,
    FLAG_DEACTIVATED, FLAG_WORLD_CHANGED, KICKED_FROM_ORDER, FLAG_CAPTURED  // last 2 DEFERRED
}
```

### startTimer

1. Если `timers.containsKey(playerUuid)` → return false (таймер уже запущен, AC-12).
2. Создать `BukkitTask` через `plugin.server.scheduler.runTaskTimer(plugin, { tick(playerUuid) }, 20L, 20L)`.
3. Создать `HomeTimerState(playerUuid, orderId, taskId = task.taskId)`.
4. `timers[playerUuid] = state`.
5. Отправить ActionBar: `<yellow>Возврат домой: <white>30 сек.</white> Не двигайтесь!</yellow>`.
6. Return true.

### cancelTimer

1. `val state = timers.remove(playerUuid) ?: return` (idempotent).
2. `state.cancelled.set(true)`.
3. `plugin.server.scheduler.cancelTask(state.taskId)`.
4. Если `!silent` → отправить сообщение игроку (ActionBar + reason text).

### cancelTimersForOrder

1. Snapshot: `timers.values.toList()`.
2. Для каждого `state` с `state.orderId == orderId` → вызвать `cancelTimer(state.playerUuid, reason)`.

### tick

```
try {
    val state = timers[playerUuid] ?: return  // already cancelled
    if (state.cancelled.get()) { timers.remove(playerUuid); return }
    state.remainingSeconds--
    if (state.remainingSeconds <= 0) {
        executeHomeTP(playerUuid)
        return
    }
    val player = plugin.server.getPlayer(playerUuid) ?: return
    player.sendActionBar(mm.deserialize("<yellow>Возврат домой: <white>${state.remainingSeconds} сек.</white></yellow>"))
} catch (e: Exception) {
    plugin.logger.severe("HomeTimer tick error for $playerUuid: ${e.message}")
    cancelTimer(playerUuid, CancelReason.FLAG_DEACTIVATED, silent = true)
}
```

### executeHomeTP

```
try {
    val state = timers.remove(playerUuid) ?: return
    state.cancelled.set(true)
    plugin.server.scheduler.cancelTask(state.taskId)

    val player = plugin.server.getPlayer(playerUuid) ?: return

    // Fresh PDC read — НЕ кэшировать из startTimer (CC-06)
    val flagStabilityManager = plugin.server.servicesManager
        .load(FlagStabilityManager::class.java) ?: run {
        player.sendActionBar(mm.deserialize("<red>Флаг недоступен. Телепортация отменена.</red>"))
        return
    }

    val flagLoc = flagStabilityManager.getFlagLocation(state.orderId)
    if (flagLoc == null || !flagStabilityManager.isFlagActive(state.orderId)) {  // CC-02
        player.sendActionBar(mm.deserialize("<red>Флаг недоступен. Телепортация отменена.</red>"))
        return
    }

    if (flagLoc.world?.name != player.world.name) {  // AC-28 double-check
        player.sendActionBar(mm.deserialize("<red>Телепортация в другой мир недоступна.</red>"))
        return
    }

    try {
        player.teleport(flagLoc)
        player.sendActionBar(mm.deserialize("<green>Вы вернулись домой!</green>"))
    } catch (e: Exception) {
        plugin.logger.warning("Teleport failed for $playerUuid: ${e.message}")
        player.sendActionBar(mm.deserialize("<red>Ошибка телепортации. Попробуйте снова.</red>"))
    }
} catch (e: Exception) {
    plugin.logger.severe("executeHomeTP error for $playerUuid: ${e.message}")
}
```

### onDisable

```kotlin
timers.values.toList().forEach { state ->
    state.cancelled.set(true)
    plugin.server.scheduler.cancelTask(state.taskId)
}
timers.clear()
```

## Tests to Write

| TC | Scenario | Expected |
|----|----------|---------|
| TC-03 | startTimer → BukkitTask создан, таймер запущен | state в timers, ActionBar отправлен |
| TC-12 | startTimer дважды | второй вызов → false, один timer |
| TC-08 | tick до t=0 | executeHomeTP вызван |
| TC-24 | executeHomeTP, flagLoc==null | ActionBar ошибки, телепорт не произошёл |
| TC-35 | isFlagActive=false (CC-02) | телепорт отменён |
| TC-39 | флаг relocated, executeHomeTP читает новую точку (CC-06) | телепорт к актуальной точке |

## Acceptance Criteria Covered

AC-03, AC-08, AC-12, AC-21, CC-01 (partial), CC-02, CC-06

## Definition of Done

- [ ] `HomeTimerManager.kt` создан и компилируется
- [ ] Unit-тесты TC-03, TC-12, TC-08, TC-24, TC-35, TC-39 написаны и проходят
- [ ] `./gradlew compileKotlin` — PASS
- [ ] `./gradlew detekt ktlintCheck` — PASS
