---
genre: how-to
module: comminusm
title: Stage 02 — RenameOrderUseCase + OrderRenameMenu
stage: 2
status: DONE
---

# Stage 02 — RenameOrderUseCase + OrderRenameMenu

## Goal

Implement the rename use case (validation + permission + DB + ArmorStand) and the Anvil GUI menu (text input, event handling, in-progress tracking).

## Files to create / change

| File | Action |
|------|--------|
| `src/main/kotlin/.../application/usecases/order/RenameOrderUseCase.kt` | CREATE interface |
| `src/main/kotlin/.../application/usecases/order/RenameOrderUseCaseImpl.kt` | CREATE impl |
| `src/main/kotlin/.../gui/OrderRenameMenu.kt` | CREATE Anvil GUI class |

## Detailed tasks

### 1. `RenameOrderUseCase.kt`

```kotlin
interface RenameOrderUseCase {
    operator fun invoke(ownerUuid: UUID, newName: String): Result<Unit>
}
```

Sealed `Result<T>`: use existing `ru.kyamshanov.comminusm.domain.value_objects.Result`.

### 2. `RenameOrderUseCaseImpl.kt`

Dependencies injected: `orderRepository: OrderRepository`

Logic of `invoke(ownerUuid, newName)`:

```
1. Validate newName:
   a. isBlank() or all Unicode-whitespace → Failure(ValidationException("empty"))
   b. length > 20 → Failure(ValidationException("too_long"))
   c. !matches Regex("[A-Za-zА-Яа-яЁё0-9\\-_]+") → Failure(ValidationException("invalid_chars"))

2. Find order: val order = orderRepository.findByOwner(ownerUuid)
              ?: return Failure(NotFoundException("order not found"))   [CC-02]

3. Check permission: if (order.ownerUuid != ownerUuid)
                        return Failure(UnauthorizedException(...))      [CC-01 — re-check at use-case call time]

4. No-op check: if (newName == order.name) return Success(Unit)       [AC-19, case-sensitive]

5. Cache old name: val oldName = order.name

6. orderRepository.rename(order.id, newName)  — throws on DB error

   On exception:
     → log error
     → return Failure(PersistenceException("db_error"))
     → caller (OrderRenameMenu) must handle: rollback in-memory + revert ArmorStand + show error

7. return Success(Unit)
```

**Note:** RenameOrderUseCaseImpl does NOT update in-memory model or ArmorStand — those happen in `OrderRenameMenu` on the main thread (Bukkit API required). The use case is Bukkit-free.

### 3. `OrderRenameMenu.kt`

Class: `class OrderRenameMenu(private val renameOrderUseCase: RenameOrderUseCase, private val plugin: Plugin) : Listener`

**In-progress tracking:**
```kotlin
private val inProgressRenames = ConcurrentHashMap<UUID, Long>()  // playerUUID → orderId
```

#### `fun open(player: Player, order: model.Order)`

```kotlin
fun open(player: Player, order: Order) {
    // CC-06: double-click guard
    if (inProgressRenames.containsKey(player.uniqueId)) return

    val anvilInv = Bukkit.createInventory(null, InventoryType.ANVIL, Component.text("Название ордера"))
    val inputItem = ItemStack(Material.PAPER).apply {
        itemMeta = itemMeta?.also { meta ->
            meta.displayName(Component.text(order.name))   // pre-fill with current name [AC-03]
        }
    }
    anvilInv.setItem(0, inputItem)
    inProgressRenames[player.uniqueId] = order.id           // track in-progress
    player.openInventory(anvilInv)
}
```

#### `@EventHandler fun onPrepareAnvil(event: PrepareAnvilEvent)`

```kotlin
// Allow output slot to be non-null so the player can click it
val text = event.inventory.renameText ?: return
val result = ItemStack(Material.PAPER).apply {
    itemMeta = itemMeta?.also { meta ->
        meta.displayName(Component.text(text))
    }
}
event.result = result
```

#### `@EventHandler fun onInventoryClick(event: InventoryClickEvent)`

```kotlin
if (event.inventory.type != InventoryType.ANVIL) return
if (event.rawSlot != 2) return                            // output slot only
val playerUuid = (event.whoClicked as? Player)?.uniqueId ?: return
val orderId = inProgressRenames[playerUuid] ?: return     // not our Anvil
event.isCancelled = true

val player = event.whoClicked as Player
val typedName = event.inventory.renameText ?: ""

// Validation error messages → action bar (AC-18); fallback to chat if not available
when {
    typedName.isBlank() || typedName.all { it.isWhitespace() } -> {
        player.sendActionBar(Component.text("§cНазвание не может быть пустым"))
        inProgressRenames.remove(playerUuid)
        player.closeInventory()
        return
    }
    typedName.length > 20 -> {
        player.sendActionBar(Component.text("§cМаксимум 20 символов"))
        inProgressRenames.remove(playerUuid)
        player.closeInventory()
        return
    }
    !typedName.matches(Regex("[A-Za-zА-Яа-яЁё0-9\\-_]+")) -> {
        player.sendActionBar(Component.text("§cНедопустимые символы. Используйте: буквы, цифры, дефис (-), подчёркивание (_)"))
        inProgressRenames.remove(playerUuid)
        player.closeInventory()
        return
    }
}

// Load current order state for in-memory tracking and ArmorStand update
val currentOrder = getOrderByOwnerUseCase(playerUuid)   // injected use case
if (currentOrder == null || currentOrder.id != orderId) {
    player.sendActionBar(Component.text("§cОрдер не найден"))
    inProgressRenames.remove(playerUuid)
    player.closeInventory()
    return
}

val oldName = currentOrder.name

// Call use case (validates + writes DB) — runs on main thread; DB write dispatched async inside
val result = renameOrderUseCase(playerUuid, typedName)   // [AC-05, AC-06, AC-07, CC-01, CC-02]

inProgressRenames.remove(playerUuid)
player.closeInventory()   // [AC-09] close current menu

when (result) {
    is Result.Success -> {
        // Update ArmorStand (main thread — safe) [AC-09, CC-03, CC-08]
        updateArmorStand(currentOrder, typedName)
        player.sendActionBar(Component.text("§aНазвание ордера изменено на '${typedName}'"))  // [AC-09]
    }
    is Result.Failure -> {
        // Rollback ArmorStand display if it was already updated optimistically (not in this flow)
        player.sendActionBar(Component.text("§cОшибка при сохранении названия. Попробуйте позже"))  // [AC-13]
    }
}
```

#### `private fun updateArmorStand(order: model.Order, newName: String)`

Only called from main thread. Handles CC-03 (entity null) and CC-08 (unloaded chunk).

```kotlin
private fun updateArmorStand(order: Order, newName: String) {
    if (!order.isActivated) return                          // no ArmorStand if not activated
    val world = Bukkit.getWorld(order.centerWorld ?: return) ?: return
    val chunk = world.getChunkAt(order.centerX shr 4, order.centerZ shr 4)
    val asKey = NamespacedKey(plugin, "armorstand/${order.id}")
    val asUuidStr = chunk.persistentDataContainer.get(asKey, PersistentDataType.STRING) ?: run {
        plugin.logger.warning("ArmorStand PDC entry not found for order ${order.id}")   // CC-03
        return
    }
    val entity = try {
        world.getEntity(UUID.fromString(asUuidStr))
    } catch (e: IllegalArgumentException) {
        plugin.logger.warning("Invalid ArmorStand UUID for order ${order.id}: $asUuidStr")
        return
    }
    if (entity == null || !entity.isValid) {
        plugin.logger.warning("ArmorStand entity not found or invalid for order ${order.id}")  // CC-03
        return
    }
    (entity as? org.bukkit.entity.ArmorStand)?.customName(Component.text(newName))
}
```

#### `@EventHandler fun onInventoryClose(event: InventoryCloseEvent)`

```kotlin
if (event.inventory.type != InventoryType.ANVIL) return
val playerUuid = (event.player as? Player)?.uniqueId ?: return
inProgressRenames.remove(playerUuid)    // cleanup on any Anvil close [AC-08, CC-07]
```

#### `@EventHandler fun onPlayerQuit(event: PlayerQuitEvent)`

```kotlin
inProgressRenames.remove(event.player.uniqueId)   // [CC-07] disconnect cleanup
```

### Async DB write pattern [premortem-R8]

The `renameOrderUseCase` MUST NOT call `orderRepository.rename()` on the main thread (SQLite write causes server lag). Use the same pattern as `FlagActivationHelper`:

```kotlin
// In OrderRenameMenu, after main-thread validation + ArmorStand update:
Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
    try {
        orderRepository.rename(order.id, typedName)
        // success callback back on main thread:
        Bukkit.getScheduler().runTask(plugin, Runnable {
            player.sendActionBar(Component.text("§aНазвание ордера изменено на '${typedName}'"))
        })
    } catch (e: Exception) {
        plugin.logger.severe("rename DB failed for order ${order.id}: ${e.message}")
        // rollback ArmorStand on main thread:
        Bukkit.getScheduler().runTask(plugin, Runnable {
            updateArmorStand(order, oldName)
            player.sendActionBar(Component.text("§cОшибка при сохранении названия. Попробуйте позже"))
        })
    }
})
```

This means:
1. Main thread: validate → close Anvil → update ArmorStand optimistically → cache oldName → launch async
2. Async: DB write
3. Main thread callback: send confirmation OR roll back ArmorStand + show error

`RenameOrderUseCaseImpl` therefore only handles validation + permission check (Bukkit-free, synchronous). The DB write and callbacks live in `OrderRenameMenu`.

## Tests to write (failing first)

- `TC-05`: invalid chars → action bar error, Anvil closes
- `TC-06`: >20 chars → action bar "Максимум 20 символов", closes
- `TC-07`: empty/whitespace → action bar error, closes
- `TC-08`: Escape (InventoryCloseEvent) → no rename, tracking cleared
- `TC-09`: valid name → use case called, action bar confirmation
- `TC-17`: DB failure → rollback message shown
- `TC-24`: same name as current → no-op (AC-19)
- `TC-25` (CC-01): player demoted before confirm → Failure(Unauthorized)
- `TC-26` (CC-02): order deleted before confirm → Failure(NotFound)
- `TC-27` (CC-03): ArmorStand null → DB updated, no NPE
- `TC-30` (CC-06): double-click → only one Anvil open
- `TC-31` (CC-07): disconnect → inProgressRenames cleared

## Definition of done

- [ ] `./gradlew compileKotlin` — no errors
- [ ] `./gradlew :[module]:test` — all tests pass
- [ ] `./gradlew detekt ktlintCheck` — clean
- [ ] No TODO/FIXME in production code
