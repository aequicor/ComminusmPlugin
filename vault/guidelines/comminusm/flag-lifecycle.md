---
module: comminusm
date: 2026-05-05
status: Active
---

# Flag Lifecycle Guideline — Orders and Fronts

---

## Rule 1: Block lifecycle belongs in the Service, not listeners or menus

When a flag (Order or Front) is activated, deactivated, moved, or deleted, the associated banner block (WHITE_BANNER or RED_BANNER) must be created or destroyed by the **service**, not by individual listeners or menu handlers. Every code path that modifies flag state must also manage the corresponding world block.

**Violations found (fixed in DEF-User-01, DEF-User-04):**
- `WorkFrontService.deactivate()` deleted the DB record but left the RED_BANNER in the world → orphaned flag.
- `FlagDeletionConfirmListener` dropped WHITE_BANNER on the ground after deleting the order → stale flag blocked recreation.

**Pattern:**
```kotlin
fun deactivate(uuid: UUID) {
    val front = repository.findByOwner(uuid)  // capture BEFORE deleting
    repository.deleteByOwner(uuid)
    if (front != null && chunkCacheManager != null) {
        val world = Bukkit.getWorld(front.centerWorld)
        if (world != null) {
            // Break the banner block
            world.getBlockAt(front.centerX, front.centerY, front.centerZ).type = Material.AIR
            // Clean chunk cache
            val chunk = world.getChunkAt(front.centerX shr 4, front.centerZ shr 4)
            chunkCacheManager.removeFrontChunk(chunk)
        }
    }
}
```

---

## Rule 2: Detect flags by plain text, not styled components

Kyori Adventure's `Component.contains(Component)` is **style-sensitive**. A `Component.text("Флаг Ордера", NamedTextColor.GOLD)` does NOT contain a plain `Component.text("Флаг Ордера")` — styles must match. Always use `PlainTextComponentSerializer` for semantic name matching.

**Violations found (fixed in DEF-User-03):**
- `BlockListener.resolveOrderFlag()` and `resolveFrontFlag()` used `customName.contains(Component.text("..."))` — styles mismatched and detection silently returned null.

**Pattern:**
```kotlin
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

fun matchesFlagName(customName: Component, keyword: String): Boolean {
    return PlainTextComponentSerializer.plainText().serialize(customName).contains(keyword)
}
```

---

## Rule 3: Flags should go to inventory, not the ground

When a player deactivates their own flag (break or support-block), the flag item should go to their inventory. Only fall back to dropping on the ground when inventory is full — and always remind the player they can get a new flag anytime via `/party`.

**Violations found (fixed in TC-User-01 UX refinement):**
- `BlockListener.onBlockBreak` used `world.dropItemNaturally()` for RED_BANNER on direct break and support-block break.

**Pattern:**
```kotlin
private fun giveOrNotify(player: Player, item: ItemStack, successMsg: String) {
    if (player.inventory.firstEmpty() == -1) {
        player.world.dropItemNaturally(player.location, item)
        player.sendMessage(Component.text(
            "⚠ Ваш инвентарь переполнен! Флаг выброшен на землю.\n" +
            "Вы всегда можете получить новый флаг через меню /party"
        ))
    } else {
        player.inventory.addItem(item)
        player.sendMessage(Component.text(successMsg))
    }
}
```

---

## Rule 4: Never drop items with IDs of deleted records

When deleting a database entity, do NOT drop an item that references the now-deleted ID. The item becomes a stale reference, gets auto-picked up, and blocks future operations that check for its presence in inventory.

**Violations found (fixed in DEF-User-04):**
- `FlagDeletionConfirmListener` dropped `"Флаг Ордера №${order.id}"` after `orderService.deleteByOwner(uuid)` — order deleted, flag useless, creation blocked.

**Defense in depth:** Add a safety net to remove stale flags from inventory during creation (see `FlagItemProtectionListener.removeAllOrderFlags`).

---

## Rule 5: Support blocks must be protected for BOTH flag types

Both `WHITE_BANNER` (order flags) and `RED_BANNER` (front flags) are attached to support blocks. When a support block is destroyed, vanilla Minecraft drops the banner as an item — without firing a `BlockBreakEvent` for the banner itself. Protection MUST check at the support-block level.

**Protection matrix:**

| Breaker | Order support (WHITE) | Front support (RED) |
|---------|----------------------|---------------------|
| Owner   | Show deletion confirmation GUI | Deactivate front, flag to inventory |
| Foreign | Deny with message | Deny with message |

Check MUST run before zone checks (centralized at top of `onBlockBreak`) because support block and breaker may be in different zones.

---

## Related

- DEF-User-01..04 (fix-tc-user-01-02)
- `vault/guidelines/comminusm/reports/fix-tc-user-01-02.md`
- `vault/guidelines/comminusm/reports/component-contains-style-mismatch.md`
- `vault/guidelines/comminusm/reports/stale-order-flag-blocks-creation.md`
