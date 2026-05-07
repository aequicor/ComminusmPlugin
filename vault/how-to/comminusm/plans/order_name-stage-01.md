---
genre: how-to
module: comminusm
title: Stage 01 — Data layer + Default name
stage: 1
status: DONE
---

# Stage 01 — Data layer + Default name

## Goal

Add `name` field to the domain Order entity, update repository CRUD, set default name on order creation, use order name on ArmorStand.

## Files to change

| File | Change |
|------|--------|
| `src/main/kotlin/.../domain/entities/Order.kt` | Add `name: String = ""` field |
| `src/main/kotlin/.../domain/repositories/OrderRepository.kt` | Add `rename(id: Long, name: String)` |
| `src/main/kotlin/.../infrastructure/repositories/OrderRepositoryImpl.kt` | Implement rename; update insert to include name; update SELECT to read name |
| `src/main/kotlin/.../application/usecases/order/CreateOrderUseCaseImpl.kt` | Set default name from player nickname (sanitize) |
| `src/main/kotlin/.../manager/FlagActivationHelper.kt` | Pass order name as ArmorStand title instead of flagTitleFormat |

## Detailed tasks

### 1. `domain/entities/Order.kt`

Add `name: String = ""` as a field in the data class, after `ownerUuid`:

```kotlin
data class Order(
    val id: Long = 0,
    val ownerUuid: UUID,
    val name: String = "",   // ← ADD
    val level: Int = 1,
    ...
)
```

### 2. `domain/repositories/OrderRepository.kt`

Add method to the interface:
```kotlin
fun rename(id: Long, name: String)
```

### 3. `infrastructure/repositories/OrderRepositoryImpl.kt`

**a. Update `insert()` to include name:**
```sql
INSERT INTO orders (owner_uuid, name, level, radius) VALUES (?, ?, ?, ?)
```
Bind name as 2nd parameter.

**b. Update all SELECT queries** to include `name` column and map it to `Order.name`.

**c. Add `rename()` implementation:**
```kotlin
override fun rename(id: Long, name: String) {
    val sql = "UPDATE orders SET name = ? WHERE id = ?"
    connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, name)
        stmt.setLong(2, id)
        stmt.executeUpdate()
    }
}
```

### 4. `CreateOrderUseCaseImpl.kt`

Before calling `orderRepository.insert(order)`, sanitize the player's nickname into a valid order name:

```kotlin
fun sanitizeNickname(nickname: String): String {
    val result = nickname.replace(Regex("[^A-Za-zА-Яа-яЁё0-9\\-_]"), "_")
    return if (result.isBlank() || result.all { it == '_' }) "Order" else result.take(20)
}
```

Set `order = order.copy(name = sanitizeNickname(player.name))` before insert.

After insert, notify player: `"Название вашего ордера установлено на '${order.name}'"` via action bar or chat.

Note: `player.name` is the Bukkit player name (nickname) at creation time.

### 5. `FlagActivationHelper.kt`

The `activate()` method (or `phase2DbAndArmorStand()`) currently builds the title as:
```kotlin
val title = config.flagTitleFormat.replace("{type}", flagType).replace("{player}", ownerName)
stand.customName(Component.text(title))
```

**Change**: Accept `orderName: String` as a parameter to the relevant method and use it as the ArmorStand display name:
```kotlin
stand.customName(Component.text(orderName))
```

Trace the call chain from `ActivateOrderUseCaseImpl` (or `OrderService.activate()`) to FlagActivationHelper and thread the order name through. The name is available from the domain Order entity after it's loaded from DB.

## Tests to write (failing first, TDD)

- `TC-01`: Order created → `Order.name` equals sanitized player nickname
- `TC-19`: Nickname "Player#123" → name "Player_123"
- `TC-20`: Nickname "###" (all invalid) → name "Order"
- `TC-11 (CC-12)`: Nickname longer than 20 chars → name is first 20 valid chars

### 6. Fallback for existing orders with blank name [premortem-R1]

Existing orders in DB have `name = ''` (DEFAULT ''). No DB migration is required for MVP. Instead, add a display fallback:

In `OrderMenu.open()` and anywhere `order.name` is rendered, use:
```kotlin
val displayName = order.name.ifBlank { "Ордер №${order.id}" }
```

This prevents blank names in the menu and on ArmorStand until the owner renames.

The rename button lore should also use `displayName`, not `order.name` directly.

### 7. Update `DomainToModelAdapter` [premortem-R2]

After adding `name` to `domain/entities/Order.kt`, locate `DomainToModelAdapter.toPresentationModel()` and ensure it maps `domainOrder.name` to `modelOrder.name`. If the adapter auto-maps by field name this may be implicit — verify explicitly.

### 8. Thread order name through activation call chain [premortem-R7]

Trace `ActivateOrderUseCaseImpl` → `OrderService.activate()` → `FlagActivationHelper.activate()` and ensure `order.name` (loaded from DB after insert) is passed to `FlagActivationHelper` for use as ArmorStand title. If the order is loaded before activation, the name is available. Verify the exact call site and add the parameter explicitly.

## Definition of done for this stage

- [ ] `./gradlew compileKotlin` — no errors
- [ ] `./gradlew :[module]:test` — all tests pass
- [ ] `./gradlew detekt ktlintCheck` — clean
- [ ] SELECT queries return `name` column; no null pointer on existing rows (DEFAULT '' in DB)
