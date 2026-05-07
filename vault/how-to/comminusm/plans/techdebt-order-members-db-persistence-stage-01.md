# Stage 01 — OrderMembersRepository: DB persistence

**TD:** TD-comminusm-order-members-db-persistence
**Module:** comminusm
**Path:** PLAN
**Goal:** Wire SQLite persistence to `OrderMembersRepository` so that order-member records survive plugin restarts.

---

## Context

`OrderMembersRepository` (`src/main/kotlin/ru/kyamshanov/comminusm/commune/repository/OrderMembersRepository.kt`) currently stores members only in a `ConcurrentHashMap`. The `order_members` table already exists in `DatabaseManager` with the schema:

```sql
CREATE TABLE IF NOT EXISTS order_members (
    order_id INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    granted_at TEXT NOT NULL,
    granted_via TEXT NOT NULL,
    PRIMARY KEY (order_id, player_uuid),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CHECK (granted_via IN ('native', 'commune'))
)
```

The DI wiring is in `DIContainer.kt`:
```kotlin
private val orderMembersRepository by lazy {
    OrderMembersRepository(ConcurrentHashMap())
}
```

Other repositories (`OrderRepositoryImpl`, `WorkdaysRepositoryImpl`) accept `Connection` directly and execute synchronous JDBC calls — follow the same pattern.

---

## Changes Required

### 1. `OrderMembersRepository.kt`

Add `connection: Connection?` as second constructor parameter (nullable — null = test/in-memory mode):

```kotlin
class OrderMembersRepository(
    private val cache: MutableMap<Long, MutableSet<OrderMember>>,
    private val connection: Connection? = null,
)
```

In `addMember()`: after updating the cache, execute an INSERT OR REPLACE on `order_members`:
```sql
INSERT OR REPLACE INTO order_members (order_id, player_uuid, granted_at, granted_via)
VALUES (?, ?, ?, ?)
```
Use parameterized query. `granted_at` is formatted as `LocalDateTime.toString()` (ISO-8601, consistent with other tables that store TEXT dates).

In `removeMember()`: after removing from cache, execute:
```sql
DELETE FROM order_members WHERE order_id = ? AND player_uuid = ?
```

Add a new `loadAll()` method:
```kotlin
fun loadAll(): List<OrderMember>
```
- SELECT all rows from `order_members`
- Build `OrderMember` from `(order_id, player_uuid, granted_at, granted_via)`
- Populate `cache` and return the list
- If `connection == null`, return emptyList()

### 2. `DIContainer.kt`

Pass `database.connection` to `OrderMembersRepository`:
```kotlin
private val orderMembersRepository by lazy {
    OrderMembersRepository(ConcurrentHashMap(), database.connection)
}
```

---

## Tests Required

File: `src/test/kotlin/ru/kyamshanov/comminusm/commune/repository/OrderMembersRepositoryPersistenceTest.kt`

Use an in-process SQLite connection (`:memory:`) to avoid file I/O in tests. Create the `order_members` table (and `orders` table for FK) in test setup.

Test cases:
1. **Round-trip add**: `addMember()` → `loadAll()` → verify member is returned with correct fields
2. **Remove persisted**: `addMember()` → `removeMember()` → `loadAll()` → verify member is gone from DB
3. **Null connection (test mode)**: `addMember()` with `connection=null` only updates in-memory cache; `loadAll()` returns emptyList()
4. **Duplicate add is idempotent**: calling `addMember()` twice with same `(orderId, playerUuid)` → DB has only one row
5. **loadAll populates cache**: after `loadAll()`, `getMembersOfOrder()` and `isMember()` return the loaded data

---

## Constraints

- Follow `OrderRepositoryImpl` pattern: synchronous JDBC, `stmt.close()` / `.use { }` pattern
- No wildcard imports in test files (Detekt WildcardImport rule)
- No magic numbers — if needed, use named constants
- Use `@Suppress("TooGenericExceptionCaught")` only if truly needed
- DB calls must use parameterized queries — no string concatenation with user data

---

## Definition of Done

- [ ] `OrderMembersRepository` constructor accepts `Connection?`
- [ ] `addMember()` persists to DB when connection != null
- [ ] `removeMember()` deletes from DB when connection != null
- [ ] `loadAll()` method added and functional
- [ ] `DIContainer` passes `database.connection`
- [ ] 5 tests written and pass
- [ ] `./gradlew compileKotlin` green
- [ ] `./gradlew test` green
- [ ] `./gradlew detekt ktlintCheck` green
