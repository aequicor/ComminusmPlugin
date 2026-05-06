---
genre: reference
module: comminusm
title: Technical Specification — Order Home & Spawn
topic: order-home-spawn
status: Draft
date: 2026-05-06
author: "@SystemAnalyst"
related:
  - vault/concepts/comminusm/requirements/order-home-spawn.md
  - vault/concepts/comminusm/plans/order-home-spawn-corner-cases.md
  - vault/reference/comminusm/test-cases/order-home-spawn-test-cases.md
  - vault/reference/comminusm/spec/flag-stability.md
---

# Technical Specification — Order Home & Spawn

**Module:** comminusm
**Status:** Draft
**Date:** 2026-05-06
**Author:** @SystemAnalyst
**Requirements:** [[concepts/comminusm/requirements/order-home-spawn]]

---

## 1. Overview

This feature adds two closely related mechanics for order members:

1. **Return Home (телепортация домой)** — an order member opens the order GUI and clicks the "Return Home" button. A 30-second stand-still timer starts. If the player does not move, receive damage, or attack during those 30 seconds, they are teleported to the exact coordinates of their order's active flag banner. The timer runs in-memory only and does not survive server restarts. Cross-world teleport is forbidden.

2. **Order Respawn (возрождение у флага)** — when an order member dies and the order flag is active, the player respawns at the flag banner coordinates instead of the world spawn or their bed. Priority: order flag → bed → world spawn.

Both mechanics require the flag to be verified as active via the flag-stability API at the moment of teleport or respawn application — not at the moment of initiating the action.

---

## 2. Data Models

### 2.1 HomeTimerState (in-memory only)

Held in a `ConcurrentHashMap<UUID, HomeTimerState>` managed by `HomeTimerManager`. Not persisted across server restarts (CC-05).

```
Field              Type                    Description
-----              ----                    -----------
playerUuid         UUID                    Owner of this timer entry (used as map key)
orderId            Long                    ID of the order whose flag is the teleport destination
startPosition      Location                Player's XYZ+world at the moment the timer was started
                                           (used to detect movement; NOT the flag position)
taskId             Int                     BukkitTask ID returned by the repeating scheduler task;
                                           used to cancel the task when the timer is cancelled
remainingSeconds   Int                     Countdown from 30 down to 0; decremented by the repeating
                                           task each tick-second
cancelled          AtomicBoolean           Set to true when cancelTimer() claims this entry first;
                                           checked inside tick() immediately after state retrieval
                                           to abort the in-progress tick before executeHomeTP is called
                                           (cancellation-race guard — see Q2).
```

All fields except `cancelled` are populated atomically before the BukkitTask is submitted. `cancelled` is initialized to `false` and transitions to `true` exactly once (compare-and-set semantic). No other field is mutable from outside `HomeTimerManager`.

### 2.2 Flag Position (read from PDC at the moment of use)

No new data model is introduced. Flag coordinates are read from the existing Chunk PDC key:

```
PDC key:  comminusm:flag/{orderId}
Encoding: "{worldName}:{x}:{y}:{z}"   (banner block coordinates)
```

**NamespacedKey contract (Q6):** The namespace string is `"comminusm"` and the key string is `"flag/{orderId}"` (where `{orderId}` is the numeric order ID). The `NamespacedKey` must be constructed using the exact plugin instance registered by the `flag-stability` module (i.e., the same plugin object that wrote the PDC entry). The key ownership belongs to `flag-stability`. `HomeTimerManager` and `OrderRespawnListener` must obtain the plugin instance reference through the same shared mechanism used by flag-stability (e.g., injected at construction time from the plugin main class), not by creating a new `NamespacedKey` with a different plugin instance.

This is defined by the flag-stability spec (Section 3, Key 1). The order-home-spawn feature reads this key but never writes it. Flag position is **never cached** in `HomeTimerState`; it is read fresh from PDC at the moment of teleport execution (CC-06) and at the moment of respawn event handling (CC-01).

**World-name resolution and null handling:** When parsing the PDC position string, if `Bukkit.getWorld(worldName)` returns null (world renamed, migrated, or unloaded), `getFlagLocation` returns null and the calling code falls back as for any absent key. **The implementation MUST emit a WARN-level log entry** at the point of null world resolution, including the world name string and the order ID, so that server administrators can detect stale PDC entries caused by world renames or migrations. No automatic PDC cleanup is performed — cleanup is the responsibility of server administration.

---

## 3. Event Handlers and GUI Components

### 3.1 OrderGuiClickHandler (modification of existing Order GUI)

**Trigger:** `InventoryClickEvent` on the order GUI inventory.

**Slot:** One designated slot for the "Return Home" button item (exact slot number defined by the existing GUI layout; this spec does not prescribe a slot index — the implementation must match the existing inventory structure).

**Slot and item validation (Q10):** Before executing any click logic, `OrderGuiClickHandler` MUST validate both the slot index AND the item type/identity of the clicked item. The handler must confirm that `event.rawSlot` equals the expected "Return Home" slot constant AND that `event.currentItem` is the expected button `ItemStack` (checking `Material` type and, if applicable, the item's `ItemMeta` identifier). If either check fails, the handler must RETURN immediately without executing any logic. This prevents unintended triggering from slot drift, inventory desync, or external item manipulation.

**Button visibility rules:**

> **[PO architectural decision Q1/Q5]:** The current order implementation has a single owner per order — there is no member/candidate/role system. All membership checks are replaced by ownership checks. AC-17 (kick member), AC-29 (candidates), AC-26 (leader = member), and AC-20 (capture by enemy) are **NOT APPLICABLE** in the current implementation.

| Player ownership | Flag active | Same world | Button state |
|-----------------|-------------|-----------|--------------|
| Owner of the order flag | yes | yes | Visible and clickable |
| Owner of the order flag | yes | no (AC-28) | Disabled or hidden with message |
| Owner of the order flag | no | any | Hidden or not rendered |
| Non-owner | any | any | Not shown (AC-02) |

Ownership check uses `OrderService.isOwner(playerUuid): Boolean` (see Section 9.1). No role lookup; no candidate status.

**On click — precondition checks (in order):**

> **[PO architectural decision Q4]:** `orderId` MUST be obtained from the already-open GUI context (e.g., from the button `ItemMeta`/PDC or from a session cache populated when the GUI was built) — NOT via an async DB call to `OrderService` on the main thread. Calling `OrderService.getOrderId(playerUuid)` synchronously here is forbidden.

1. Confirm `event.whoClicked` is a `Player`.
2. Retrieve `orderId` from the GUI button's PDC/ItemMeta (stored when the GUI inventory was constructed). If absent or unparseable: STOP silently.
3. Confirm the player is the owner via `OrderService.isOwner(playerUuid)` (synchronous PDC-based check — see Section 9.1). If not owner: STOP (button not applicable).
4. Confirm the player's current world equals the flag's world (AC-28). If not: send MiniMessage message `<red>Возврат домой недоступен — флаг находится в другом мире.</red>`. Cancel. STOP.
5. Confirm the flag is currently active via `FlagStabilityManager.isFlagActive(orderId)` (CC-02). If not: send `<red>Флаг ордера недоступен.</red>`. Cancel. STOP.
6. Check `HomeTimerManager.hasActiveTimer(playerUuid)`. If a timer is already running: ignore silently (AC-12). STOP.
7. Call `HomeTimerManager.startTimer(player, orderId)`.

All checks are performed on the main thread (GUI events always fire on main thread).

---

### 3.2 HomeTimerManager

Singleton service (not a Bukkit listener). Manages the `ConcurrentHashMap<UUID, HomeTimerState>` and owns BukkitTask lifecycle.

**Thread-safety contract (Q3):** The backing map is a `ConcurrentHashMap<UUID, HomeTimerState>`. All map operations (`put`, `remove`, `get`, `values()`) are intrinsically thread-safe without additional locking. `cancelTimersForOrder(orderId, reason)` may be called from an async context (e.g., a flag-stability async task) and is safe to call on any thread because the map is a `ConcurrentHashMap`. However, any Bukkit API call inside the cancel path (e.g., `task.cancel()`, `player.sendMessage(...)`) MUST be bounced back to the main thread via `BukkitScheduler.runTask(plugin, ...)` before execution. The state removal from the map itself may happen on the calling thread; only the Bukkit API calls require the main thread bounce.

**Concurrency upper bound (Q11):** The expected maximum number of concurrent active home timers is bounded by the server's online player count. No additional per-server cap is imposed. Implementations should assume a typical upper bound of 100 simultaneous timers per server instance; no secondary index is required unless profiling reveals O(N) `cancelTimersForOrder` scans exceed 1 ms at this scale.

All public methods that perform Bukkit API calls (startTimer, cancelTimer) are called from the main thread. `cancelTimersForOrder` may be called from any thread (see thread-safety contract above).

#### startTimer(player: Player, orderId: Long)

1. Record `startPosition` = `player.location.clone()` (clone required — Location is mutable).
2. Build `HomeTimerState(playerUuid, orderId, startPosition, remainingSeconds = 30)`.
3. Submit a repeating `BukkitTask` with period 20 ticks (1 second) via `BukkitScheduler.runTaskTimer`. Store the returned `taskId` in the state.
4. Put state into the map keyed by `playerUuid`.
5. Send ActionBar to player: `<yellow>Возврат домой: <white>30 сек.</white></yellow>` (AC-03, AC-04).

#### tick(playerUuid: UUID)  — called by the repeating task

Runs on the main thread (BukkitTask without `Async` is always main thread).

**Error isolation contract (Q1):** The entire body of `tick()` MUST be wrapped in a top-level `try/catch(Exception)` block. If any exception is thrown inside `tick()`:
- Log at ERROR level with the playerUuid and the exception stacktrace.
- Call `cancelTimer(playerUuid, reason = DISCONNECT, silent = true)` to remove the state from the map and cancel the BukkitTask (prevents map leak).
- Do NOT re-throw. The task must terminate cleanly so Bukkit does not silently suppress the error and leak the repeating task.

**Cancellation race strategy (Q2):** A within-tick cancellation race occurs when `cancelTimer()` removes the state from the map and sets `cancelled = true` concurrently while `tick()` has already retrieved the state object and is about to call `executeHomeTP`. To prevent double-execution:

1. Retrieve state from map. If absent: STOP.
2. Immediately check `state.cancelled.get()`. If `true`: STOP. Do not proceed to `executeHomeTP`.
3. Resolve `Player` from `Bukkit.getPlayer(playerUuid)`. If null (player offline): call `cancelTimer(playerUuid, silent = true)`. STOP.
4. Decrement `remainingSeconds`.
5. If `remainingSeconds > 0`: send ActionBar `<yellow>Возврат домой: <white>{remainingSeconds} сек.</white></yellow>`. STOP.
6. If `remainingSeconds == 0`: call `executeHomeTP(playerUuid, state)`.

The double-check in step 2 (after map retrieval) closes the race window where `cancelTimer` set `cancelled = true` and removed from map after `tick()` had already retrieved the entry in step 1.

#### executeHomeTP(playerUuid: UUID, state: HomeTimerState)

1. Cancel and remove the BukkitTask: `task.cancel()`. Remove state from map.
2. Resolve `Player` from `Bukkit.getPlayer(playerUuid)`. If null: STOP (player disconnected).
3. Read flag position from Chunk PDC: `FlagStabilityManager.getFlagLocation(state.orderId)`. If null or parse error: send `<red>Флаг недоступен. Телепортация отменена.</red>` (AC-21). STOP.
4. Verify flag is still active: `FlagStabilityManager.isFlagActive(state.orderId)`. If not: send `<red>Флаг ордена деактивирован. Телепортация отменена.</red>` (CC-02). STOP.
5. Verify flag world equals player's current world (inter-world check at execution time, AC-28). If mismatch: send `<red>Флаг находится в другом мире. Телепортация отменена.</red>`. STOP.
6. Call `player.teleport(flagLocation)` on main thread. This call must be wrapped in a try/catch(Exception) (Q5): if teleport throws (e.g., chunk load failure, world error), log WARN with the exception and player UUID, send `<red>Телепортация не удалась. Попробуйте позже.</red>` to the player, and STOP cleanly without re-throwing.
7. Send player message: `<green>Вы вернулись домой!</green>`.

#### cancelTimer(playerUuid: UUID, reason: CancelReason, silent: Boolean = false)

1. Remove state from map atomically: `val state = map.remove(playerUuid)`. If the returned value is null (key was absent): STOP (idempotent). **Ordering note (Q4):** `ConcurrentHashMap.remove(key)` returns the previously associated value as a local variable. The call to `state.cancelled.set(true)` in step 2 always operates on this retained local reference — never on a null pointer — regardless of any concurrent calls. This ordering (remove-then-set) is therefore safe: a concurrent second caller receives `null` from its own `remove()` call and stops at step 1, while this caller holds the only reference to `state` and safely sets `cancelled = true`.
2. Set `state.cancelled.set(true)` — prevents any in-progress `tick()` that already retrieved this state from proceeding to `executeHomeTP` (cancellation-race guard, Q2).
3. Cancel the associated BukkitTask via `taskId`. This call requires main thread; if called from an async context, schedule via `BukkitScheduler.runTask(plugin, ...)`.
4. If `silent = false`: resolve `Player` and send appropriate MiniMessage cancel message (see Section 5). Player resolution and message sending require main thread.

`CancelReason` is an enum: `MOVEMENT`, `DAMAGE`, `ATTACK`, `FLAG_DESTROYED`, `FLAG_CAPTURED` *(NOT APPLICABLE — no enemy capture in current impl)*, `FLAG_WORLD_CHANGED`, `KICKED_FROM_ORDER` *(NOT APPLICABLE — no member kick in current impl)*, `PLAYER_DIED`, `DISCONNECT`.

#### cancelTimersForOrder(orderId: Long, reason: CancelReason)

> **[PO architectural decision Q2]:** Iteration MUST use an explicit snapshot — `ConcurrentHashMap.values().toList()` — taken before the loop begins. This avoids iterating the live view, which could reflect concurrent additions or removals mid-loop and produce undefined iteration behaviour.

Iterates the snapshot and calls `cancelTimer` for each entry whose `orderId` matches. **Performance:** O(N) over the number of active timers. Acceptable given the expected upper bound of ≤ 100 concurrent timers (see concurrency upper bound note above). No secondary index is required.

This method is safe to call from any thread. The snapshot is taken on the calling thread; `ConcurrentHashMap.values().toList()` is thread-safe. Bukkit API calls within the cancel path (task.cancel, sendMessage) MUST be bounced to the main thread as specified in the thread-safety contract.

#### onDisable() — plugin lifecycle (Q8)

Called during plugin shutdown or `/reload`. `HomeTimerManager.onDisable()` MUST:

1. Iterate all entries in the map.
2. For each entry: call `BukkitTask.cancel()` on the stored task (by taskId). No player notifications are sent (server is shutting down).
3. Clear the map.

This prevents BukkitTask leaks during hot-reload or server shutdown. The method must complete synchronously before returning control to the plugin main class `onDisable`.

#### hasActiveTimer(playerUuid: UUID): Boolean

Returns `map.containsKey(playerUuid)`.

---

### 3.3 HomeTimerCancelListener

Bukkit listener. Cancels the active home timer on disqualifying events.

#### onPlayerMove(event: PlayerMoveEvent)

- Check `HomeTimerManager.hasActiveTimer(event.player.uniqueId)`. If not: RETURN.
- Compare `event.from` and `event.to`: if X, Y, or Z coordinates differ by more than the movement threshold (see Section 6.1): `cancelTimer(uuid, MOVEMENT)`.
- Head rotation change (yaw/pitch only, no XYZ change) does NOT cancel the timer.

#### onEntityDamage(event: EntityDamageEvent)

- Check entity is `Player` and `HomeTimerManager.hasActiveTimer(uuid)`. If not: RETURN.
- `cancelTimer(uuid, DAMAGE)`.

#### onEntityDamageByEntity(event: EntityDamageByEntityEvent)

- Check damager (or projectile shooter) is `Player` and `HomeTimerManager.hasActiveTimer(uuid)`. If not: RETURN.
- `cancelTimer(uuid, ATTACK)`.

#### onPlayerQuit(event: PlayerQuitEvent)

- `cancelTimer(event.player.uniqueId, DISCONNECT, silent = true)` (AC-15).

#### onPlayerDeath(event: PlayerDeathEvent)  _(or handled implicitly via AC-19)_

- `cancelTimer(event.entity.uniqueId, PLAYER_DIED, silent = true)` (AC-19).

---

### 3.4 OrderRespawnListener

Bukkit listener. Overrides respawn location on `PlayerRespawnEvent`.

**Priority:** `EventPriority.HIGH` to run after bed-assignment listeners but before world-spawn assignment.

#### onPlayerRespawn(event: PlayerRespawnEvent)

All steps execute synchronously on the main thread.

**Error isolation (Q2 / Section 7 contract):** The entire body of `onPlayerRespawn` MUST be wrapped in a top-level `try/catch(Exception)` block. If any exception propagates to the catch block: log at ERROR level with the player UUID and the full stacktrace, then RETURN without setting `event.respawnLocation` (Bukkit's default respawn chain applies). The handler must never propagate an exception to the Bukkit event pipeline.

1. **[PO architectural decision Q4]:** Retrieve player's `orderId` by reading the bedrock block's PDC synchronously (PDC read is a synchronous in-memory operation — no DB call). `OrderService.getOrderId(playerUuid)` MUST NOT be called here; it performs an async DB lookup that cannot be awaited inside a synchronous `PlayerRespawnEvent` handler. If no `orderId` is found in PDC (player has no order, or bedrock PDC is absent): RETURN (fallback to Bukkit default — AC-11).
2. Read flag location fresh from PDC: `FlagStabilityManager.getFlagLocation(orderId)`. If null: RETURN (fallback — AC-10, AC-22).
3. Verify flag is active: `FlagStabilityManager.isFlagActive(orderId)`. If not: RETURN (fallback — CC-01, CC-02).
4. Force-load the chunk at the flag coordinates (CC-04). **Chunk load strategy (Q7):** `PlayerRespawnEvent` is synchronous and cannot be deferred; therefore `world.getChunkAt(flagLoc).load(true)` is used as a documented exception to the "no blocking main thread" constraint. This is the only place in this feature where a synchronous blocking Bukkit call is permitted. The rationale is that `PlayerRespawnEvent` provides no async callback mechanism — the respawn location must be set before the handler returns. If `load(true)` returns false or throws: log WARN with chunk coordinates, RETURN (fallback — AC-22).
5. Verify world of flag equals `event.respawnLocation.world` name? No — cross-world is allowed for respawn (CC-03). Construct `Location(flagWorld, x, y, z)` using the flag's world object retrieved from `Bukkit.getWorld(worldName)`. If world is null (e.g., world unloaded): log WARN, RETURN (fallback).
6. Set `event.respawnLocation = flagLocation` (AC-09, AC-27).

The handler does NOT check the player's bed — Bukkit's bed priority is bypassed by setting the respawn location explicitly with priority HIGH. This implements the priority order: order flag > bed > world spawn (AC-27). If this handler returns early (any fallback path), Bukkit applies its normal priority chain (bed → world spawn).

---

## 4. Business Logic Flows

### 4.1 Return Home Flow (US-01, US-02, US-03, US-04 partial)

```
1. Player opens order GUI → OrderGuiClickHandler renders button per visibility rules.
2. Player clicks "Return Home":
   a. World-match check (AC-28).
   b. Flag active check (CC-02).
   c. Duplicate timer check (AC-12).
   d. HomeTimerManager.startTimer() — timer begins, ActionBar shown.
3. Each second (20 ticks):
   a. HomeTimerManager.tick() decrements counter, updates ActionBar (AC-04).
4. Disqualifying events (HomeTimerCancelListener):
   - Move with XYZ delta > threshold → cancel MOVEMENT (AC-05, AC-14, AC-16, AC-24).
   - Receive damage → cancel DAMAGE (AC-06).
   - Deal damage → cancel ATTACK (AC-07).
   - Disconnect → cancel DISCONNECT silent (AC-15).
   - Death → cancel PLAYER_DIED silent (AC-19).
   - Flag destroyed → cancel FLAG_DESTROYED (AC-13).
   - Flag captured — **NOT APPLICABLE** (AC-20: enemy capture not in current implementation).
   - Flag relocated to other world → cancel FLAG_WORLD_CHANGED (AC-18a).
   - Kicked from order — **NOT APPLICABLE** (AC-17: no member kick mechanic in current implementation).
5. At t=0: executeHomeTP():
   a. Re-read flag PDC location (CC-06 — freshly read, not cached from step 2).
   b. Re-verify flag active (CC-02).
   c. Re-verify same world (AC-28).
   d. player.teleport(flagLocation).
```

### 4.2 Order Respawn Flow (US-04, US-05)

```
1. Player dies → PlayerDeathEvent fires.
   → HomeTimerCancelListener cancels any active home timer silently (AC-19).
2. PlayerRespawnEvent fires (EventPriority.HIGH):
   a. Read orderId from bedrock block PDC synchronously (no DB call — see Q4 architectural constraint).
   b. Read flag PDC (fresh, CC-01).
   c. Verify flag active (CC-01, CC-02).
   d. Force-load chunk (CC-04).
   e. Resolve flag world (CC-03).
   f. Set event.respawnLocation.
3. If any step (a–e) fails or returns null: Bukkit default chain applies.
```

### 4.3 Flag Events → Timer Cancellation (Q4 — OQ-04 resolved)

**Integration contract: event-driven (push) via custom Bukkit events.**

The flag-stability module publishes the following custom Bukkit events when flag lifecycle state changes. The `order-home-spawn` module registers listeners for these events and calls `HomeTimerManager.cancelTimersForOrder` in response. This is a push integration: flag-stability fires events; home-spawn reacts. flag-stability does NOT call `HomeTimerManager` directly — there is no direct API dependency from flag-stability to home-spawn.

| Custom event (published by flag-stability) | Listener action | Cancel reason |
|-------------------------------------------|-----------------|--------------:|
| `FlagDeactivatedEvent(orderId)` | `cancelTimersForOrder(orderId, FLAG_DESTROYED)` | `FLAG_DESTROYED` |
| `FlagCapturedEvent(orderId, newOwnerId)` ⚠️ DEFERRED (AC-20) | `cancelTimersForOrder(orderId, FLAG_CAPTURED)` — NOT APPLICABLE in current implementation (no enemy capture mechanic) | `FLAG_CAPTURED` |
| `FlagRelocatedEvent(orderId, oldWorld: String, newWorld: String)` — cross-world only | `cancelTimersForOrder(orderId, FLAG_WORLD_CHANGED)` if `oldWorld != newWorld` | `FLAG_WORLD_CHANGED` |
| `FlagRelocatedEvent` — same world (`oldWorld == newWorld`) | No action (AC-18) | — |

The custom events are part of the flag-stability module's public API surface and must be defined in the flag-stability spec before implementation of this integration begins. If these events do not yet exist in the flag-stability spec, that is a dependency gap that must be resolved before this integration can be implemented.

**Timing:** the `executeHomeTP` re-verification at t=30 (steps 3–4) remains mandatory as a safety net — it handles the window between the last `tick()` and before cancel-event processing, and also covers cases where events fire while the server is in the middle of processing the teleport. The ActionBar will stop showing after the timer's BukkitTask is cancelled; the cancellation message (Section 5) is sent at that point.

`HomeTimerManager` exposes `cancelTimersForOrder(orderId: Long, reason: CancelReason)` that cancels all active timers whose `orderId` matches.

> **[PO architectural decision Q1/Q5 — NOT APPLICABLE]:** The `OrderService.removeMember` integration hook and the `KICKED_FROM_ORDER` cancel path are not applicable in the current implementation — there is no member kick mechanic. `FLAG_CAPTURED` cancellation via an enemy-capture event is likewise not applicable. If these mechanics are introduced in a future iteration, this section must be updated.

---

## 5. Notification Messages (MiniMessage)

All messages use MiniMessage format. No legacy ChatColor strings.

| Event | Message |
|-------|---------|
| Timer started | `<yellow>Возврат домой: <white>30 сек.</white> Не двигайтесь!</yellow>` |
| Timer tick (ActionBar) | `<yellow>Возврат домой: <white>{N} сек.</white></yellow>` |
| Cancelled — movement | `<red>Телепортация отменена: вы сдвинулись с места.</red>` |
| Cancelled — damage received | `<red>Телепортация отменена: вы получили урон.</red>` |
| Cancelled — attack dealt | `<red>Телепортация отменена: вы атаковали.</red>` |
| Cancelled — flag gone (destroyed or captured) | `<red>Телепортация отменена: флаг ордера недоступен.</red>` |
| Cancelled — flag in other world | `<red>Телепортация отменена: флаг перемещён в другой мир.</red>` |
| Cancelled — kicked from order ⚠️ DEFERRED (AC-17) | `<red>Телепортация отменена: вы покинули ордер.</red>` — NOT APPLICABLE in current implementation (no member kick mechanic) |
| Cancelled — player died | (silent — no message) |
| Cancelled — disconnect | (silent — no message) |
| Button disabled (cross-world) | `<gray>Возврат домой недоступен: флаг находится в другом мире.</gray>` |
| Teleport failed — flag data unavailable | `<red>Флаг недоступен. Телепортация отменена.</red>` |
| Teleport failed — flag inactive | `<red>Флаг ордера деактивирован. Телепортация отменена.</red>` |
| Teleport success | `<green>Вы вернулись домой!</green>` |
| Teleport threw exception (chunk/world error) | `<red>Телепортация не удалась. Попробуйте позже.</red>` |

ActionBar messages are sent via `player.sendActionBar(Component)` (Paper Adventure API).
Chat messages are sent via `player.sendMessage(Component)`.

---

## 6. Edge Cases

| # | Corner case | Condition | Handling |
|---|-------------|-----------|----------|
| CC-01 | Race: flag destroyed same tick as player death | `PlayerRespawnEvent` handler reads PDC — key may already be deleted | Re-read PDC fresh in respawn handler; if null → fallback to world spawn (AC-10). No exception propagates out of the handler. |
| CC-02 | Flag coordinates present in PDC but flag physically deactivated | `isFlagActive` returns false | Both teleport execution (`executeHomeTP`) and respawn handler (`onPlayerRespawn`) call `isFlagActive` independently and fall back if false. |
| CC-03 | Player dies in Nether/End, flag in Overworld | Cross-world respawn | Respawn handler constructs `Location` from the flag world object (not from `event.respawnLocation.world`). Flag world is resolved via `Bukkit.getWorld(worldName)`. |
| CC-04 | Chunk at flag coordinates is not loaded at respawn time | `world.getChunkAt(...).load(true)` fails or returns false | Log WARN, fall back to world spawn (AC-22). No teleport attempted to unloaded chunk. |
| CC-05 | Server restart while timer is active | Timer is in-memory only | All timers lost on restart. After reconnect, player must click the button again. This is expected and documented behavior. |
| CC-06 | Flag relocated within same world in same tick as timer fires | Timer reads stale cached coordinates | Flag position is read fresh from PDC inside `executeHomeTP`, not from `HomeTimerState`. Player teleports to the new (actual) coordinates (AC-18). |
| CC-07 | Player opens another inventory while timer is running | Timer runs in background (AC-23) | Timer is not coupled to the GUI state. Only movement / damage / attack cancel it. |
| CC-09 | Minimal client position drift < movement threshold | `PlayerMoveEvent` fires with sub-threshold delta | Movement threshold check: XYZ delta < `0.1` blocks per event does not trigger cancellation (see Section 6.1). |
| AC-18a | Flag relocated to different world during timer | Inter-world flag relocation detected | `cancelTimersForOrder(orderId, FLAG_WORLD_CHANGED)` called by flag-stability integration. Timer cancelled immediately. |
| AC-24 | Player riding a mount that moves | Mount position changes → `PlayerMoveEvent` fires on rider | The rider's `PlayerMoveEvent` reflects the mount's movement. Standard threshold check applies. Timer cancels if XYZ delta exceeds threshold. No auto-resume after dismount. |
| AC-27 | Player has a bed set; order flag is active | Respawn priority | `PlayerRespawnEvent` handler (priority HIGH) sets `event.respawnLocation` to flag coordinates. Bukkit's bed-priority chain is bypassed. |
| AC-28 | Player and flag are in different worlds at button click | Button click precondition | World-match check in `OrderGuiClickHandler` before `startTimer`. Timer never starts. |
| Q1 | Unhandled exception in `tick()` kills the BukkitTask silently, leaks HomeTimerState | Runtime exception inside repeating task | Top-level try/catch in `tick()` body. On exception: log ERROR, call `cancelTimer(playerUuid, DISCONNECT, silent = true)` to remove state and cancel BukkitTask. |
| Q2 | Within-tick cancellation race: `cancelTimer` removes state while `tick()` already retrieved it and proceeds to `executeHomeTP` | Concurrent cancel and tick execution | `HomeTimerState.cancelled` (AtomicBoolean) set by `cancelTimer` before returning; checked by `tick()` immediately after map retrieval. |
| Q5 | `player.teleport()` throws synchronous exception (chunk load failure, world error) | `executeHomeTP` step 6 | try/catch around teleport call; log WARN; notify player; STOP cleanly. |
| Q8 | BukkitTask leak on plugin reload or shutdown | Plugin `onDisable` not cleaning up active timers | `HomeTimerManager.onDisable()` cancels all BukkitTasks and clears map synchronously. |
| Q10 | Unintended slot triggering in order GUI from slot desync or external inventory manipulation | `OrderGuiClickHandler` receives click event for unexpected slot | Validate both `rawSlot` index and `currentItem` material/identity before executing any click logic. Return immediately if either check fails. |
| Q4 | Concurrent `cancelTimer` call while another call already holds a state reference from `map.remove()` | Second concurrent `cancelTimer` call for the same playerUuid | `ConcurrentHashMap.remove(key)` is atomic; the second caller receives `null` and stops at step 1. The first caller retains the state local reference and safely calls `cancelled.set(true)`. No NPE possible. See `cancelTimer` step 1 ordering note. |
| PDC-WARN | World name stored in PDC no longer resolvable (renamed/migrated world) | `Bukkit.getWorld(worldName)` returns null during PDC parse | `getFlagLocation` returns null; caller falls back as for absent key. WARN log emitted with world name and order ID so admin can detect stale entries. No automatic PDC cleanup. |

### 6.1 Movement Threshold

To prevent client-lag micro-drift from falsely cancelling the timer (CC-09), the movement detection in `onPlayerMove` applies a minimum delta threshold:

- Compare `event.from` and `event.to`.
- If `abs(to.x - from.x) < 0.1 AND abs(to.y - from.y) < 0.1 AND abs(to.z - from.z) < 0.1`: treat as no movement — do NOT cancel.
- Otherwise: cancel with reason `MOVEMENT`.
- Head rotation (yaw/pitch change with no XYZ delta): never cancels.

This threshold value (`0.1`) is a fixed constant, not configurable.

---

## 7. Error Handling

| Error scenario | Where detected | Handling | User message |
|----------------|---------------|----------|--------------|
| Flag PDC key absent at timer execution | `executeHomeTP` step 3 | Cancel teleport, log DEBUG | «Флаг недоступен. Телепортация отменена.» |
| Flag PDC key absent at respawn | `onPlayerRespawn` step 2 | Fall back to world spawn, log DEBUG | (none — fallback is transparent) |
| Flag deactivated at timer execution | `executeHomeTP` step 4 | Cancel teleport, log DEBUG | «Флаг ордена деактивирован. Телепортация отменена.» |
| Flag deactivated at respawn | `onPlayerRespawn` step 3 | Fall back to world spawn | (none) |
| Chunk load fails at respawn | `onPlayerRespawn` step 4 | Fall back to world spawn, log WARN with chunk coords | (none) |
| Flag world unloaded at respawn | `onPlayerRespawn` step 5 | Fall back to world spawn, log WARN with world name | (none) |
| PDC world-name not resolvable (world renamed/migrated) | `getFlagLocation` internal, called from `onPlayerRespawn` or `executeHomeTP` | Returns null; calling code falls back as for absent key. MUST log WARN with world name string and order ID. | (none — transparent fallback; WARN visible in server log for admin) |
| Flag in different world at timer execution | `executeHomeTP` step 5 | Cancel teleport | «Флаг находится в другом мире. Телепортация отменена.» |
| Player offline when timer fires | `executeHomeTP` step 2 | STOP silently | (none) |
| PDC parse error (malformed position string) | `getFlagLocation` in flag-stability | Returns null; handled as absent key | Same as "Flag PDC key absent" |
| Exception inside `onPlayerRespawn` | Uncaught exception | Must be caught at handler top-level; log ERROR with stacktrace; fall back to world spawn | (none) |
| Unhandled exception inside `tick()` (Q1) | Top-level try/catch in `tick()` body | Log ERROR with playerUuid + stacktrace; call `cancelTimer(playerUuid, DISCONNECT, silent = true)` to remove state and cancel BukkitTask; do NOT re-throw | (none — cleanup is silent) |
| `player.teleport()` throws synchronously (Q5) | try/catch around step 6 in `executeHomeTP` | Log WARN with playerUuid + exception; send error message to player; STOP cleanly | «Телепортация не удалась. Попробуйте позже.» |
| `cancelTimersForOrder` called from async thread, BukkitTask.cancel() must run on main thread (Q3) | `cancelTimer` internals | Map removal on calling thread; Bukkit API calls scheduled via `BukkitScheduler.runTask` | (none) |

**Contract:** `onPlayerRespawn` must never propagate an exception to the Bukkit event pipeline. All exception paths must be caught at the handler level with a try/catch, logged at ERROR, and result in fallback to world spawn.

**Contract:** `tick()` must never propagate an exception to the BukkitTask executor. Top-level try/catch is mandatory. On any exception, the state must be cleaned up from the map before returning.

---

## 8. Security Considerations

- **Authentication:** All actions are initiated by the server via Bukkit events. No external HTTP endpoints.
- **Authorization — Return Home button:** Visibility and clickability are checked server-side on every `InventoryClickEvent`. Client cannot bypass the check by packet manipulation because the server re-validates role and flag state on click.
- **Authorization — Respawn override:** `PlayerRespawnEvent` is a server-internal event; no client input is accepted.
- **Ownership check:** Before rendering or acting on the button, the player's ownership of the order flag must be verified via `OrderService.isOwner(playerUuid)` (synchronous PDC-based check). Only the flag owner has access. Role-based checks (MEMBER, LEADER, CANDIDATE) are **NOT APPLICABLE** in the current implementation — see PO decision Q1/Q5.
- **World-check enforcement:** The cross-world teleport prohibition (AC-28) is enforced both at button-click time AND at timer-execution time (`executeHomeTP` step 5). Both checks are required because the flag's world may change between click and execution (AC-18a).
- **No sensitive data in PDC:** Flag positions are coordinates (world name + integers). No PII, tokens, or secrets are stored.
- **No blocking of main thread:** All timer ticks and event handlers run on the main thread via `BukkitTask`. No blocking I/O is performed. PDC reads are synchronous in-memory operations (no disk I/O beyond what Paper itself does). **Exception:** `world.getChunkAt(flagLoc).load(true)` in `onPlayerRespawn` is a documented synchronous exception — see Section 3.4 and Section 7 for rationale.

---

## 9. Dependencies

### 9.1 Internal

| Dependency | What is consumed |
|------------|-----------------|
| `flag-stability` spec / `FlagStabilityManager` | See interface contract below. |
| `OrderService` | `isOwner(playerUuid): Boolean` — returns true if the player is the owner of their order flag (main-thread safe, synchronous). `getMemberRole` is **NOT USED** — does not exist in the current implementation. `removeMember` hook — triggers `KICKED_FROM_ORDER` timer cancellation; **NOT APPLICABLE** in the current implementation (no member kick mechanic). See thread-context note below. |

#### FlagStabilityManager — interface contract (OQ-03 resolved)

This spec references `FlagStabilityManager` as the boundary between the `flag-stability` module and this feature. The contract this spec depends on is:

```
interface FlagStabilityManager {

    /**
     * Returns the Location of the active flag banner for the given order,
     * or null if no PDC entry exists or the stored position string cannot
     * be parsed. The world is resolved via Bukkit.getWorld(worldName);
     * if the world is unloaded or does not exist, returns null.
     *
     * Thread context: safe to call on main thread only.
     * PDC read is a synchronous in-memory operation (no network/disk I/O).
     */
    fun getFlagLocation(orderId: Long): Location?

    /**
     * Returns true if the flag for the given order is currently in an
     * active/registered state according to the flag-stability internal
     * activation registry (not merely whether a PDC key is present).
     *
     * A flag may have a PDC entry but be inactive (e.g., captured,
     * deactivated). This method is the authoritative active-state check.
     *
     * Thread context: safe to call on main thread only.
     */
    fun isFlagActive(orderId: Long): Boolean
}
```

The concrete implementation of `FlagStabilityManager` is owned by the `flag-stability` module and must be defined in the flag-stability spec before implementation of this feature begins. This spec treats the above signatures and semantics as a fixed contract; if the flag-stability implementation differs, this spec must be updated.

#### PDC-first runtime resolution — architectural constraint (Q4 resolved)

> **[PO architectural decision Q4]:** `orderId` and ownership MUST be determined from PDC block data (synchronous, in-memory) at runtime — NOT via DB calls. Specifically:
>
> - **`OrderGuiClickHandler`:** `orderId` is read from the button's `ItemMeta`/PDC at click time (stored when the GUI was built). No call to `OrderService.getOrderId(playerUuid)` is permitted on the main thread.
> - **`onPlayerRespawn`:** `orderId` is read from the bedrock block's PDC synchronously at respawn time. No call to `OrderService.getOrderId(playerUuid)` is permitted inside this synchronous event handler.
> - **`OrderService.isOwner(playerUuid)`:** This method is safe to call on the main thread because it performs a synchronous PDC-based ownership check, not an async DB query.
>
> The DB layer (`OrderService`) is used for persistence only — writing/reading persistent order state. It is never called synchronously on the main thread for runtime flag/ownership resolution in this feature.

#### OrderService.removeMember — thread-context note (Q1 resolved)

`OrderService.removeMember` is an internal server-side API. Its call originates from game logic (e.g., a command, an admin action) that may run on the main thread or, if called from an async task, on an async thread. Because `HomeTimerManager.cancelTimersForOrder` is safe to call from any thread (see Section 3.2 thread-safety contract), the hook from `removeMember` does NOT need a main-thread guarantee. However, any Bukkit API calls within the cancel path (task.cancel, sendMessage) will be bounced to the main thread internally by `cancelTimer` as specified. The hook must NOT assume it runs on main thread.

> **[PO decision Q1/Q5]:** The `removeMember` integration hook and `KICKED_FROM_ORDER` cancel path are **NOT APPLICABLE** in the current implementation — there is no member kick mechanic.

| Order GUI (existing `InventoryClickEvent` handler) | Integration point to add the "Return Home" button slot and click handler. |

### 9.2 Bukkit / Paper API

| API | Usage |
|-----|-------|
| `PlayerMoveEvent` | Detect player movement to cancel timer |
| `EntityDamageEvent` | Detect damage received to cancel timer |
| `EntityDamageByEntityEvent` | Detect attack dealt to cancel timer |
| `PlayerQuitEvent` | Silent timer cancellation on disconnect |
| `PlayerDeathEvent` | Silent timer cancellation on death |
| `PlayerRespawnEvent` (priority HIGH) | Override respawn location |
| `InventoryClickEvent` | Detect "Return Home" button click in order GUI |
| `BukkitScheduler.runTaskTimer` | 1-second repeating task for countdown |
| `Player.sendActionBar(Component)` | Display countdown progress |
| `Player.sendMessage(Component)` | Cancellation and success notifications |
| `Player.teleport(Location)` | Execute home teleport |
| `Chunk.load(boolean)` | Force-load chunk before respawn (CC-04) |
| `Bukkit.getWorld(String)` | Resolve flag world by name (CC-03) |
| MiniMessage (Adventure API) | All text formatting — no legacy ChatColor |

### 9.3 Architectural Constraints

| Constraint | Rule |
|------------|------|
| No blocking main thread | All Bukkit API calls from main thread; PDC reads are non-blocking. Exception: synchronous chunk load in `onPlayerRespawn` (documented, unavoidable — see Q7 in Section 3.4). |
| No Player reference storage | Store `UUID` in `HomeTimerState`; resolve `Player` per tick via `Bukkit.getPlayer(uuid)` |
| Timer state is in-memory only | No serialization or persistence; lost on server restart (CC-05) |
| MiniMessage only | No `ChatColor`, no `§` color codes in any message string |
| No cross-world teleport | Return Home is disabled if player world ≠ flag world (AC-28) |
| Thread-safe map | `HomeTimerManager` backing map is `ConcurrentHashMap`. `cancelTimersForOrder` may be called from async context; Bukkit API calls within cancel path must be bounced to main thread. |
| Plugin lifecycle cleanup | `HomeTimerManager.onDisable()` cancels all BukkitTasks and clears the map on plugin disable or reload. |
| NamespacedKey ownership | PDC key `comminusm:flag/{orderId}` is owned by flag-stability. order-home-spawn uses the same plugin instance reference for key construction — no new NamespacedKey with a different plugin instance. |
| PDC-first runtime resolution | `orderId` and ownership determined from PDC block data (synchronous), not from async DB calls. `OrderService` is persistence-only. See Q4 architectural constraint in Section 9.1. |

---

## 10. Open Questions

| # | Question | Impact |
|---|----------|--------|
| OQ-01 | Can multiple players simultaneously have active timers pointing to the same order flag? | Requirements allow it (CC-08: штатное поведение). No limit required. Documented. |
| OQ-02 | Exact ActionBar display format — confirmed to be ActionBar (not Title or chat message)? | Requirements state ActionBar is primary and only channel (Out of Scope note). Confirmed. |
| OQ-03 | ~~`FlagStabilityManager.isFlagActive(orderId)` — exact API signature and semantics?~~ | **RESOLVED** — interface contract defined in Section 9.1. `isFlagActive` checks the internal activation registry, not merely PDC key presence. `getFlagLocation` reads PDC and resolves the world via `Bukkit.getWorld`. Both methods are main-thread only. |
| OQ-04 | ~~`HomeTimerManager.cancelTimersForOrder` — who is responsible for calling this?~~ | **RESOLVED** — push model via custom Bukkit events. flag-stability publishes `FlagDeactivatedEvent`, `FlagCapturedEvent`, `FlagRelocatedEvent`. order-home-spawn listens and calls `cancelTimersForOrder`. See Section 4.3. Custom events must be defined in flag-stability spec before this integration is implemented. |
