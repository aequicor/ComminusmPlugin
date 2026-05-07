# BUG-TC155: OrderMembersMenu allows item dragging despite read-only intent

## Symptom

Players can drag items from their inventory into the OrderMembersMenu despite it being intended as read-only. The menu is supposed to prevent all interaction:
- Buttons don't respond to clicks
- Items CAN be dragged/moved into the menu inventory
- Menu should be completely non-interactive

## Reproduction

1. Open order menu
2. Click "Participants" button to open OrderMembersMenu
3. Attempt to drag items from player inventory into the menu inventory
4. **Actual:** Items CAN be dragged/moved
5. **Expected:** Menu should be completely non-interactive - no item movement allowed

Failing test: `src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMembersMenuTest.kt::TC-155 onInventoryDrag method is missing - BUG - inventory drag events NOT cancelled()`

## Root cause

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/gui/OrderMembersMenu.kt`  
**Line:** Missing handler (should exist after line 119)

### Analysis

The OrderMembersMenu class handles `InventoryClickEvent` via the `onInventoryClick()` method at line 95-119:

```kotlin
@EventHandler
fun onInventoryClick(event: InventoryClickEvent) {
    val title = event.view.title().toString()
    if (!title.startsWith("§8Участники ордера")) return
    
    // TC-155: Cancel ALL clicks in the menu to prevent item dragging
    event.isCancelled = true
    // ... button handling code ...
}
```

However, this only handles single-click interactions. In Bukkit, there are **two separate events** for inventory interaction:

1. **InventoryClickEvent** - fires on single clicks (handled)
2. **InventoryDragEvent** - fires when players drag items across multiple slots or use shift+click to move items (NOT handled)

**Evidence:**

- Grep search shows NO `InventoryDragEvent` handling anywhere in the codebase
- OrderMembersMenu has no `onInventoryDrag()` method
- All other GUI menus (CommuneOrderMenu, CommunePartyMenu, etc.) also lack this handler
- The failing test confirms the missing method

### Why this causes the bug

When a player drags items in Bukkit:
- Single-click drag: triggers `InventoryClickEvent` (cancelled by current code)
- Multi-slot drag (shift+click, mouse drag): triggers `InventoryDragEvent` (NOT cancelled, no handler)

Without cancelling `InventoryDragEvent`, the second type of drag is allowed, letting items pass through the read-only menu.

## Suspect commit

Commit 4f328f7 ("fix: cancel all inventory clicks in GUI menus (TC-155, DEF-04)") attempted to fix this by cancelling all `InventoryClickEvent` instances. However, the fix was incomplete because it did not account for the separate `InventoryDragEvent`.

## Recommended fix approach

1. **Add import** for `InventoryDragEvent` in OrderMembersMenu.kt
2. **Add event handler method** annotated with `@EventHandler` that:
   - Checks if the event's inventory view title matches the menu title pattern
   - Returns early if not a match
   - Cancels the event: `event.isCancelled = true`
3. **Apply same fix to other GUI menus** (CommuneOrderMenu, CommunePartyMenu, AdminMenu, FrontMenu, etc.) to prevent drag interactions in all read-only menus
4. **Write integration test** that simulates both `InventoryClickEvent` and `InventoryDragEvent` to verify both are cancelled

## Test evidence

Failing test demonstrates the bug:
```kotlin
@Test
fun `TC-155 onInventoryDrag method is missing - BUG - inventory drag events NOT cancelled`() {
    val hasOnInventoryDragMethod = menu::class.java.methods
        .any { method -> 
            method.name == "onInventoryDrag" &&
            method.parameterCount == 1
        }
    
    // FAILS: method doesn't exist
    assert(hasOnInventoryDragMethod) { 
        "BUG-TC-155: OrderMembersMenu is missing onInventoryDrag() method to handle " +
        "InventoryDragEvent. Without it, players can drag items into the read-only menu."
    }
}
```

Test file: `src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMembersMenuTest.kt`
