---
genre: how-to
module: comminusm
title: Stage 02 — Core Business Logic & Listeners
topic: communes
date: 2026-05-06
author: "@Main"
related:
  - vault/reference/comminusm/spec/communes.md
  - vault/concepts/comminusm/plans/communes-plan.md
---

# Stage 02 — Core Business Logic & Listeners

**Objective:** Implement commune management logic, cross-order membership, and event-driven cascades.  
**Duration:** ~1-2 days  
**Dependencies:** Stage 01 (OrderMembersRepository, CommuneService, CommuneInvitationService)

---

## Components to Implement

### 1. CrossOrderMembershipService

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/CrossOrderMembershipService.kt`

**Responsibility:** Grant/revoke cross-order Member roles (via `order_members` table). Atomic in-memory + async persist. Suppresses cascading recalculations via batch mode.

**Public API:**

```kotlin
interface CrossOrderMembershipService {
    // Grant cross-order membership
    suspend fun grantCrossOrderMember(
        nativeOrderId: Long,  // player's native order
        hostOrderId: Long,    // order to add member into
        playerUUID: UUID,
    ): Boolean  // true if granted; false if already member or error
    
    // Revoke cross-order membership
    suspend fun revokeCrossOrderMember(
        nativeOrderId: Long,
        hostOrderId: Long,
        playerUUID: UUID,
    ): Boolean
    
    // Get all cross-order orders for a player
    suspend fun getCrossOrderOrders(playerUUID: UUID, nativeOrderId: Long): Set<Long>
    
    // Recalculate rights for a player (re-evaluate all memberships)
    suspend fun recalculateCrossOrderRights(playerUUID: UUID)
}
```

**Batch Cascade Mode (CC-Q5):**

```kotlin
private val cascadeModeThreadLocal = ThreadLocal<Boolean>()

fun enterCascadeMode() {
    cascadeModeThreadLocal.set(true)
}

fun exitCascadeMode() {
    cascadeModeThreadLocal.remove()
}

fun isInCascadeMode(): Boolean = cascadeModeThreadLocal.get() ?: false
```

**Implementation notes:**
- When granting: add to cache immediately, then async persist via `OrderMembersRepository.addMember(..., granted_via='commune')`
- When revoking: remove from cache immediately, then async persist via `OrderMembersRepository.removeMember(...)`
- `recalculateCrossOrderRights`: called by `OrderMemberRemovedEvent` listener to re-evaluate player's cross-order grants
  - **UNLESS** `isInCascadeMode()` returns true (in which case skip the call; parent context will call once at end)
- Atomic semantics: updates happen in-memory first (visible to next call); async persist is eventual
- On DB error: log at ERROR level; next startup AC-47 reconciles

**Tests:**
- Unit: grant/revoke, batch mode suppression, recalculation logic
- Integration: concurrent grants, cascade mode context

---

### 2. CommuneStartupTask

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/startup/CommuneStartupTask.kt`

**Responsibility:** Async initialization on `onEnable()`. Load storage, full consistency scan (AC-47), graceful degrade on error.

**Public API:**

```kotlin
class CommuneStartupTask(
    private val databaseManager: DatabaseManager,
    private val communeService: CommuneService,
    private val communeInvitationService: CommuneInvitationService,
    private val orderMembersRepository: OrderMembersRepository,
    private val orderMembershipService: OrderMembershipService,
) {
    suspend fun startup(): StartupResult {
        return try {
            // 1. Load communes
            val communes = databaseManager.loadCommunes()
            communeService.loadFromStorage(communes)
            
            // 2. Load commune orders
            val communeOrders = databaseManager.loadCommuneOrders()
            communeOrders.forEach { (communeId, orderId) ->
                communeService.addOrderToCommune(communeId, orderId)
            }
            
            // 3. Load invitations
            val invitations = databaseManager.loadCommuneInvitations()
            communeInvitationService.loadFromStorage(invitations)
            // Reschedule expiry timers
            invitations.forEach { invitation ->
                scheduleExpiry(invitation)
            }
            
            // 4. Consistency scan (AC-47)
            val issues = performConsistencyScan()
            
            StartupResult(success = true, issues = issues)
        } catch (e: DatabaseUnavailableException) {
            logger.error("Commune startup failed: database unavailable", e)
            StartupResult(
                success = false,
                issues = listOf("DatabaseManager unavailable: ${e.message}")
            )
        }
    }
    
    private suspend fun performConsistencyScan(): List<String> {
        val issues = mutableListOf<String>()
        
        // 1. Verify all commune members are valid orders
        for (commune in communeService.getAllCommunes()) {
            for (orderId in commune.orderIds) {
                if (orderService.getOrderById(orderId) == null) {
                    issues.add("Orphan order in commune: $orderId in ${commune.id}")
                }
            }
        }
        
        // 2. Verify cross-order members have valid native orders
        for ((orderId, members) in orderMembersRepository.getAllMembers()) {
            for (member in members.filter { it.grantedVia == "commune" }) {
                val nativeOrders = orderMembershipService.listNativeOrders(member.playerUUID)
                if (nativeOrders.isEmpty()) {
                    issues.add("Cross-order member has no native orders: ${member.playerUUID} in $orderId")
                    // Cleanup: remove this cross-order grant
                    orderMembersRepository.removeMember(orderId, member.playerUUID)
                }
            }
        }
        
        // 3. Verify orphaned orders are not in any commune
        // (orders in commune_orders not in communes table)
        
        return issues
    }
}

data class StartupResult(val success: Boolean, val issues: List<String> = emptyList())
```

**Graceful Degrade:**
- If DB error: startup returns `success=false`, app continues (commune system disabled)
- Log CRITICAL message to alert admins
- On next startup, AC-47 scan will reconcile if DB is back

**Tests:**
- Unit: load from DB, AC-47 scan, issue detection
- Integration: startup with DB, startup without DB, recovery after DB comes back

---

### 3. CommuneOrderDestroyListener

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/CommuneOrderDestroyListener.kt`

**Responsibility:** Observe `FlagDeactivatedEvent` (published by `OrderService.deleteByOwner`), trigger cascade commune exit.

**Implementation (spec §6.6, CC-S01):**

```kotlin
class CommuneOrderDestroyListener(
    private val communeService: CommuneService,
    private val crossOrderMembershipService: CrossOrderMembershipService,
    private val orderMembershipService: OrderMembershipService,
    private val communeChatService: CommuneChatService,
) : Listener {
    
    @EventHandler
    suspend fun onOrderDestroyed(event: FlagDeactivatedEvent) {
        val orderId = event.orderId
        val commune = communeService.getCommuneByOrder(orderId) ?: return
        
        // Enter cascade mode (suppress individual recalculations)
        crossOrderMembershipService.enterCascadeMode()
        val affectedPlayers = mutableSetOf<UUID>()
        
        try {
            // Step 1: Execute commune-leave cascade (spec 6.5 steps 5-11)
            // Revoke all cross-order rights in both directions
            val communeMembers = communeService.getOrdersInCommune(commune.id) ?: emptySet()
            for (otherOrderId in communeMembers) {
                if (otherOrderId == orderId) continue
                val members = orderMembershipService.getMembersOfOrder(otherOrderId)
                for (member in members.filter { it.grantedVia == "commune" }) {
                    try {
                        crossOrderMembershipService.revokeCrossOrderMember(
                            otherOrderId, orderId, member.playerUUID
                        )
                        affectedPlayers.add(member.playerUUID)
                    } catch (e: Exception) {
                        logger.error("Error revoking cross-order member during cascade: ${member.playerUUID} in $orderId", e)
                    }
                }
            }
            
            // Step 2: Remove order from commune
            communeService.removeOrderFromCommune(commune.id, orderId)
            
            // Step 3: Delete native members of destroyed order
            val nativeMembers = orderMembershipService.getMembersOfOrder(orderId)
                .filter { it.grantedVia == "native" }
            for (member in nativeMembers) {
                try {
                    orderMembershipService.removeMemberSilently(orderId, member.playerUUID)
                    affectedPlayers.add(member.playerUUID)
                } catch (e: Exception) {
                    logger.error("Error removing native member during cascade: ${member.playerUUID} in $orderId", e)
                }
            }
            
            // Step 4: Check if commune is now empty
            if (communeService.getCommuneById(commune.id)?.isEmpty == true) {
                dissolveCommuneInternal(commune.id)
            }
            
        } finally {
            // Step 5: Exit cascade mode and recalculate once per player
            crossOrderMembershipService.exitCascadeMode()
            for (playerUUID in affectedPlayers) {
                try {
                    crossOrderMembershipService.recalculateCrossOrderRights(playerUUID)
                } catch (e: Exception) {
                    logger.error("Error recalculating rights for $playerUUID", e)
                }
            }
            logger.info("Order destroy cascade completed for orderId=$orderId (commune=${commune.id}, affected=${affectedPlayers.size} players)")
        }
    }
    
    private suspend fun dissolveCommuneInternal(communeId: UUID) {
        // Implement commune dissolution logic from spec 6.7
        // (stub for now; full implementation in later revision)
    }
}
```

**Registration:**
```kotlin
// In ComminusmPlugin.onEnable():
Bukkit.getPluginManager().registerEvents(
    CommuneOrderDestroyListener(...),
    this
)
```

**Tests:**
- Unit: cascade logic, error recovery, affected player collection
- Integration: order destruction → commune updates → player right recalc

---

### 4. CommuneMembershipListener

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/CommuneMembershipListener.kt`

**Responsibility:** Observe `OrderMemberRemovedEvent`, trigger cross-order rights recalculation (AC-25).

```kotlin
class CommuneMembershipListener(
    private val crossOrderMembershipService: CrossOrderMembershipService,
) : Listener {
    
    @EventHandler
    suspend fun onMemberRemoved(event: OrderMemberRemovedEvent) {
        // If a player loses membership in an order, recalculate all their cross-order grants
        // (because they may have lost access to the native order that granted them commune status)
        crossOrderMembershipService.recalculateCrossOrderRights(event.playerUUID)
    }
}
```

---

### 5. CommunePlayerListener

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/CommunePlayerListener.kt`

**Responsibility:** Observe `PlayerJoinEvent`, run consistency check (AC-47), deliver offline notifications (CC-14).

```kotlin
class CommunePlayerListener(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
    private val communeChatService: CommuneChatService,
) : Listener {
    
    @EventHandler
    suspend fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        // 1. Consistency check: verify player's commune memberships are still valid
        val nativeOrders = orderMembershipService.listNativeOrders(player.uniqueId)
        for (nativeOrderId in nativeOrders) {
            val commune = communeService.getCommuneByOrder(nativeOrderId) ?: continue
            // Verify native order is still in this commune
            if (nativeOrderId !in (communeService.getOrdersInCommune(commune.id) ?: emptySet())) {
                // Inconsistency detected: native order no longer in commune (shouldn't happen, but handle)
                logger.warn("Consistency: player ${player.name} native order $nativeOrderId not in commune ${commune.id}")
                // Reset commune chat toggle for this player
                communeChatService.setToggleMode(player.uniqueId, false)
            }
        }
        
        // 2. Deliver offline notifications (CC-14)
        // (stub: implementation depends on notification system)
    }
}
```

---

### 6. FriendlyFireListener

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/FriendlyFireListener.kt`

**Responsibility:** Handle `EntityDamageByEntityEvent`, prevent damage between commune members (AC-20, AC-22).

**Key rules (spec AC-22):**
- A player's "native orders" = owner order + all native member orders (granted_via != 'commune')
- Friendly-fire protection: if attacker's native orders and target's native orders share a commune → block damage
- Cross-order membership (granted_via='commune') does NOT count for native orders

```kotlin
class FriendlyFireListener(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
) : Listener {
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    suspend fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.damager !is Player || event.entity !is Player) return
        
        val attacker = event.damager as Player
        val target = event.entity as Player
        
        // Get native orders for both players
        val attackerNativeOrders = orderMembershipService.listNativeOrders(attacker.uniqueId)
        val targetNativeOrders = orderMembershipService.listNativeOrders(target.uniqueId)
        
        // Check if any native order pair shares a commune
        for (attackerOrder in attackerNativeOrders) {
            val attackerCommune = communeService.getCommuneByOrder(attackerOrder) ?: continue
            for (targetOrder in targetNativeOrders) {
                if (targetOrder in (communeService.getOrdersInCommune(attackerCommune.id) ?: emptySet())) {
                    // Friendly fire detected
                    event.isCancelled = true
                    attacker.sendMessage("§7Это ваш союзник")
                    return
                }
            }
        }
    }
}
```

---

## Events to Define

### OrderMemberAddedEvent

```kotlin
class OrderMemberAddedEvent(
    val orderId: Long,
    val playerUUID: UUID,
    val grantedVia: String, // "native" or "commune"
) : Event() {
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
```

### OrderMemberRemovedEvent

```kotlin
class OrderMemberRemovedEvent(
    val orderId: Long,
    val playerUUID: UUID,
    val grantedVia: String,
) : Event() {
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
```

---

## Tests for Stage 02

### Unit Tests

- `CrossOrderMembershipServiceTest`
  - Grant cross-order member
  - Revoke cross-order member
  - Batch cascade mode (suppress recalculations)
  - Recalculate rights

- `CommuneOrderDestroyListenerTest`
  - Order destroyed → commune cascade
  - Affected players collected
  - Native members removed
  - Commune dissolved if empty

- `CommuneMembershipListenerTest`
  - Member removed → recalc triggered

- `CommunePlayerListenerTest`
  - Join → consistency check

- `FriendlyFireListenerTest`
  - Same commune → no damage
  - Different communes → damage allowed
  - Cross-order membership not counted

- `CommuneStartupTaskTest`
  - Load from DB
  - AC-47 consistency scan
  - Issue detection
  - Graceful degrade on DB error

### Integration Tests

- Full cascade: order destroyed → all cross-order rights revoked → commune dissolved
- Concurrent operations: multiple listeners firing simultaneously
- Event propagation: member added → listeners respond

---

## DoD Checklist

- [ ] All classes compile
- [ ] `./gradlew compileKotlin` passes
- [ ] Unit tests: `./gradlew test` passes
- [ ] `./gradlew detekt ktlintCheck` passes
- [ ] All public APIs documented
- [ ] Event handlers registered in `ComminusmPlugin.onEnable()`
- [ ] Cascade error handling implemented (try/catch + continue)
- [ ] Batch cascade mode working (no N² recalculations)
- [ ] AC-47 consistency scan implemented

---
