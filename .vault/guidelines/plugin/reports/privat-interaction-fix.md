# Bug Fix Report: Privat Interaction Fixes

**Date:** 04.05.2026
**Author:** BugFixer Agent
**Status:** Fixed

---

## Bug 1: Foreign Front Flag Breaking

### Bug Description
Player could break another player's RED_BANNER (Front flag) through two bypass paths:
1. Breaking the **support block** under a wall-mounted banner
2. Breaking **orphaned banner blocks** left behind after a front was moved

### Root Cause

**Path 1 — Support-block break bypass:**
In Minecraft, breaking the block a wall-mounted banner is attached to causes the banner to drop **without** firing a `BlockBreakEvent` for the banner itself. If Player B is standing in their own zone (Order or Front), and Player A's front banner is wall-mounted on a block inside B's zone, B can break the support block and destroy A's banner — the protection in `BlockListener.onBlockBreak` (lines 53-82) never triggers because the banner `BlockBreakEvent` is never emitted.

**Path 2 — Orphaned banners:**
- When Player A moves their front (places a new RED_BANNER), `FrontFlagListener.onBlockPlace` calls `workFrontService.activate()` which internally deletes the old DB record. The old RED_BANNER block **remains in the world** with no DB registration.
- When Player A breaks their own front, `onBlockBreak` cancels the event, calls `deactivate()`, but the banner block stays.
- In both cases, the foreign-front check (lines 72-81) passes through (no DB entry), and if another player's zone covers that location, the general zone check allows the break.

### Fix Applied

**1. Remove orphaned banner blocks from the world (`BlockListener.kt`):**
- When a player breaks their own front flag, set `event.block.type = Material.AIR` after deactivation to remove the orphaned banner.

**2. Remove old banner when moving front (`FrontFlagListener.kt`):**
- When a player places a new front flag (replacing an old one), destroy the old banner block before activating the new one by setting its type to `Material.AIR`.

**3. Prevent support-block break bypass (`BlockListener.kt`):**
- Added `isForeignFrontSupportBlock()` helper method that checks all 6 adjacent directions of the broken block for a RED_BANNER. If any adjacent RED_BANNER belongs to a registered front with a different owner UUID, the break is denied.
- Check runs at both "own order" and "own front" allow-gates in `onBlockBreak` — after confirming the player is in their own zone, we still verify they're not destroying the support block of someone else's front flag.

---

## Bug 2: Block Interaction Outside Zones

### Bug Description
Player could place blocks and interact with blocks (right-click on doors, chests, etc.) outside both their Order zone and their Front zone. The "inside own front" zone check existed in `onBlockBreak` but was missing from `onBlockPlace` and `onPlayerInteract`.

### Root Cause
Commit `e4f5a43` (previous privates fix) added the front zone check only to `onBlockBreak` (lines 100-104). The `onBlockPlace` (lines 116-152) and `onPlayerInteract` (lines 154-195) handlers had the same 4-zone structure but only 3 checks — missing step 3 "check inside own front". This meant a player with an active Front but no Order could break blocks in their front zone (thanks to `onBlockBreak`) but could NOT place blocks or interact with blocks there.

### Fix Applied
Added the missing "inside own front" zone check to both `onBlockPlace` and `onPlayerInteract` — matching the existing pattern from `onBlockBreak`:
```
// 3. Check: inside player's OWN front? → ALLOW
val myFront = workFrontService?.getByOwner(uuid)
if (myFront != null && myFront.centerWorld == world.name && isInsideFront(myFront, loc)) {
    return // allowed
}
```

---

## Files Changed

| File | Change |
|------|--------|
| `src/main/kotlin/.../listener/BlockListener.kt` | 1. Remove orphaned banner on own front break (line 61). 2. Added `isForeignFrontSupportBlock()` helper method (~lines 226-251). 3. Added support-block checks at both "own order" and "own front" allow-gates in `onBlockBreak` (lines 90-95, 110-115). 4. Added "inside own front" check to `onBlockPlace` (lines 147-151). 5. Added "inside own front" check to `onPlayerInteract` (lines 196-200). |
| `src/main/kotlin/.../listener/FrontFlagListener.kt` | Remove old banner block from world before activating new front (lines 60-66). |

---

## Verification

- [x] `./gradlew compileKotlin` — BUILD SUCCESSFUL
- [x] `./gradlew test` — BUILD SUCCESSFUL, all tests pass
- [x] No new detekt/ktlint warnings beyond pre-existing

---

## Lessons Learned

1. **Banner physics bypass:** Wall-mounted banner destruction via support block is a Minecraft engine behavior that skips the banner's `BlockBreakEvent`. Any block-protection system must check adjacent banners when the support block is broken.
2. **Orphaned world-state:** When a plugin de-registers a block from its database, it must also physically remove that block from the world. DB state and world state must always be consistent.
3. **Symmetric handlers:** All three interaction handlers (break, place, interact) should implement the same zone-check logic. Inconsistencies create confusing "half-protection" behavior.
4. **Safe-call patterns:** `workFrontService?.deactivate(uuid)` is correct even when the outer `if` has already verified `workFrontService` is non-null at the call site, because the field can be mutated by other threads. The warning from Kotlin compiler is a false positive in that context.
