# Bug Fix Report: Stale Order Flag Blocks New Order Creation

**Date:** 06.05.2026
**Author:** BugFixer Agent
**Status:** Fixed

---

## Bug Description

After deleting an Order through the deletion confirmation GUI, the player cannot create a new Order via `/party`. Instead of getting the "Партия выделила вам жилплощадь!" message, the player is told "У вас уже есть флаг Ордера, товарищ! Установите его в мире." — because `hasOrderFlagInInventory()` returns true.

## Root Cause

**Primary cause:** `FlagDeletionConfirmListener.onClick` slot 2 (lines 40-51) created and **dropped** a `WHITE_BANNER` ItemStack with the deleted Order's ID (`"§aФлаг Ордера №${order.id}"`). This flag referenced a now-deleted order — completely useless. The player auto-picked it up, so `FlagItemProtectionListener.hasOrderFlagInInventory()` returned `true`, blocking `PartyMenu.orderSlot` from creating a new order.

**Secondary guard:** Even without the primary fix, a player could have a stale flag from a deleted order (from a previous session or race condition). The existing code had no fallback — it simply blocked creation.

```
Call chain:
FlagDeletionConfirmListener.onClick(slot=2)
  → orderService.deleteByOwner(uuid)
  → world.dropItemNaturally(WHITE_BANNER with "Флаг Ордера №{deleted_id}")
  → Player auto-picks up
  → PartyMenu.onClick(orderSlot)
    → FlagItemProtectionListener.hasOrderFlagInInventory(player) = true
    → "У вас уже есть флаг Ордера, товарищ!" — blocked
```

## Fix Applied

**Fix 1 — `FlagDeletionConfirmListener.kt`:** Stopped dropping the obsolete flag. When an Order is deleted, the flag is gone. The player can always get a new one through `/party`. Removed lines 39-51 (ItemStack creation + `world.dropItemNaturally`). Changed success message from "§c☭ Ордер аннулирован. Флаг удалён." to "§c☭ Ордер аннулирован."

**Fix 2 — `PartyMenu.kt`:** Instead of blocking when `hasOrderFlagInInventory()` is true, call `FlagItemProtectionListener.removeAllOrderFlags(player)` to clear any stale flags, then continue creating the new order.

**Fix 3 — `FlagItemProtectionListener.kt`:** Added `removeAllOrderFlags(player: Player)` companion method that iterates inventory slots and clears any WHITE_BANNER items with "Флаг Ордера" in the display name.

### Files Changed

| File | Change |
|------|--------|
| `src/main/kotlin/ru/kyamshanov/comminusm/listener/FlagDeletionConfirmListener.kt` | Removed obsolete flag drop logic; cleaned unused `Location` import; shortened success message |
| `src/main/kotlin/ru/kyamshanov/comminusm/gui/PartyMenu.kt` | Replaced blocking `return` with `removeAllOrderFlags()` call |
| `src/main/kotlin/ru/kyamshanov/comminusm/listener/FlagItemProtectionListener.kt` | Added `removeAllOrderFlags()` companion method |

## Verification

- [x] `./gradlew compileKotlin` — BUILD SUCCESSFUL
- [x] `./gradlew test` — BUILD SUCCESSFUL (all tests pass)
- [x] No new lint warnings

## Lessons Learned

- Dropping items with IDs of deleted records creates stale references that can block future operations.
- When a deletion drops items as a "courtesy," consider whether those items still have meaning. A flag for a deleted Order is meaningless.
- Defense in depth: even after fixing the root cause, the PartyMenu fallback handles the edge case where a stale flag already exists in inventory from a prior session.
- Same pattern should be checked for Front flags (TC-User-01 already fixed; Front deletion may have the same issue — though it doesn't block creation the same way).

## Retrospective

**Root cause:** Listener creates and drops an item referencing a deleted DB record — stale item gets auto-picked up, inventory check blocks future operations.
**Category:** Guideline gap

### Actions

| # | Action | Status | File |
|---|--------|--------|------|
| 1 | Add Rule 4 to flag-lifecycle guideline | ✅ Done | `vault/guidelines/comminusm/flag-lifecycle.md` |
| 2 | Add safety net: `removeAllOrderFlags()` | ✅ Done | FlagItemProtectionListener.kt |

### Lessons

- "Drop item on delete" patterns are anti-patterns when the item references the now-deleted record.
- Always add a cleanup fallback for stale items — inventory-based presence checks are fragile without it.
