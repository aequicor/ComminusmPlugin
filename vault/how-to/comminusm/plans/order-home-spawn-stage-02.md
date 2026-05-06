---
genre: how-to
module: comminusm
title: "Stage 02 — HomeTimerCancelListener"
topic: order-home-spawn
stage: 2
status: DONE
date: 06.05.2026
related:
  - vault/concepts/comminusm/plans/order-home-spawn-plan.md
  - vault/reference/comminusm/spec/order-home-spawn.md
---

# Stage 02 — HomeTimerCancelListener

## Goal

Создать listener, который отменяет активный таймер при движении, уроне, атаке, выходе с сервера или смерти игрока.

## Files to Create

- `src/main/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListener.kt`

## Implementation

```kotlin
package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.kyamshanov.comminusm.service.CancelReason
import ru.kyamshanov.comminusm.service.HomeTimerManager

class HomeTimerCancelListener(private val homeTimerManager: HomeTimerManager) : Listener {

    // AC-05, AC-14 — движение (включая гравитацию, лестницу, воду)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        // Порог 0.1 блока для защиты от клиентского дрейфа (CC-09)
        val delta = Math.sqrt(
            Math.pow(to.x - from.x, 2.0) +
            Math.pow(to.y - from.y, 2.0) +
            Math.pow(to.z - from.z, 2.0)
        )
        if (delta >= 0.1) {
            homeTimerManager.cancelTimer(event.player.uniqueId, CancelReason.MOVEMENT)
        }
    }

    // AC-06 — получение урона
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        homeTimerManager.cancelTimer(player.uniqueId, CancelReason.DAMAGE)
    }

    // AC-07 — нанесение удара
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? org.bukkit.entity.Player ?: return
        homeTimerManager.cancelTimer(attacker.uniqueId, CancelReason.ATTACK)
    }

    // AC-15 — выход с сервера (тихая отмена)
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        homeTimerManager.cancelTimer(event.player.uniqueId, CancelReason.DISCONNECT, silent = true)
    }

    // AC-19 — смерть во время таймера
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        homeTimerManager.cancelTimer(event.player.uniqueId, CancelReason.DAMAGE, silent = true)
    }
}
```

### Заметки по реализации

- **AC-14:** `PlayerMoveEvent` срабатывает при любом изменении координат, включая гравитацию — порог 0.1 защищает от минимального клиентского дрейфа (CC-09).
- **AC-24:** Игрок на маунте — Paper генерирует `PlayerMoveEvent` когда маунт двигается; порог 0.1 обеспечивает отмену при физическом перемещении (AC-24 автоматически через этот listener).
- **AC-16 (external /tp):** Внешний `/tp` также генерирует `PlayerMoveEvent` с изменёнными координатами → таймер прерывается. Teleport to self (те же координаты) → delta = 0 < 0.1 → таймер НЕ прерывается. ✓
- **AC-23:** Закрытие меню ордера НЕ должно отменять таймер — здесь нет handler для `InventoryCloseEvent`. ✓
- **CC-07:** Если открыто другое inventory — таймер продолжает работать; единственный способ отмены — движение/урон/атака.

## Tests to Write

| TC | Scenario | Expected |
|----|----------|---------|
| TC-05 | PlayerMoveEvent с delta > 0.1 | cancelTimer(MOVEMENT) |
| TC-14 | Падение под действием гравитации (y изменилось) | cancelTimer(MOVEMENT) |
| TC-15 | Скольжение в воде | cancelTimer(MOVEMENT) |
| TC-06 | EntityDamageEvent (player получает урон) | cancelTimer(DAMAGE) |
| TC-07 | EntityDamageByEntityEvent (player атакует) | cancelTimer(ATTACK) |
| TC-16 | PlayerQuitEvent | cancelTimer(DISCONNECT, silent=true) |
| TC-22 | PlayerDeathEvent | cancelTimer (silent) |
| TC-17 | Внешний /tp на другие координаты | cancelTimer(MOVEMENT) через delta |
| TC-18 | Внешний /tp на идентичные координаты | delta=0 → таймер НЕ отменяется |
| TC-27 | Маунт движется, игрок сидит на нём | PlayerMoveEvent срабатывает, delta > 0.1 → cancel |

## Acceptance Criteria Covered

AC-05, AC-06, AC-07, AC-14, AC-15, AC-16, AC-19, AC-23, AC-24, CC-07, CC-09

## Definition of Done

- [ ] `HomeTimerCancelListener.kt` создан и компилируется
- [ ] Unit-тесты TC-05..07, TC-14..18, TC-22, TC-27 написаны и проходят
- [ ] `./gradlew compileKotlin` — PASS
- [ ] `./gradlew detekt ktlintCheck` — PASS
