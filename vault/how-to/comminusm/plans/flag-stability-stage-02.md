---
genre: how-to
module: comminusm
title: "Stage 02 — Activation Flow: Support Block + ArmorStand + Rollback"
topic: flag-stability
stage: 2
status: Pending
date: 2026-05-05
---

# Stage 02 — Activation Flow

**Goal:** Rewrite `OrderFlagListener.onBlockPlace` and `FrontFlagListener.onBlockPlace` to place an indestructible support block, spawn an invisible ArmorStand with the owner's name, write PDC keys, and roll back cleanly on any failure.

**Spec refs:** Section 5 (Activation Flow), Section 10 (Concurrent Safety), AC-08, AC-09, AC-17, AC-20, AC-29, AC-30, AC-31, AC-37, AC-41, CC-01, CC-05, CC-06, CC-09

**Depends on:** Stage 01

---

## Tasks

### 2.1 — Shared activation helper: `FlagActivationHelper`

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/manager/FlagActivationHelper.kt`  
**New class.** Extracts the common activation logic used by both order and front listeners.

#### Pre-condition checks (all synchronous, main thread, before lock):

```kotlin
fun checkPreconditions(
    player: Player,
    bannerBlock: Block,
    config: PluginConfig,
    manager: FlagStabilityManager
): ActivationCheckResult {
    // 1a. Early exit: not a flag banner (handled by caller checking item meta)
    
    // 1b. World allowlist (AC-20, CC-06)
    if (bannerBlock.world.name !in config.flagAllowedWorlds) {
        return ActivationCheckResult.Denied("Флаги можно устанавливать только в Overworld.")
    }

    // 1c. Free air above (AC-09)
    val airRequired = if (bannerBlock.y >= 318) 1 else config.flagMinAirAbove
    val freeAbove = (1..airRequired).count { offset ->
        val mat = bannerBlock.world.getBlockAt(bannerBlock.x, bannerBlock.y + offset, bannerBlock.z).type
        mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR ||
        mat == Material.WATER || mat == Material.LAVA
    }
    if (freeAbove < airRequired) {
        return ActivationCheckResult.Denied("Недостаточно места над флагом.")
    }

    // 1d. Chunk flag limit (AC-22)
    val chunkX = bannerBlock.x shr 4; val chunkZ = bannerBlock.z shr 4
    val chunkKey = manager.chunkKey(bannerBlock.world.name, chunkX, chunkZ)
    // count flags in chunk PDC — if >= maxPerChunk, deny
    val count = bannerBlock.chunk.persistentDataContainer.keys
        .count { it.namespace == "comminusm" && it.key.startsWith("flag/") }
    if (count >= config.flagMaxPerChunk) {
        return ActivationCheckResult.Denied("Достигнут лимит флагов в этом чанке.")
    }

    return ActivationCheckResult.Ok(chunkKey)
}
```

#### Activation body (called after DB record confirmed, on main thread via runTask):

```kotlin
fun performActivation(
    bannerBlock: Block,
    supportBlock: Block,          // block directly below banner
    originalMaterial: Material,   // saved before replacement (AC-41)
    flagId: String,               // "order/{uuid}" or "front/{uuid}"
    ownerName: String,            // Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString() (CC-01)
    flagType: String,             // "Ордер" or "Трудовой Фронт"
    config: PluginConfig,
    manager: FlagStabilityManager,
    chunkKey: String,
    lock: ReentrantLock
) {
    // Step 4: Replace support block
    supportBlock.type = config.flagSupportBlockMaterial

    // Step 5: Write flag PDC + support_material PDC (within lock scope)
    val chunk = supportBlock.chunk
    val pdc = chunk.persistentDataContainer
    val flagKey = NamespacedKey(plugin, "flag/$flagId")
    val supportMatKey = NamespacedKey(plugin, "support_material/$flagId")
    pdc.set(flagKey, PersistentDataType.LONG_ARRAY,
        longArrayOf(bannerBlock.x.toLong(), bannerBlock.y.toLong(), bannerBlock.z.toLong()))
    pdc.set(supportMatKey, PersistentDataType.STRING, config.flagSupportBlockMaterial.name)

    // Step 6: Add both positions to cache (within lock scope — before lock release!)
    manager.addToCache(bannerBlock.world.name, bannerBlock.x shr 4, bannerBlock.z shr 4,
        bannerBlock.x, bannerBlock.y, bannerBlock.z)
    manager.addToCache(supportBlock.world.name, supportBlock.x shr 4, supportBlock.z shr 4,
        supportBlock.x, supportBlock.y, supportBlock.z)

    // Step 7: Release lock
    lock.unlock()

    // Step 8: Spawn ArmorStand (main thread, after lock release)
    val asLocation = bannerBlock.location.clone().add(0.5, 1.0, 0.5)
    val armorStand = try {
        bannerBlock.world.spawn(asLocation, ArmorStand::class.java) { as_ ->
            as_.isVisible = false
            as_.isGravity = false
            as_.isMarker = true
            val title = config.flagTitleFormat
                .replace("{type}", flagType)
                .replace("{player}", ownerName)
            as_.customName(Component.text(title))
            as_.isCustomNameVisible = true
        }
    } catch (e: Exception) {
        // Step 9 rollback: ArmorStand spawn failed
        rollback(bannerBlock, supportBlock, originalMaterial, flagId, manager, pdc, flagKey, supportMatKey, chunkKey)
        plugin.logger.severe("ArmorStand spawn failed for flag $flagId: ${e.message}")
        return
    }

    // Step 9: Write armorstand PDC
    val asKey = NamespacedKey(plugin, "armorstand/$flagId")
    pdc.set(asKey, PersistentDataType.STRING, armorStand.uniqueId.toString())
}

private fun rollback(
    bannerBlock: Block, supportBlock: Block, originalMaterial: Material,
    flagId: String, manager: FlagStabilityManager,
    pdc: PersistentDataContainer,
    flagKey: NamespacedKey, supportMatKey: NamespacedKey, chunkKey: String
) {
    // AC-29: restore exact original material (NEVER AIR unless original was AIR)
    supportBlock.type = originalMaterial
    pdc.remove(flagKey)
    pdc.remove(supportMatKey)
    manager.removeFromCache(bannerBlock.world.name, bannerBlock.x shr 4, bannerBlock.z shr 4,
        bannerBlock.x, bannerBlock.y, bannerBlock.z)
    manager.removeFromCache(supportBlock.world.name, supportBlock.x shr 4, supportBlock.z shr 4,
        supportBlock.x, supportBlock.y, supportBlock.z)
}
```

---

### 2.2 — Concurrent activation (AC-30, FR-07)

The listener must NOT block the main thread. Pattern:

```kotlin
// Attempt to acquire chunk lock non-blocking
val lock = manager.getChunkLock(chunkKey)
if (!lock.tryLock()) {
    // Retry after 1 tick (wall-clock ~50ms)
    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
        if (!lock.tryLock()) {
            player.sendMessage("Попробуйте ещё раз.")  // 5s wall-clock ≈ 100 ticks
            return@Runnable
        }
        // Re-check position inside lock (AC-17)
        proceedWithActivation(lock, ...)
    }, 100L)  // 100 ticks = 5 seconds wall-clock
    return
}
proceedWithActivation(lock, ...)
```

**Inside lock:** check position not occupied (CC-06), save `originalMaterial`, replace support block, write flag + support_material PDC, add to cache, release lock.  
**After lock release (main thread):** async DB write → on success (main thread callback): spawn ArmorStand, write armorstand PDC. On DB failure: `rollback()` (AC-31).

---

### 2.3 — Null-safe owner name (CC-01)

```kotlin
val ownerName: String = runCatching {
    Bukkit.getOfflinePlayer(ownerUuid).name
}.getOrNull() ?: ownerUuid.toString().also {
    plugin.logger.warning("Could not resolve name for UUID $ownerUuid — using UUID as fallback")
}
```

---

### 2.4 — Player disconnect during async (CC-05)

The async DB callback must NOT reference the `Player` object. All player-facing messages must be scheduled via `Bukkit.getPlayer(uuid)?.sendMessage(...)` — safe if player went offline:

```kotlin
// In async callback, after returning to main thread:
Bukkit.getPlayer(ownerUuid)?.sendMessage("Флаг активирован!") // null-safe — player may have left
```

---

### 2.5 — Apply to both listeners

Modify `OrderFlagListener.onBlockPlace` and `FrontFlagListener.onBlockPlace` to:
1. Call `FlagActivationHelper.checkPreconditions()` — deny and cancel event if failed
2. Acquire chunk lock (non-blocking, 5s retry)
3. Re-check position inside lock
4. Save `originalMaterial` as a local `val` (AC-41)
5. Call `FlagActivationHelper.performActivation(...)` with the saved material and owner name

---

## Tests for this Stage

**Unit tests:**
- Activation denied for world not in allowedWorlds (AC-20)
- Activation denied when < 2 air blocks above (AC-09)
- Activation denied when chunk limit reached (AC-22)
- Two simultaneous activations at same position → only one succeeds (AC-17, CC-06)
- Rollback restores original DIRT block when ArmorStand spawn fails (AC-29)
- Rollback restores original STONE block (not AIR) on DB failure (AC-31)
- Null player name falls back to UUID string (CC-01)
- Player disconnects mid-async → no NPE (CC-05)

**Relevant TCs:** TC-04, TC-21, TC-22, TC-34, TC-37, TC-50, TC-51, TC-52, TC-69, TC-70, TC-74, TC-75

---

## Completion Criteria

- [ ] Order flag activation: places BEDROCK under banner + invisible ArmorStand above + PDC written
- [ ] Front flag activation: same for RED_BANNER
- [ ] Failed activation: world unchanged (original block restored), no orphan ArmorStand
- [ ] Concurrent same-position activation: exactly one succeeds
- [ ] `./gradlew compileKotlin` and `detekt ktlintCheck` pass
