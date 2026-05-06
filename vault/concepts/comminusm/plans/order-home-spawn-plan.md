---
genre: concept
module: comminusm
title: Implementation Plan — Order Home & Spawn
topic: order-home-spawn
status: In Progress
date: 06.05.2026
author: "@Main"
related:
  - vault/reference/comminusm/spec/order-home-spawn.md
  - vault/concepts/comminusm/requirements/order-home-spawn.md
  - vault/concepts/comminusm/plans/order-home-spawn-corner-cases.md
  - vault/reference/comminusm/test-cases/order-home-spawn-test-cases.md
---

# Implementation Plan — Order Home & Spawn

## Goal

Добавить кнопку «Вернуться домой» в меню ордера (30-сек таймер, телепорт к флагу) и сделать флаг ордера точкой спавна при смерти.

## Modules Affected

- `src/main/kotlin/ru/kyamshanov/comminusm/`
  - `gui/OrderMenu.kt` — добавить кнопку + click handler
  - `service/HomeTimerManager.kt` — **новый файл**
  - `listener/HomeTimerCancelListener.kt` — **новый файл**
  - `listener/OrderRespawnListener.kt` — **новый файл**
  - `listener/FlagEventListener.kt` — **новый файл** (подписка на события flag-stability)
  - `ComminusmPlugin.kt` — регистрация новых listener-ов и manager-а

## Architecture Summary

```
[Player clicks button in OrderMenu]
       ↓
[OrderMenu.kt — onClick]
  → validate: owner? same world? flag active? no active timer?
  → HomeTimerManager.startTimer(player, orderId)
       ↓
[HomeTimerManager — BukkitTask (20 ticks / 1 sec)]
  → каждый тик: decrementSeconds, sendActionBar
  → при t=0: executeHomeTP()
       ↓
[HomeTimerCancelListener]
  → PlayerMoveEvent (delta > 0.1) → cancelTimer(MOVEMENT)
  → EntityDamageEvent → cancelTimer(DAMAGE)
  → EntityDamageByEntityEvent (attacker) → cancelTimer(ATTACK)
  → PlayerQuitEvent → cancelTimer(DISCONNECT, silent=true)
       ↓
[FlagEventListener]
  → FlagDeactivatedEvent → cancelTimersForOrder(FLAG_DEACTIVATED)
  → FlagRelocatedEvent (cross-world) → cancelTimersForOrder(FLAG_WORLD_CHANGED)

[Player dies]
       ↓
[OrderRespawnListener — PlayerRespawnEvent (PRIORITY=HIGH)]
  → read orderId from PDC (bedrock block at death location? No — from player's order)
  → FlagStabilityManager.getFlagLocation(orderId)
  → isFlagActive? → force-load chunk → set respawnLocation
```

## Stages

| # | Stage | Files | Key AC/CC |
|---|-------|-------|-----------|
| 1 | HomeTimerManager + HomeTimerState | `service/HomeTimerManager.kt` | AC-03, AC-08, AC-12, CC-01, CC-02, CC-06 |
| 2 | HomeTimerCancelListener | `listener/HomeTimerCancelListener.kt` | AC-05..07, AC-14..16, AC-19, AC-23, AC-24 |
| 3 | OrderMenu — кнопка «Вернуться домой» | `gui/OrderMenu.kt` | AC-01..03, AC-28, AC-29 (DEFERRED) |
| 4 | OrderRespawnListener | `listener/OrderRespawnListener.kt` | AC-09..11, AC-22, AC-27, CC-01, CC-03, CC-04 |
| 5 | FlagEventListener + Plugin wiring | `listener/FlagEventListener.kt`, `ComminusmPlugin.kt` | AC-13, AC-18, AC-18a, AC-20(DEFERRED), CC-05 |

## Critical Corner Cases (must have tests)

| CC | Stage | Test |
|----|-------|------|
| CC-01 | 4 | TC-34: race — флаг уничтожается в том же тике что смерть |
| CC-02 | 1, 4 | TC-35: stale PDC — флаг деактивирован, запись осталась |
| CC-03 | 4 | TC-36: смерть в Nether, флаг в Overworld — межмировой спавн |
| CC-04 | 4 | TC-37: chunk не загружен для точки флага |
| CC-05 | 5 | TC-38: рестарт сервера — таймеры in-memory теряются |
| CC-06 | 1 | TC-39: флаг relocated пока таймер идёт — телепорт к новой точке |
| CC-07 | 2, 3 | TC-40: другое меню открыто — таймер продолжает работать |

## Dependencies

- `FlagStabilityManager` — должен предоставлять `getFlagLocation(orderId: Long): Location?` и `isFlagActive(orderId: Long): Boolean`
- `FlagDeactivatedEvent(orderId: Long)` — кастомное Bukkit событие от flag-stability
- `FlagRelocatedEvent(orderId: Long, oldWorld: String, newWorld: String, newLocation: Location)` — кастомное Bukkit событие
- `OrderService.findByOwner(playerUuid: UUID): Order?` — уже существует

## Definition of Done

- [ ] ./gradlew compileKotlin — PASS
- [ ] ./gradlew :comminusm:test — PASS (все новые TC)
- [ ] ./gradlew detekt ktlintCheck — PASS
- [ ] TC-01..TC-41 покрыты (TC-19/23/30/33 — SKIP)
