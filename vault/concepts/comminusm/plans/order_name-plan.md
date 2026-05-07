---
genre: concept
module: comminusm
title: Implementation Plan — Order Name
topic: order-name
status: In Progress
date: 07.05.2026
---

# Implementation Plan — Order Name

## Goal

Add a customizable `name` field to orders. Default = owner's nickname (sanitized). Rename via Anvil GUI button in the Order Management Menu. After rename: update ArmorStand in world and refresh menu.

## Artifacts

- Requirements: `vault/concepts/comminusm/requirements/order_name.md`
- Corner Cases: `vault/concepts/comminusm/plans/order_name-corner-cases.md`
- Test Cases: `vault/reference/comminusm/test-cases/order_name-test-cases.md`
- Spec: `vault/reference/comminusm/spec/order_name.md`

## Codebase Context

- DB already has `name TEXT NOT NULL DEFAULT ''` column in `orders` table — no schema migration needed
- `domain/entities/Order.kt` does NOT have `name` field — must add
- `model/Order.kt` already has `name: String` — maps from DB row
- ArmorStand UUID stored in chunk PDC: `NamespacedKey(plugin, "armorstand/$orderId")`
- ArmorStand title currently uses `config.flagTitleFormat` — after this feature: use order name
- No existing AnvilGUI — implement with Paper `InventoryType.ANVIL`
- Use-case pattern: interface + impl in `application/usecases/order/`; returns `Result<T>`
- OrderMenu detection in onClick: `title.contains("Ордер №")`

## Stages

| # | Stage | Files | Test focus |
|---|-------|-------|-----------|
| 1 | Data layer — entity, repository, default name | `domain/entities/Order.kt`, `domain/repositories/OrderRepository.kt`, `infrastructure/repositories/OrderRepositoryImpl.kt`, `application/usecases/order/CreateOrderUseCaseImpl.kt`, `manager/FlagActivationHelper.kt` | AC-01, AC-15, TC-01, TC-19, TC-20 |
| 2 | Rename use case + Anvil GUI | `application/usecases/order/RenameOrderUseCase.kt`, `application/usecases/order/RenameOrderUseCaseImpl.kt`, `gui/OrderRenameMenu.kt` | AC-03..AC-13, AC-16..AC-20, CC-01..CC-09 |
| 3 | OrderMenu button + DI wiring | `gui/OrderMenu.kt`, main plugin class | AC-02, AC-11, AC-12, TC-13..TC-16 |

## Pre-mortem Risks (generated)

| R# | Линза | Сценарий | Вер. | Влиян. | Митигация |
|----|-------|---------|------|--------|-----------|
| R1 | Rollout | Существующие ордера `name=''` → пустое имя в меню и ArmorStand | H | H | **ACT NOW** — Stage 01: fallback `order.name.ifBlank { "Ордер №${order.id}" }` во всех render-точках |
| R2 | Hidden dep. | `DomainToModelAdapter` не обновлён для `name` → `model.Order.name` всегда `""` | M | H | **ACT NOW** — Stage 01: обновить адаптер, добавить тест |
| R3 | Failure recovery | Async DB write упал после обновления ArmorStand → расхождение дисплея и БД до следующего рестарта | L | M | Stage 02: ArmorStand обновляется ПОСЛЕ успешного DB callback; на ошибку — откат ArmorStand |
| R4 | Wrong target | Игрок не замечает уведомление об автосанитизации ника | M | M | AC-15 покрыт; уведомление в action bar; запись |
| R5 | Scale | PrepareAnvilEvent стреляет на каждый символ ввода | L | L | Не логировать в PrepareAnvilEvent; record only |
| R6 | Concurrency | Два переименования одного ордера → одно молча перезаписывается | M | L | Принято как tech debt (CC-16, last-write-wins в Out of Scope); запись |
| R7 | Hidden dep. | FlagActivationHelper не получает order.name при spawn ArmorStand | M | H | **ACT NOW** — Stage 01 §8: проследить цепочку, передать order.name как параметр |
| R8 | Failure recovery | `orderRepository.rename()` на main thread → server lag | H | H | **ACT NOW** — Stage 02: DB write только async (BukkitScheduler); ArmorStand обновляется optimistically на main thread |

| Risk | Severity | Mitigation |
|------|----------|-----------|
| ArmorStand entity null when chunk unloaded (CC-08) | HIGH | Null-check before setCustomName(); log warning; skip entity update — DB is source of truth |
| Leader demoted between Anvil open and confirm (CC-01) | CRITICAL | Re-check ownerUuid == player.uniqueId in RenameOrderUseCaseImpl (at confirm time, not open time) |
| Order deleted between Anvil open and confirm (CC-02) | CRITICAL | findById() returns null → return Failure(NotFoundException) → show error, close Anvil |
| in-progress tracking orphan on disconnect (CC-07) | HIGH | PlayerQuitEvent handler clears entry from ConcurrentHashMap |
| Double Anvil open on double-click (CC-06) | HIGH | Check inProgressRenames before opening; skip if already in map |
| DB write failure with stale in-memory name (AC-13) | CRITICAL | Cache oldName before update; on DB failure callback → restore oldName in map, re-update ArmorStand |
| Thread safety: ArmorStand.setCustomName() called off main thread | CRITICAL | Call setCustomName() in InventoryClickEvent handler (main thread); DB write dispatched async after |
