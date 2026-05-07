# Stage 01 — CommuneStartupTask: loadCommunes() + performConsistencyCheck()

**TD:** TD-comminusm-startup-db-load-and-consistency-scan
**Module:** comminusm
**Path:** PLAN
**Goal:** Implement the two empty stubs in `CommuneStartupTask` so that communes survive plugin restarts and AC-47 orphan cross-order members are cleaned on startup.

---

## Context

`CommuneStartupTask.loadCommunes()` and `performConsistencyCheck()` are empty stubs. Both run asynchronously inside `Bukkit.getScheduler().runTaskAsynchronously()` — synchronous JDBC is safe here.

Prerequisites already satisfied:
- `OrderMembersRepository.loadAll()` is now implemented (TD-comminusm-order-members-db-persistence).
- `DatabaseManager` has tables `communes`, `commune_orders`, `order_members` (read only in this stage).
- `CommuneService` maps (`communes`, `orderToCommuneId`) are empty on startup.

---

## Changes Required

### 1. `CommuneService.kt` — add two public methods

**`restoreCommune(commune: Commune)`** — bulk-load an existing commune from storage, bypassing creation logic:
```kotlin
fun restoreCommune(commune: Commune) {
    lock.write {
        communes[commune.id] = commune
        commune.orderIds.forEach { orderId ->
            orderToCommuneId[orderId] = commune.id
        }
    }
}
```

**`getAllCommunes(): List<Commune>`** — needed by consistency check:
```kotlin
fun getAllCommunes(): List<Commune> = lock.read { communes.values.toList() }
```

Both methods need KDoc.

### 2. `CommuneStartupTask.kt` — inject dependencies + implement stubs

Extend constructor to:
```kotlin
class CommuneStartupTask(
    @Suppress("UNUSED_PARAMETER") private val communeService: CommuneService,
    private val orderMembersRepository: OrderMembersRepository,
    private val connection: Connection? = null,
    private val plugin: Plugin? = null,
)
```
(The `@Suppress("UNUSED_PARAMETER")` on `communeService` should be removed since it's now used.)

**`loadCommunes()` implementation:**
```kotlin
internal fun loadCommunes() {
    val conn = connection ?: return  // null = no-db mode (tests)
    try {
        // 1. Load communes and their order associations
        val communeOrders = mutableMapOf<UUID, MutableSet<Long>>()
        conn.prepareStatement("SELECT commune_id, order_id FROM commune_orders").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val communeId = UUID.fromString(rs.getString("commune_id"))
                    val orderId = rs.getLong("order_id")
                    communeOrders.getOrPut(communeId) { mutableSetOf() }.add(orderId)
                }
            }
        }

        conn.prepareStatement("SELECT id, created_at, version FROM communes").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val communeId = UUID.fromString(rs.getString("id"))
                    val createdAt = LocalDateTime.parse(rs.getString("created_at"))
                    val version = rs.getLong("version")
                    val orderIds = communeOrders[communeId] ?: emptySet()

                    val commune = Commune(
                        id = communeId,
                        orderIds = orderIds,
                        version = version,
                        createdAt = createdAt,
                        createdBy = SENTINEL_UUID,  // not stored in DB schema; use sentinel
                    )
                    communeService.restoreCommune(commune)
                }
            }
        }

        // 2. Load order members into cache
        orderMembersRepository.loadAll()

    } catch (e: RuntimeException) {
        storageLoadFailed = true
        Bukkit.getLogger().warning("Failed to load communes: ${e.message}")
    }
}
```

Add `companion object` constant:
```kotlin
companion object {
    private val SENTINEL_UUID = UUID(0L, 0L)
}
```

**`performConsistencyCheck()` implementation:**
```kotlin
internal fun performConsistencyCheck() {
    val allCommunes = communeService.getAllCommunes()
    for (commune in allCommunes) {
        for (orderId in commune.orderIds) {
            val crossOrderMembers = orderMembersRepository.getMembersWithType(orderId, "commune")
            for (member in crossOrderMembers) {
                val hasNativeInCommune = commune.orderIds.any { oid ->
                    orderMembersRepository.getMembersWithType(oid, "native")
                        .any { it.playerUuid == member.playerUuid }
                }
                if (!hasNativeInCommune) {
                    orderMembersRepository.removeMember(orderId, member.playerUuid)
                    Bukkit.getLogger().info(
                        "AC-47: Removed stale cross-order member ${member.playerUuid} from order $orderId"
                    )
                }
            }
        }
    }
}
```

Remove the `@Suppress("UNUSED_PARAMETER")` on `communeService` from the constructor now that it is used.

### 3. `DIContainer.kt` — update `communeStartupTask`

```kotlin
val communeStartupTask by lazy {
    CommuneStartupTask(communeService, orderMembersRepository, database.connection, plugin)
}
```

Change `orderMembersRepository` visibility from `private` to `internal` (or keep private and pass it directly as an expression). Actually — since it's `private` but used in two places now, leave private and pass the reference inline.

---

## Tests Required

File: `src/test/kotlin/ru/kyamshanov/comminusm/commune/listener/CommuneStartupTaskTest.kt`

Use in-memory SQLite for DB operations. Test cases:

1. **`loadCommunes_populatesCommuneService`**: insert rows in `communes` + `commune_orders` → call `loadCommunes()` → verify `communeService.getCommuneOfOrder(orderId)` returns the commune.
2. **`loadCommunes_populatesOrderMembersCache`**: insert `order_members` rows → call `loadCommunes()` → verify `orderMembersRepository.isMember()`.
3. **`loadCommunes_nullConnection_noSideEffects`**: `connection=null` → call `loadCommunes()` → `communeService.getAllCommunes()` is empty, no exception.
4. **`performConsistencyCheck_removesOrphanCrossOrderMember`**: Set up commune {orderA, orderB}; add player to orderB with `grantedVia="commune"` but player has NO native order in orderA or orderB → `performConsistencyCheck()` → player removed from orderB.
5. **`performConsistencyCheck_keepsValidCrossOrderMember`**: Set up commune {orderA, orderB}; player has native in orderA and commune-granted in orderB → `performConsistencyCheck()` → player stays in orderB.
6. **`loadCommunes_storageFailure_setsStorageLoadFailed`**: inject a connection that throws on first `prepareStatement` call → `loadCommunes()` → `storageLoadFailed == true`.

---

## Constraints

- `communeService` `@Suppress("UNUSED_PARAMETER")` annotation MUST be removed from constructor
- No `!!` operator — use `?: return` for nullable connection
- Use `.use {}` for all JDBC resources  
- No string concatenation in SQL
- `SENTINEL_UUID` is a named constant, not a magic value

---

## Definition of Done

- [ ] `CommuneService.restoreCommune()` and `getAllCommunes()` added with KDoc
- [ ] `CommuneStartupTask` constructor extended with `orderMembersRepository` + `connection`
- [ ] `loadCommunes()` fully implemented: loads communes + commune_orders + order members
- [ ] `performConsistencyCheck()` fully implemented: AC-47 orphan scan + remove
- [ ] `DIContainer` updated: `communeStartupTask` passes all 4 dependencies
- [ ] 6 tests written and passing
- [ ] `./gradlew compileKotlin` green
- [ ] `./gradlew test` green
- [ ] `./gradlew detekt ktlintCheck` green
