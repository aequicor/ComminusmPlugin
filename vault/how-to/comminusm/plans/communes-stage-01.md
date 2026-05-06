---
genre: how-to
module: comminusm
title: Stage 01 — Foundation Data Layer & Services
topic: communes
date: 2026-05-06
author: "@Main"
related:
  - vault/reference/comminusm/spec/communes.md
  - vault/concepts/comminusm/plans/communes-plan.md
---

# Stage 01 — Foundation: Data Layer & Services

**Objective:** Implement storage, repositories, and core services for member management and communes.  
**Duration:** ~1 day  
**Dependencies:** None (builds on existing OrderService read API)

---

## Components to Implement

### 1. OrderMember Model

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/model/OrderMember.kt`

```kotlin
data class OrderMember(
    val orderId: Long,
    val playerUUID: UUID,
    val grantedAt: Instant,
    val grantedVia: String, // "native" or "commune"
)
```

**Invariants:**
- `grantedVia` must be "native" or "commune"
- At service level: prevent duplicate (order_id, player_uuid, granted_via) tuples

---

### 2. OrderMembersRepository

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/repository/OrderMembersRepository.kt`

**Responsibility:** SQL CRUD for `order_members` table via `DatabaseManager`.

**Public API:**

```kotlin
interface OrderMembersRepository {
    // Async reads
    suspend fun getMembers(orderId: Long): Set<OrderMember>
    suspend fun getMembersOfPlayer(playerUUID: UUID): Set<OrderMember>
    suspend fun isMember(orderId: Long, playerUUID: UUID): Boolean
    
    // Async writes
    suspend fun addMember(orderId: Long, playerUUID: UUID, grantedVia: String): Boolean
    suspend fun removeMember(orderId: Long, playerUUID: UUID): Boolean
    suspend fun removeAllMembers(orderId: Long): Int // return count removed
    
    // Batch operations (for cascade)
    suspend fun removeMembersWithGrantedVia(orderId: Long, grantedVia: String): Int
}
```

**Implementation notes:**
- Use `DatabaseManager.executeAsync()` for all DB access
- In-memory cache: `ConcurrentHashMap<Long, Set<OrderMember>>` (keyed by orderId)
- Eager load from DB at startup (before `CommuneStartupTask`)
- All reads hit cache first; writes update cache immediately, then async persist
- On DB error: log at ERROR level; cache remains in last-known state; next startup AC-47 reconciles

**Tests:**
- Unit: CRUD operations, cache consistency, duplicate detection
- Integration: DatabaseManager interaction, concurrent reads/writes

---

### 3. Commune Model

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/model/Commune.kt`

```kotlin
data class Commune(
    val id: UUID,
    val orderIds: MutableSet<Long>,
    var version: Long, // incremented on every membership change
    val createdAt: Instant,
) {
    fun addOrder(orderId: Long) {
        orderIds.add(orderId)
        version++
    }
    
    fun removeOrder(orderId: Long) {
        orderIds.remove(orderId)
        version++
    }
    
    val isEmpty: Boolean
        get() = orderIds.isEmpty()
}
```

**Invariants:**
- `orderIds` is the authoritative set of order IDs in the commune
- `version` auto-increments on any add/remove (used for stale-state detection § 5.4 of spec)

---

### 4. CommuneService

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/CommuneService.kt`

**Responsibility:** In-memory source of truth for communes. Lifecycle: create, add order, remove order, existence checks.

**Public API:**

```kotlin
interface CommuneService {
    // Queries
    fun getCommuneByOrder(orderId: Long): Commune?
    fun getCommuneById(communeId: UUID): Commune?
    fun getOrdersInCommune(communeId: UUID): Set<Long>?
    
    // Lifecycle (atomic in-memory, async persist handled by caller)
    fun createCommune(orderId: Long): Commune
    fun addOrderToCommune(communeId: UUID, orderId: Long)
    fun removeOrderFromCommune(communeId: UUID, orderId: Long)
    fun dissolveCommune(communeId: UUID)
    
    // Batch load (at startup)
    fun loadFromStorage(communes: List<Commune>)
    fun getAllCommunes(): Collection<Commune>
}
```

**Implementation:**
- Internal map: `ConcurrentHashMap<UUID, Commune>`
- Index: `ConcurrentHashMap<Long, UUID>` (orderId → communeId for fast lookup)
- All operations are in-memory only; persistence is async (caller's responsibility)
- Thread-safe via ConcurrentHashMap

**Tests:**
- Unit: create, add/remove orders, lookup by order, batch load
- Concurrent: multiple threads adding/removing orders

---

### 5. CommuneInvitation Model

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/model/CommuneInvitation.kt`

```kotlin
data class CommuneInvitation(
    val id: UUID,
    val communeId: UUID,
    val fromOrderId: Long,
    val targetOrderId: Long,
    val targetLeaderUUID: UUID,
    val expiresAt: Instant,
)
```

---

### 6. CommuneInvitationService

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/CommuneInvitationService.kt`

**Responsibility:** Manage pending commune invitations (create, replace, expire, cancel).

**Public API:**

```kotlin
interface CommuneInvitationService {
    fun createInvitation(
        communeId: UUID,
        fromOrderId: Long,
        targetOrderId: Long,
        targetLeaderUUID: UUID,
    ): CommuneInvitation
    
    fun getInvitation(invitationId: UUID): CommuneInvitation?
    fun getInvitationsFor(targetOrderId: Long): List<CommuneInvitation>
    fun getActiveInvitationsFromCommune(communeId: UUID): List<CommuneInvitation>
    
    fun replaceInvitation(
        communeId: UUID,
        targetOrderId: Long,
        oldInvitationId: UUID,
        newLeaderUUID: UUID,
    ): CommuneInvitation
    
    fun cancelInvitation(invitationId: UUID)
    fun expireInvitation(invitationId: UUID) // called by expiry timer
    
    fun loadFromStorage(invitations: List<CommuneInvitation>)
}
```

**Implementation:**
- Internal map: `ConcurrentHashMap<UUID, CommuneInvitation>` (by invitation ID)
- Index: `ConcurrentHashMap<Long, List<UUID>>` (targetOrderId → list of invitation IDs)
- Expiry timer: `ScheduledExecutorService` with task per invitation (expires after 500 s)
- On expiry: auto-remove from maps; publish `InvitationExpiredEvent` (optional, for logging)
- On replace: cancel old timer, remove old invitation, create new one with new timer

**Tests:**
- Unit: create, replace, expire, lookup
- Integration: timer-based expiry, concurrent operations

---

### 7. OrderMembershipService

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/OrderMembershipService.kt`

**Responsibility:** High-level API for native member management. Publishes events for cross-order recalc.

**Public API:**

```kotlin
interface OrderMembershipService {
    suspend fun isNativeMember(orderId: Long, playerUUID: UUID): Boolean
    suspend fun isMember(orderId: Long, playerUUID: UUID): Boolean
    suspend fun getMembersOfOrder(orderId: Long): Set<OrderMember>
    suspend fun getOrdersOfPlayer(playerUUID: UUID): Set<OrderMember>
    
    suspend fun inviteMember(orderId: Long, targetPlayerUUID: UUID): Boolean
    suspend fun acceptMembership(orderId: Long, playerUUID: UUID): Boolean
    suspend fun removeMember(orderId: Long, playerUUID: UUID): Boolean
    suspend fun removeMemberSilently(orderId: Long, playerUUID: UUID) // for cascade, no event
    
    fun listNativeOrders(playerUUID: UUID): Set<Long> // for friendly-fire check
}
```

**Events published:**
- `OrderMemberAddedEvent(orderId, playerUUID, grantedVia)` — trigger cross-order recalc
- `OrderMemberRemovedEvent(orderId, playerUUID, grantedVia)` — trigger cross-order recalc

**Implementation:**
- Delegates to `OrderMembersRepository` for persistence
- Publishes events via `Bukkit.getPluginManager().callEvent()`
- All writes are async (wrap in `launch { ... }` coroutine)

**Tests:**
- Unit: invite, accept, remove, event publishing
- Integration: event propagation to listeners

---

## Data Model: `order_members` Table

**SQL Schema:**

```sql
CREATE TABLE order_members (
    order_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    granted_via VARCHAR(16) NOT NULL,
    
    PRIMARY KEY (order_id, player_uuid),
    UNIQUE (order_id, player_uuid),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    CHECK (granted_via IN ('native', 'commune'))
);
```

**Migration:**
- If table doesn't exist, create it at startup (check via DatabaseManager)
- If exists, load all rows into cache

---

## Startup Sequence

1. `ComminusmPlugin.onEnable()`
2. Load `order_members` table into `OrderMembersRepository` cache
3. Load `communes` table into `CommuneService` cache
4. Load `commune_orders` joins into `CommuneService` cache
5. Load `commune_invitations` into `CommuneInvitationService`; reschedule expiry timers
6. Register listeners (in next stages)

---

## Tests for Stage 01

### Unit Tests

- `OrderMembersRepositoryTest`
  - Add member (native and commune)
  - Remove member
  - Get members of order
  - Cache consistency after add/remove
  - Duplicate prevention

- `CommuneServiceTest`
  - Create commune
  - Add/remove orders
  - Get commune by order / by ID
  - Version increment
  - Dissolve

- `CommuneInvitationServiceTest`
  - Create invitation
  - Replace invitation (old timer canceled)
  - Get invitations for target
  - Expire (manual call)
  - Load from storage

- `OrderMembershipServiceTest`
  - Invite member
  - Accept membership
  - Remove member
  - Event publishing
  - Concurrent invites

### Integration Tests

- Load from storage + cache consistency
- Concurrent reads/writes (stress test)
- Cascade remove (multiple orders)

---

## DoD Checklist

- [ ] All classes compile without errors
- [ ] `./gradlew compileKotlin` passes
- [ ] Unit tests: `./gradlew test` passes
- [ ] `./gradlew detekt ktlintCheck` passes
- [ ] All public APIs documented (KDoc)
- [ ] No TODOs without DECISIONS.md entry
- [ ] TableName schema verified against spec §3

---
