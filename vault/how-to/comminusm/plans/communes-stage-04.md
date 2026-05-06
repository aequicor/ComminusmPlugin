---
genre: how-to
module: comminusm
title: Stage 04 — GUI Menus
topic: communes
date: 2026-05-06
author: "@Main"
related:
  - vault/reference/comminusm/spec/communes.md
  - vault/concepts/comminusm/plans/communes-plan.md
---

# Stage 04 — GUI Menus

**Objective:** Implement all inventory GUIs for commune management (decorators + new menus).  
**Duration:** ~1-2 days  
**Dependencies:** Stage 01 & 02

---

## Key Pattern: Decorator (open-closed)

**Constraint:** `PartyMenu.kt` and `OrderMenu.kt` are **not modified**.

**Solution:** Use **Decorator pattern** to wrap existing menus:
- `CommunePartyMenu` wraps `PartyMenu` → renders all original slots + new "Коммуна" button
- `CommuneOrderMenu` wraps `OrderMenu` → renders all original slots + new "Участники" button

**DI wiring:**
```kotlin
// In ComminusmPlugin.onEnable():
// Instead of: val partyMenu = PartyMenu(...)
// Do:         val partyMenu = CommunePartyMenu(PartyMenu(...), ...)

// Same for OrderMenu → CommuneOrderMenu
```

---

## Components to Implement

### 1. CommunePartyMenu

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/menu/CommunePartyMenu.kt`

**Responsibility:** Decorator wrapping `PartyMenu`. Adds "Коммуна" button (slot TBD, typically slot 13 = center-top).

**Implementation:**

```kotlin
class CommunePartyMenu(
    private val delegate: PartyMenu,
    private val communeService: CommuneService,
    private val orderService: OrderService,
    private val orderMembershipService: OrderMembershipService,
) : Listener {
    
    fun open(player: Player) {
        // Delegate: open original PartyMenu
        delegate.open(player)
        
        // Add "Коммуна" button
        val playerOrders = orderMembershipService.listNativeOrders(player.uniqueId)
        val isLeader = playerOrders.any { orderService.isLeader(player.uniqueId) }
        
        // Find inventory from delegate's internal state (or re-create)
        val inv = getInventory(player) // see implementation note below
        
        if (isLeader) {
            inv.setItem(
                13, // center-top
                GuiUtils.namedItem(
                    "§aКоммуна",
                    Material.PAPER,
                    "§7Управление альянсом ордеров",
                    "§8Нажми чтобы открыть"
                )
            )
        } else {
            inv.setItem(
                13,
                GuiUtils.namedItem(
                    "§7Коммуна",
                    Material.PAPER,
                    "§7Коммуну создаёт лидер ордера",
                    "§8(Отключено)",
                )
            )
        }
        
        player.openInventory(inv)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != "§8Партия") return // Check PartyMenu title
        
        if (event.slot == 13) {
            val player = event.whoClicked as Player
            val isLeader = isOrderLeader(player)
            
            if (!isLeader) {
                event.isCancelled = true
                player.sendMessage("§cТолько лидер ордера может управлять коммуной")
                return
            }
            
            event.isCancelled = true
            // Open CommuneMenu instead
            openCommuneMenu(player)
        } else {
            // Delegate other clicks to original PartyMenu
            delegate.onInventoryClick(event)
        }
    }
    
    private fun openCommuneMenu(player: Player) {
        // TBD in CommuneMenu implementation
    }
    
    private fun getInventory(player: Player): Inventory {
        // Get inventory from player's open inventory or re-create
        return player.openInventory.topInventory
            ?: Bukkit.createInventory(null, 45, Component.text("§8Партия"))
    }
}
```

**Implementation note:** Getting the inventory from `PartyMenu` requires either:
1. Storing inventory reference in delegate (modify PartyMenu to expose it) — **violates open-closed**
2. Re-creating inventory from scratch (duplicate PartyMenu logic) — **violates open-closed**
3. Using ClickEvent to capture and re-display — **works**
4. Registering as Listener on separate priority to intercept and re-render — **works**

**Recommended:** Use priority-based listener pattern:
```kotlin
@EventHandler(priority = EventPriority.LOWEST) // Run after PartyMenu
fun onPartyMenuClick(event: InventoryClickEvent) {
    if (event.view.title != "§8Партия") return
    
    // At this point, PartyMenu has rendered. Add our button.
    val inv = event.view.topInventory
    // Add "Коммуна" button to slot 13
    // Re-show inventory to player
}
```

---

### 2. CommuneOrderMenu

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/menu/CommuneOrderMenu.kt`

**Responsibility:** Decorator wrapping `OrderMenu`. Adds "Участники" button (spec §5.8).

**Implementation:** Same pattern as CommunePartyMenu, but:
- Slot: typically slot 22 or 24 (center area)
- Button title: "§6Участники"
- Material: PAPER
- Visible only to order leaders and native members (AC-60)

```kotlin
class CommuneOrderMenu(
    private val delegate: OrderMenu,
    private val orderService: OrderService,
    private val orderMembershipService: OrderMembershipService,
) : Listener {
    
    @EventHandler(priority = EventPriority.LOWEST)
    fun onOrderMenuClick(event: InventoryClickEvent) {
        if (!isOrderMenuTitle(event.view.title)) return
        
        if (event.slot == 22) {
            val player = event.whoClicked as Player
            val playerOrders = orderMembershipService.listNativeOrders(player.uniqueId)
            
            if (playerOrders.isEmpty()) {
                event.isCancelled = true
                player.sendMessage("§cВы не член этого ордера")
                return
            }
            
            event.isCancelled = true
            // Open OrderMembersMenu
            openOrderMembersMenu(player, playerOrders.first())
        } else {
            // Delegate all other clicks to OrderMenu
            delegate.onInventoryClick(event)
        }
    }
}
```

---

### 3. CommuneMenu

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/menu/CommuneMenu.kt`

**Responsibility:** Main GUI for commune management. Displays member orders, management buttons, incoming invitations.

**Layout (spec §5):**

```
Slot layout (45-slot inventory):
╔════════════════════════════════╗
║ 0│  1│  2│  3│  4│  5│  6│  7│ 8║  <- border
║  ├──────────────────────────────┤
║9 │ 10│ 11│ 12│ 13│ 14│ 15│ 16│17║
║  ├──────────────────────────────┤
║18│ 19│ 20│ 21│ 22│ 23│ 24│ 25│26║  <- main content area
║  ├──────────────────────────────┤
║27│ 28│ 29│ 30│ 31│ 32│ 33│ 34│35║
║  ├──────────────────────────────┤
║36│ 37│ 38│ 39│ 40│ 41│ 42│ 43│44║  <- footer (back button at 39)
╚════════════════════════════════╝

For CommuneMenu:
- Border: slots 0-8, 9, 17, 18, 26, 27, 35, 36, 44 (BLACK_STAINED_GLASS_PANE)
- Content area: slots 10-16, 19-25, 28-34, 37-43
- Header (10-12): Info about commune
- Orders list (19-25, 28-34): Each order as 1 slot
- Buttons (37-38, 40-41): "Invite", "Leave" (if leader)
- Incoming invitations block (10-16): If any pending (AC-30)
- Back button: slot 39
```

**Implementation:**

```kotlin
class CommuneMenu(
    private val communeService: CommuneService,
    private val orderService: OrderService,
    private val communeInvitationService: CommuneInvitationService,
    private val orderMembershipService: OrderMembershipService,
) : Listener {
    
    fun open(player: Player, communeId: UUID) {
        val commune = communeService.getCommuneById(communeId)
            ?: return player.sendMessage("§cКоммуна не найдена")
        
        val isLeader = isOrderLeader(player)
        
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Коммуна"))
        
        // Fill border
        GuiUtils.fillBorder(inv)
        
        // Header: commune info
        inv.setItem(
            10,
            GuiUtils.namedItem(
                "§aКоммуна",
                Material.PAPER,
                "§7Участники: §e${commune.orderIds.size}",
                "§7ID: §e${commune.id}",
            )
        )
        
        // Orders list
        val orders = commune.orderIds.mapNotNull { orderService.getOrderById(it) }
        var slot = 19
        for (order in orders) {
            if (slot > 34) {
                // Paging needed (show Prev/Next buttons)
                break
            }
            
            inv.setItem(
                slot,
                GuiUtils.namedItem(
                    "§6Ордер №${order.id}",
                    Material.WHITE_BANNER,
                    "§7Владелец: §e${Bukkit.getOfflinePlayer(order.ownerUuid).name}",
                    "§7Уровень: §e${order.level}",
                )
            )
            slot++
        }
        
        // Incoming invitations block (AC-30)
        val invitations = communeInvitationService.getInvitationsFor(???) // TODO: impl
        if (invitations.isNotEmpty() && isLeader) {
            inv.setItem(
                11,
                GuiUtils.namedItem(
                    "§cВходящее приглашение",
                    Material.REDSTONE,
                    "§7Количество: §e${invitations.size}",
                    "§7Нажми для управления",
                )
            )
        }
        
        // Management buttons (only for leader)
        if (isLeader) {
            inv.setItem(
                37,
                GuiUtils.namedItem(
                    "§ePригласить ордер",
                    Material.NETHER_STAR,
                    "§7Отправить приглашение союзнику",
                )
            )
            inv.setItem(
                40,
                GuiUtils.namedItem(
                    "§cПокинуть коммуну",
                    Material.RED_DYE,
                    "§7Выйти из альянса",
                    "§8Все cross-order права будут отозваны",
                )
            )
        }
        
        // Back button
        inv.setItem(39, GuiUtils.namedItem("§cНазад", Material.BARRIER))
        
        player.openInventory(inv)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != "§8Коммуна") return
        
        when (event.slot) {
            39 -> {
                event.isCancelled = true
                // Re-open PartyMenu
                // TBD
            }
            37 -> {
                event.isCancelled = true
                // Show invite order selection menu
                openInviteOrderMenu(event.whoClicked as Player)
            }
            40 -> {
                event.isCancelled = true
                // Show leave confirmation screen
                openLeaveConfirmation(event.whoClicked as Player)
            }
        }
    }
}
```

---

### 4. OrderMembersMenu

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/menu/OrderMembersMenu.kt`

**Responsibility:** View/manage native and cross-order members of an order (spec §5.6).

**Access paths:**
1. From `CommuneMenu` → "Участники ордера" button
2. From `CommuneOrderMenu` (decorator) → "Участники" button

**Implementation:**

```kotlin
class OrderMembersMenu(
    private val orderMembershipService: OrderMembershipService,
    private val communeService: CommuneService,
    private val orderService: OrderService,
) : Listener {
    
    fun open(player: Player, orderId: Long) {
        val isLeader = isOrderLeader(player) // for the order, not player
        
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Участники ордера №$orderId"))
        GuiUtils.fillBorder(inv)
        
        // Get members
        val members = orderMembershipService.getMembersOfOrder(orderId)
        
        // Header: member count
        inv.setItem(10, GuiUtils.namedItem("§6Участники", Material.PAPER, "§7Количество: §e${members.size}"))
        
        // List members
        var slot = 19
        for (member in members) {
            if (slot > 34) break
            
            val grantedViaText = when (member.grantedVia) {
                "native" -> "§aНативный"
                "commune" -> "§eКоммунный"
                else -> "§7Неизвестно"
            }
            
            inv.setItem(
                slot,
                GuiUtils.namedItem(
                    "§7${Bukkit.getOfflinePlayer(member.playerUUID).name}",
                    Material.PLAYER_HEAD,
                    grantedViaText,
                    if (isLeader) "§8Нажми для исключения" else "§8(Только для лидера)"
                )
            )
            
            slot++
        }
        
        // Invite button (only for leader)
        if (isLeader) {
            inv.setItem(
                37,
                GuiUtils.namedItem(
                    "§eПригласить участника",
                    Material.NETHER_STAR,
                    "§7Пригласить игрока в этот ордер",
                )
            )
        }
        
        // Back
        inv.setItem(39, GuiUtils.namedItem("§cНазад", Material.BARRIER))
        
        player.openInventory(inv)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!event.view.title.startsWith("§8Участники ордера")) return
        
        when {
            event.slot == 39 -> {
                event.isCancelled = true
                // Back to previous menu
            }
            event.slot == 37 -> {
                event.isCancelled = true
                // Show invite player menu
            }
            event.slot in 19..34 -> {
                event.isCancelled = true
                // If leader: remove member
            }
        }
    }
}
```

---

## Supporting Components

### GuiUtils Extensions

Add utility methods to existing `GuiUtils`:
- `fillBorder(inv: Inventory)` — fill all border slots with BLACK_STAINED_GLASS_PANE
- `namedItem(name: String, material: Material, vararg lore: String)` — create item with name & lore

---

## Tests for Stage 04

### Unit Tests

- Menu creation: slots populated correctly, items correct
- Click handling: correct slot detection, event cancellation
- Decorator pattern: original menu still works, new buttons added
- Paging: list truncation when > 7 items

### Integration Tests

- Full workflow: party menu → commune menu → order members menu
- Leader vs non-leader: different buttons shown
- Incoming invitations block rendering

---

## DoD Checklist

- [ ] All menus compile
- [ ] `./gradlew compileKotlin` passes
- [ ] Decorator pattern works (PartyMenu.kt, OrderMenu.kt unmodified)
- [ ] GUI items render correctly
- [ ] Click handling works
- [ ] All tests pass
- [ ] Lint passes

---
