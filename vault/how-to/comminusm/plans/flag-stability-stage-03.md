---
genre: how-to
module: comminusm
title: "Stage 03 — Protection Events: BlockFromTo, Piston, EntityChange + Extend Existing"
topic: flag-stability
stage: 3
status: Pending
date: 2026-05-05
---

# Stage 03 — Protection Events

**Goal:** Block every game mechanic that can destroy or move a flag's support block or banner block. Extend existing `BlockListener` and `ExplosionListener`. Add new `FlagProtectionListener` for fluid flow, pistons, and falling blocks.

**Spec refs:** FR-02 (protection table), AC-01..AC-06, AC-12, AC-13, AC-32  
**Depends on:** Stage 01 (FlagStabilityManager + cache)

---

## Tasks

### 3.1 — FlagProtectionListener (new)

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/FlagProtectionListener.kt`

Register with `@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)`.  
Use `manager.isFlagPosition(block)` for all checks — cache-first, PDC fallback.

#### BlockFromToEvent — fluid flow (AC-03, AC-06)

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onFluidFlow(event: BlockFromToEvent) {
    val target = event.toBlock
    if (manager.isFlagPosition(target)) {
        event.isCancelled = true
    }
}
```

#### BlockPistonExtendEvent (AC-04, AC-06)

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onPistonExtend(event: BlockPistonExtendEvent) {
    if (event.blocks.any { manager.isFlagPosition(it) }) {
        event.isCancelled = true
    }
}
```

#### BlockPistonRetractEvent (AC-04, AC-06)

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onPistonRetract(event: BlockPistonRetractEvent) {
    if (event.blocks.any { manager.isFlagPosition(it) }) {
        event.isCancelled = true
    }
}
```

#### EntityChangeBlockEvent — falling blocks (sand, gravel) (FR-02)

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onFallingBlock(event: EntityChangeBlockEvent) {
    if (event.entity is FallingBlock && manager.isFlagPosition(event.block)) {
        event.isCancelled = true
    }
}
```

---

### 3.2 — Extend BlockListener (AC-01, AC-05, AC-13, AC-32)

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt`

Existing code already cancels `BlockBreakEvent` for banner blocks. Extend to also protect the **support block**:

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onBlockBreak(event: BlockBreakEvent) {
    val block = event.block

    // Existing banner protection (WHITE_BANNER / RED_BANNER) — keep as-is

    // NEW: support block protection
    if (manager.isFlagPosition(block)) {
        event.isCancelled = true
        event.isDropItems = false
        // AC-13: also cancels in creative mode
        event.player.sendMessage(
            if (isOwnFlag(event.player, block)) "Удалите приват через меню (ПКМ по флагу)."
            else "Это чужой приват."
        )
    }
}
```

Where `isOwnFlag()` checks the PDC `comminusm:flag/{id}` key to find the owner UUID and compares with `event.player.uniqueId`.

Also add banner protection to creative mode (AC-32) — the existing `BlockBreakEvent` cancel must NOT be skipped for creative players:

```kotlin
// Ensure creative mode players are NOT exempt
if (event.block.type == Material.WHITE_BANNER || event.block.type == Material.RED_BANNER) {
    if (manager.isFlagPosition(event.block)) {
        event.isCancelled = true
        event.isDropItems = false
    }
}
```

---

### 3.3 — Extend ExplosionListener (AC-02, AC-06, AC-15)

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/ExplosionListener.kt`

Existing code already filters banners from explosion block lists. Extend to also filter the **support block**:

```kotlin
@EventHandler
fun onEntityExplode(event: EntityExplodeEvent) {
    event.blockList().removeIf { manager.isFlagPosition(it) }
}

@EventHandler
fun onBlockExplode(event: BlockExplodeEvent) {
    event.blockList().removeIf { manager.isFlagPosition(it) }
}
```

This covers Wither + Creeper + TNT for both support block and banner block (AC-15).

---

### 3.4 — Ensure no item drop (AC-12, FR-03)

In every cancel of a `BlockBreakEvent` or `BlockExplodeEvent` affecting a flag block:
- Set `event.isDropItems = false` (where applicable)
- For explosions: block is removed from list → it simply won't be destroyed, no drop possible

---

## Tests for this Stage

- Fluid flow onto support block → cancelled (AC-03)
- Fluid flow onto banner block → cancelled
- Piston pushes support block → cancelled (AC-04)
- Piston pushes banner block → cancelled
- Creeper explosion → support block and banner both survive (AC-02)
- TNT explosion → same
- Wither explosion on OBSIDIAN support → both survive (AC-15)
- Creative player breaks support block → cancelled, no drop (AC-13)
- Creative player breaks banner block → cancelled, no drop (AC-32)
- Survival player breaks support block → message sent, no drop (AC-01)
- Falling gravel lands on support block → cancelled

**Relevant TCs:** TC-09..TC-17, TC-26..TC-28, TC-31..TC-32, TC-53

---

## Completion Criteria

- [ ] Support block and banner block survive all listed destruction paths
- [ ] No item drop (WHITE_BANNER or RED_BANNER) in any scenario
- [ ] `./gradlew compileKotlin` and `detekt ktlintCheck` pass
