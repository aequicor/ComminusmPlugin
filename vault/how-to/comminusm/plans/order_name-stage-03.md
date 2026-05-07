---
genre: how-to
module: comminusm
title: Stage 03 — OrderMenu button + DI wiring
stage: 3
status: TODO
---

# Stage 03 — OrderMenu button + DI wiring

## Goal

Add the ANVIL rename button to `OrderMenu`, apply enabled/disabled state based on leader check, wire `OrderRenameMenu` into the plugin's listener registration.

## Files to change

| File | Change |
|------|--------|
| `src/main/kotlin/.../gui/OrderMenu.kt` | Add rename button (slot 13); handle click; inject `OrderRenameMenu` |
| Main plugin class (locate via `Bukkit.getServer().pluginManager.registerEvents`) | Register `OrderRenameMenu` listener; inject `RenameOrderUseCase` |

## Detailed tasks

### 1. `OrderMenu.kt`

**a. Add `renameSlot` constant and `orderRenameMenu` dependency:**

```kotlin
private val renameSlot = 13

class OrderMenu(
    ...existing params...,
    private val orderRenameMenu: OrderRenameMenu? = null,   // nullable for backward compat
) : Listener {
```

**b. Add rename button in `open()`:**

After setting `sizeSlot` item and before `upgradeSlot`:

```kotlin
val isOwner = order.ownerUuid == player.uniqueId
if (isOwner) {
    inv.setItem(
        renameSlot,
        GuiUtils.namedItem(
            "§eПереименовать ордер",
            Material.ANVIL,
            "§7Изменить название ордера",
            "§7Текущее: §f${order.name}",
        ),
    )
} else {
    inv.setItem(
        renameSlot,
        GuiUtils.namedItem(
            "§7Переименовать ордер",
            Material.ANVIL,
            "§cТолько лидер может менять название ордера",   // [AC-11, AC-12]
        ),
    )
}
```

**c. Add `renameSlot` case to `onClick()`:**

```kotlin
renameSlot -> {
    val orm = orderRenameMenu ?: return
    val order = getOrderByOwnerUseCase(player.uniqueId) ?: return
    // Permission re-check: only the owner can open rename
    if (order.ownerUuid != player.uniqueId) {
        player.sendActionBar(Component.text("§cТолько лидер может менять название ордера"))
        return
    }
    player.closeInventory()
    orm.open(player, order)   // [AC-03, CC-06]
}
```

**Note:** The `onClick` title filter `title.contains("Ордер №")` already gates all cases — no change to the filter needed.

### 2. Main plugin class

Locate the main plugin class (the one extending `JavaPlugin` or `KotlinPlugin`) and find where `Bukkit.getPluginManager().registerEvents(...)` calls are made.

**a. Create `OrderRenameMenu` instance:**
```kotlin
val renameOrderUseCase = RenameOrderUseCaseImpl(orderRepository = orderRepositoryImpl)
val getOrderByOwnerUseCase = GetOrderByOwnerUseCaseImpl(...)  // already exists
val orderRenameMenu = OrderRenameMenu(
    renameOrderUseCase = renameOrderUseCase,
    getOrderByOwnerUseCase = getOrderByOwnerUseCase,
    plugin = this,
)
```

**b. Register listener:**
```kotlin
server.pluginManager.registerEvents(orderRenameMenu, this)
```

**c. Inject into `OrderMenu`:**
Locate where `OrderMenu(...)` is constructed. Pass `orderRenameMenu = orderRenameMenu` as a named argument.

## Tests to write (failing first)

- `TC-13` (AC-04): ArmorStand displays order name after activation
- `TC-14` (AC-05): Menu shows order name, not player ID
- `TC-15` (AC-11): Non-leader sees disabled rename button with tooltip
- `TC-16` (AC-12): Non-member sees disabled rename button
- `TC-02` (AC-02): Name shown in menu as plain text without prefix/suffix

## Integration smoke-test (manual TC-50)

1. Create order → check default name = sanitized nickname
2. Open Order menu → see rename button (ANVIL) at slot 13
3. Click rename → Anvil GUI opens with current name pre-filled
4. Type "NewGuild" → confirm → name updated in menu + ArmorStand
5. Log out and back in → name persists

## Definition of done

- [ ] `./gradlew compileKotlin` — no errors
- [ ] `./gradlew :[module]:test` — all tests pass
- [ ] `./gradlew detekt ktlintCheck` — clean
- [ ] `OrderRenameMenu` registered as Bukkit listener
- [ ] Rename button appears in OrderMenu at slot 13 for owners (enabled) and non-owners (disabled with tooltip)
- [ ] No TODO/FIXME in production code
