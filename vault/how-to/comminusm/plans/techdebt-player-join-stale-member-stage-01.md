# Stage 01 — PlayerJoin: AC-47 per-player check + CC-14 offline notifications

**TD:** TD-comminusm-player-join-stale-member-and-offline-notif
**Module:** comminusm
**Path:** PLAN
**Goal:** Implement the two deferred TODOs in `CommunePlayerListener.onPlayerJoin()`:
- AC-47: per-player cross-order grant validation on join
- CC-14: delivery of queued offline commune notifications

---

## Context

`CommunePlayerListener.onPlayerJoin()` has two deferred comment blocks:
```kotlin
// AC-47: Consistency check for stale cross-order members (deferred)
// CC-14: Offline notification delivery for commune changes (deferred)
```

`CommuneStartupTask.performConsistencyCheck()` already does a full AC-47 scan on startup,
but it does not cover the per-player case: if a commune membership changes **after**
startup while a player is offline, the player will log in with stale grants. Similarly,
commune chat messages sent to offline players are currently silently dropped.

---

## Changes Required

### 1. `OrderMembersRepository.kt` — add type-filtered player query

Add after `getOrdersOfPlayer()`:

```kotlin
/**
 * Get all order IDs where a player holds a membership of a specific type.
 */
fun getOrdersOfPlayerWithType(playerUuid: UUID, membershipType: String): Set<Long> =
    cache.entries
        .toSet()
        .filter { (_, members) -> members.any { it.playerUuid == playerUuid && it.grantedVia == membershipType } }
        .map { it.key }
        .toSet()
```

### 2. `CommunePendingNotificationService.kt` — new class

Create at `src/main/kotlin/ru/kyamshanov/comminusm/commune/service/CommunePendingNotificationService.kt`:

```kotlin
package ru.kyamshanov.comminusm.commune.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory queue for commune notifications that could not be delivered because
 * the target player was offline at the time of broadcast.
 * Drained on PlayerJoinEvent (CC-14).
 */
class CommunePendingNotificationService {
    private val queue = ConcurrentHashMap<UUID, MutableList<String>>()

    /** Queue a notification for a player who is currently offline. */
    fun enqueue(playerUuid: UUID, message: String) {
        queue.getOrPut(playerUuid) { mutableListOf() }.add(message)
    }

    /** Remove and return all queued notifications for a player. Returns empty list if none. */
    fun drain(playerUuid: UUID): List<String> = queue.remove(playerUuid) ?: emptyList()
}
```

### 3. `CommuneChatService.kt` — enqueue for offline commune members

`CommuneChatServiceImpl` currently only broadcasts to online players.
Extend `broadcastToCommune()` to enqueue for offline native commune members:

Change constructor to inject `CommunePendingNotificationService`:
```kotlin
class CommuneChatServiceImpl(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
    private val pendingNotifications: CommunePendingNotificationService,
) : CommuneChatService {
```

In `broadcastToCommune()`, after sending to online members, add offline queuing.
The formatted plain-text version of the message to queue:
```
"[Коммуна] ${sender.name}: $wrappedText"
```

Steps in updated `broadcastToCommune()`:
1. Load commune, return if null (unchanged)
2. Get `communeOrderIds` (unchanged)
3. Determine all native member UUIDs across commune orders via
   `orderMembershipService.getMembersOfOrder(orderId)` filtered to `grantedVia == "native"`,
   collected into `Set<UUID>`.
4. Broadcast Component to online commune members (unchanged logic)
5. Queue plain-text message for offline commune members:
   ```kotlin
   val offlineText = "[Коммуна] ${sender.name}: $wrappedText"
   allNativeMemberUuids
       .filter { uuid -> Bukkit.getPlayer(uuid) == null }
       .forEach { uuid -> pendingNotifications.enqueue(uuid, offlineText) }
   ```

### 4. `CommunePlayerListener.kt` — implement both TODO blocks

Change constructor to inject new dependencies:
```kotlin
class CommunePlayerListener(
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase,
    private val orderMembersRepository: OrderMembersRepository,
    private val pendingNotifications: CommunePendingNotificationService,
    private val plugin: org.bukkit.plugin.Plugin? = null,
) : Listener {
```

Updated `onPlayerJoin()`:
```kotlin
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    val player = event.player
    val playerUuid = player.uniqueId

    // CC-14: drain offline notification queue (in-memory, safe on main thread)
    for (msg in pendingNotifications.drain(playerUuid)) {
        player.sendMessage(msg)
    }

    // AC-47: per-player cross-order grant validation
    val playerOrder = getOrderByOwnerUseCase(playerUuid) ?: return
    val commune = getCommuneOfOrderUseCase(playerOrder.id)
    val communeOrderIds = commune?.orderIds ?: emptySet()

    if (plugin != null) {
        // Offload to async — removeMember() may write to SQLite
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            checkAndRevokeStaleGrants(playerUuid, communeOrderIds)
        })
    } else {
        // Test mode: run synchronously (no Bukkit scheduler available)
        checkAndRevokeStaleGrants(playerUuid, communeOrderIds)
    }
}
```

Add internal method (testable without Bukkit):
```kotlin
internal fun checkAndRevokeStaleGrants(playerUuid: UUID, communeOrderIds: Set<Long>) {
    val communeGrantedOrders = orderMembersRepository.getOrdersOfPlayerWithType(playerUuid, "commune")
    val staleOrders = communeGrantedOrders - communeOrderIds
    for (staleOrderId in staleOrders) {
        orderMembersRepository.removeMember(staleOrderId, playerUuid)
    }
}
```

Remove the two deferred comment lines.

Add needed imports:
- `import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository`
- `import ru.kyamshanov.comminusm.commune.service.CommunePendingNotificationService`
- `import java.util.UUID`

### 5. `DIContainer.kt` — wire new dependencies

Add after `crossOrderMembershipService`:
```kotlin
val pendingNotificationService by lazy { CommunePendingNotificationService() }
```

Update `communeChatService`:
```kotlin
val communeChatService by lazy {
    CommuneChatServiceImpl(communeService, orderMembershipService, pendingNotificationService)
}
```

Update `CommunePlayerListener` instantiation in `createListeners()`:
```kotlin
CommunePlayerListener(
    getOrderByOwnerUseCase,
    getCommuneOfOrderUseCase,
    orderMembersRepository,
    pendingNotificationService,
    plugin,
),
```

Add import: `import ru.kyamshanov.comminusm.commune.service.CommunePendingNotificationService`

---

## Tests Required

### `CommunePlayerListenerTest.kt`

File: `src/test/kotlin/ru/kyamshanov/comminusm/commune/listener/CommunePlayerListenerTest.kt`

Tests call `checkAndRevokeStaleGrants()` directly (no Bukkit needed). Set `plugin=null`
in constructor for test mode so `onPlayerJoin()` also runs synchronously when needed.

Use in-memory `OrderMembersRepository(ConcurrentHashMap(), connection=null)`.
Use fake use-cases that return a fixed order/commune.

Test cases:
1. **`staleGrant_revokedWhenPlayerOrderNotInCommune`**: player UUID has "commune" grant in
   orderId=10. `communeOrderIds=emptySet()`. After `checkAndRevokeStaleGrants()` →
   `isMember(10, playerUuid)` returns false.
2. **`validGrant_keptWhenOrderInCommune`**: player UUID has "commune" grant in orderId=10.
   `communeOrderIds=setOf(10L)`. After `checkAndRevokeStaleGrants()` →
   `isMember(10, playerUuid)` returns true.
3. **`noGrants_noSideEffects`**: player has no "commune" grants. `communeOrderIds=emptySet()`.
   No exception; repository unchanged.
4. **`multipleStaleGrants_allRevoked`**: player has "commune" grants in orders 10, 11, 12.
   `communeOrderIds=setOf(11L)`. After check → only orderId=11 grant kept; 10 and 12 revoked.
5. **`nativeGrant_notRevoked`**: player has "native" grant in orderId=10.
   `communeOrderIds=emptySet()`. "native" grant is not touched by the commune-type filter.

### `CommunePendingNotificationServiceTest.kt`

File: `src/test/kotlin/ru/kyamshanov/comminusm/commune/service/CommunePendingNotificationServiceTest.kt`

1. **`enqueueAndDrain_returnsSingleMessage`**: enqueue("A") → drain returns ["A"]
2. **`drain_clearsQueue`**: enqueue twice → first drain returns both, second drain returns empty
3. **`drain_emptyQueueReturnsEmptyList`**: drain without enqueue → empty list, no exception
4. **`enqueue_differentPlayers_isolated`**: enqueue for player1 and player2 → drain(player1) returns only player1's messages
5. **`multipleMessages_allDeliveredInOrder`**: enqueue 3 messages → drain returns them in insertion order

---

## Constraints

- `plugin` parameter is nullable with default `null` — test mode runs consistency check synchronously
- CC-14 queue drain (in-memory) always runs on the main thread; AC-47 JDBC writes run async
- `getOrdersOfPlayerWithType()` must iterate `cache.entries.toSet()` (snapshot) for thread safety
- No `!!` operator
- Remove the two deferred TODO comments from `CommunePlayerListener`

---

## Definition of Done

- [ ] `OrderMembersRepository.getOrdersOfPlayerWithType()` added with KDoc
- [ ] `CommunePendingNotificationService` created with `enqueue()` and `drain()`
- [ ] `CommuneChatServiceImpl` injects `CommunePendingNotificationService`, enqueues for offline members
- [ ] `CommunePlayerListener` injects new deps, implements AC-47 + CC-14, removes TODO comments
- [ ] `DIContainer` wires `pendingNotificationService` and updates both call sites
- [ ] 5 + 5 = 10 tests written and passing
- [ ] `./gradlew compileKotlin` green
- [ ] `./gradlew test` green
- [ ] `./gradlew detekt ktlintCheck` green
