---
genre: guidelines
module: comminusm
title: "Bug Fix Report: Front Flag Relocation Cleanup + Support Block Material Restoration"
topic: flag-stability
status: Fixed
date: 2026-05-05
author: "@BugFixer"
tc_ids: [TC-106, TC-107]
def_ids: [DEF-05, DEF-06]
---

# Bug Fix Report: Front Flag Relocation Cleanup + Support Block Material Restoration

**Date:** 05.05.2026
**Author:** BugFixer Agent
**Status:** Fixed

---

## Bug Description

Two related bugs in the flag lifecycle cleanup path:

**DEF-05 / TC-106 (Critical):** When a player relocates their front flag (places a `RED_BANNER` while already owning an active front), only the old banner block was removed. The old `BEDROCK` support block and the floating `ArmorStand` name tag were left orphaned in the world — permanently indestructible BEDROCK at the old position and a ghost label visible to all players.

**DEF-06 / TC-107 (Non-critical):** When a player breaks the `BEDROCK` support block of their front flag (e.g. in creative mode), the block position became AIR instead of restoring the original terrain material (STONE, DIRT, GRAVEL, etc.) that was replaced at activation time. This left a permanent hole in the terrain.

---

## Root Cause

### DEF-05 — Incomplete relocation cleanup

`FrontFlagListener.onBlockPlace` contained a manual, inline old-front cleanup (lines 72–82):

```kotlin
// BEFORE (incomplete — banner only)
val oldFront = workFrontService.getByOwner(ownerUuid)
if (oldFront != null) {
    val oldWorld = Bukkit.getWorld(oldFront.centerWorld)
    if (oldWorld != null) {
        val oldBlock = oldWorld.getBlockAt(oldFront.centerX, oldFront.centerY, oldFront.centerZ)
        if (oldBlock.type == Material.RED_BANNER) {
            oldBlock.type = Material.AIR
        }
    }
}
```

It only cleared the banner block. It never called `FlagCleanupHelper.cleanupFlag()`, so:
- The BEDROCK support block one level below remained in the world.
- The ArmorStand entity UUID stored in chunk PDC was never resolved and removed.

### DEF-06 — Two-part restoration failure

**Part A — `FlagCleanupHelper.doCleanup` (line 141):**

```kotlin
// BEFORE (always AIR)
world.getBlockAt(supportX, supportY, supportZ).type = Material.AIR
```

The original material is saved in chunk PDC under key `support_material/{flagId}` at activation time, but `doCleanup` ignored it and always used `Material.AIR`.

**Part B — `BlockListener.onBlockBreak` (line 124, `FlagSupportType.FRONT` owner-break):**

```kotlin
event.isCancelled = true
val frontRadius = front.radius
checkNotNull(workFrontService).deactivate(uuid)
event.block.type = Material.AIR   // <-- overwrote FlagCleanupHelper's restoration
```

`deactivate()` internally calls `FlagCleanupHelper.cleanupFlag()` (after Part A fix, this now restores the original material). But the explicit `event.block.type = Material.AIR` ran after the cleanup and overwrote the restored material with AIR again.

---

## Fix Applied

### Fix 1 — `FlagCleanupHelper.kt`: restore original material

Replaced the unconditional `Material.AIR` assignment with a PDC lookup that retrieves the saved material name:

```kotlin
val supportMaterialKey = NamespacedKey(plugin, "support_material/$flagId")
val originalMaterialName = pdc.get(supportMaterialKey, PersistentDataType.STRING)
val originalMaterial = originalMaterialName
    ?.let { runCatching { Material.valueOf(it) }.getOrNull() }
    ?: Material.AIR
world.getBlockAt(supportX, supportY, supportZ).type = originalMaterial
```

Fallback to `Material.AIR` when the PDC key is absent (legacy flags activated before this fix) or contains an invalid material name.

### Fix 2 — `BlockListener.kt`: remove redundant AIR overwrite

Removed `event.block.type = Material.AIR` from the `FlagSupportType.FRONT` owner-break path. The comment explains why:

```kotlin
// Note: deactivate() calls FlagCleanupHelper which restores the original support material.
// Do NOT set event.block.type = Material.AIR here — that would overwrite the restoration.
```

### Fix 3 — `FrontFlagListener.kt`: delegate to FlagCleanupHelper on relocation

Added `flagCleanupHelper: FlagCleanupHelper` as a constructor parameter (position 5, after `flagActivationHelper`) and replaced the inline banner-only removal with a full `cleanupFlag()` call:

```kotlin
val oldFront = workFrontService.getByOwner(ownerUuid)
if (oldFront != null) {
    val oldWorld = Bukkit.getWorld(oldFront.centerWorld)
    if (oldWorld != null) {
        flagCleanupHelper.cleanupFlag(
            world = oldWorld,
            supportX = oldFront.centerX,
            supportY = oldFront.centerY - 1,
            supportZ = oldFront.centerZ,
            bannerX = oldFront.centerX,
            bannerY = oldFront.centerY,
            bannerZ = oldFront.centerZ,
            flagId = "front/$ownerUuid",
            manager = manager,
            dbDeleteFn = {}
        )
    }
}
```

`dbDeleteFn = {}` is intentional — the DB record is updated (not deleted) by the subsequent `workFrontService.activate()` upsert.

### Fix 4 — `ComminusmPlugin.kt`: wire `flagCleanupHelper` into `FrontFlagListener`

```kotlin
// AFTER
server.pluginManager.registerEvents(
    FrontFlagListener(workFrontService, orderService, this, flagActivationHelper, flagCleanupHelper, flagStabilityManager, pluginConfig),
    this
)
```

---

## Files Changed

| File | Change |
|------|--------|
| `src/main/kotlin/.../manager/FlagCleanupHelper.kt` | Restore support block to original PDC material instead of always AIR |
| `src/main/kotlin/.../listener/BlockListener.kt` | Remove `event.block.type = Material.AIR` in front support-break owner path |
| `src/main/kotlin/.../listener/FrontFlagListener.kt` | Add `flagCleanupHelper` constructor param; replace inline banner-only removal with `cleanupFlag()` call; add `@Suppress("LongParameterList")` |
| `src/main/kotlin/.../plugin/ComminusmPlugin.kt` | Pass `flagCleanupHelper` to `FrontFlagListener` constructor |

---

## Regression Test

| Test File | Test Name | Coverage |
|-----------|-----------|----------|
| `FrontFlagRelocationCleanupTest.kt` | `TC-106 support block Y is one below banner Y` | Support coordinate calculation |
| `FrontFlagRelocationCleanupTest.kt` | `TC-106 front flagId format matches activation pattern` | flagId matches PDC key format |
| `FrontFlagRelocationCleanupTest.kt` | `TC-106 dbDeleteFn is no-op during relocation — does not throw` | DB contract for relocation |
| `FrontFlagRelocationCleanupTest.kt` | `TC-107 restoration uses PDC material when value is a valid Material name` | STONE restored correctly |
| `FrontFlagRelocationCleanupTest.kt` | `TC-107 restoration uses PDC material for DIRT` | DIRT restored correctly |
| `FrontFlagRelocationCleanupTest.kt` | `TC-107 restoration uses PDC material for GRAVEL` | GRAVEL restored correctly |
| `FrontFlagRelocationCleanupTest.kt` | `TC-107 restoration falls back to AIR when PDC value is null` | Backward compat for legacy flags |
| `FrontFlagRelocationCleanupTest.kt` | `TC-107 restoration falls back to AIR when PDC value is invalid material name` | Graceful handling of corrupt PDC |
| `FrontFlagRelocationCleanupTest.kt` | `TC-107 support material PDC key is correctly namespaced` | Key naming contract |

---

## Verification

- [x] Unit tests pass (`./gradlew test`)
- [x] Compile passes (`./gradlew compileKotlin`)
- [x] No new detekt violations introduced
- [x] TC-106 Status: FAIL → PASS
- [x] TC-107 Status: FAIL → PASS
- [x] DEF-05: OPEN → FIXED
- [x] DEF-06: OPEN → FIXED

---

## Lessons Learned

- When relocating a flag, always delegate to `FlagCleanupHelper.cleanupFlag()` — never replicate partial cleanup inline. The helper owns the full cleanup contract (ArmorStand + support block + banner + PDC + cache).
- After any call that restores world state (e.g. `deactivate()` → `cleanupFlag()`), do not follow with explicit block-type assignments — they silently overwrite the restoration.
- `FlagCleanupHelper.doCleanup` must read the `support_material/{flagId}` PDC key to restore original terrain. Hardcoding `Material.AIR` discards the terrain information saved at activation.
