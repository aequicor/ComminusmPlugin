---
genre: how-to
module: comminusm
title: "Stage 04 — Deletion, Deactivation & Front Move"
topic: flag-stability
stage: 4
status: Pending
date: 2026-05-05
---

# Stage 04 — Deletion, Deactivation & Front Move

**Goal:** Rewrite `OrderService.deleteByOwner`, `WorkFrontService.deactivate`, and add `WorkFrontService.move` to clean up support blocks, armor stands, PDC keys, and cache — correctly even when the chunk is not loaded, and safely under concurrent access.

**Spec refs:** Section 6 (Deletion/Deactivation), Section 7 (Front Move), FR-04, AC-10, AC-11, AC-16, AC-21, AC-38, AC-39, CC-02, CC-03, CC-07, CC-08  
**Depends on:** Stage 01 (FlagStabilityManager), Stage 02 (FlagActivationHelper PDC key names)

---

## Tasks

### 4.1 — `FlagCleanupHelper` (shared deletion logic)

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/manager/FlagCleanupHelper.kt`  
**New class.**

```kotlin
fun cleanupFlag(
    world: World,
    supportX: Int, supportY: Int, supportZ: Int,
    bannerX: Int, bannerY: Int, bannerZ: Int,
    flagId: String,
    manager: FlagStabilityManager
) {
    val chunkX = supportX shr 4; val chunkZ = supportZ shr 4
    val chunkKey = manager.chunkKey(world.name, chunkX, chunkZ)
    val lock = manager.getChunkLock(chunkKey)

    // Non-blocking tryLock with 1-tick retry (main thread — must not block)
    if (!lock.tryLock()) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!lock.tryLock()) {
                plugin.logger.warning("Could not acquire lock for cleanup of flag $flagId — will retry on next ChunkLoadEvent")
                return@Runnable
            }
            doCleanup(world, supportX, supportY, supportZ, bannerX, bannerY, bannerZ, flagId, manager, lock)
        }, 1L)
        return
    }
    doCleanup(world, supportX, supportY, supportZ, bannerX, bannerY, bannerZ, flagId, manager, lock)
}

private fun doCleanup(
    world: World,
    supportX: Int, supportY: Int, supportZ: Int,
    bannerX: Int, bannerY: Int, bannerZ: Int,
    flagId: String,
    manager: FlagStabilityManager,
    lock: ReentrantLock
) {
    try {
        val chunk = world.getChunkAt(supportX shr 4, supportZ shr 4)
        val pdc = chunk.persistentDataContainer

        // Step 2: Remove ArmorStand (AC-16)
        val asKey = NamespacedKey(plugin, "armorstand/$flagId")
        val asUuid = pdc.get(asKey, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (asUuid != null) {
            world.getEntity(asUuid)?.remove()
                ?: run {
                    // Fallback: bounding box search
                    val loc = Location(world, supportX.toDouble(), supportY.toDouble(), supportZ.toDouble())
                    world.getNearbyEntities(loc, 1.0, 3.0, 1.0) { it is ArmorStand }
                        .firstOrNull()?.remove()
                }
        }

        // Step 3: Replace support block with AIR (AC-10)
        world.getBlockAt(supportX, supportY, supportZ).type = Material.AIR

        // Step 4: Remove banner block (AC-10)
        val bannerBlock = world.getBlockAt(bannerX, bannerY, bannerZ)
        if (bannerBlock.type == Material.WHITE_BANNER || bannerBlock.type == Material.RED_BANNER) {
            bannerBlock.type = Material.AIR
        }

        // Step 5: Remove PDC keys — armorstand, flag, support_material (AC-31, AC-40)
        pdc.remove(asKey)
        pdc.remove(NamespacedKey(plugin, "flag/$flagId"))
        pdc.remove(NamespacedKey(plugin, "support_material/$flagId"))

        // Step 6: Evict cache — both banner and support positions
        manager.removeFromCache(world.name, supportX shr 4, supportZ shr 4, supportX, supportY, supportZ)
        manager.removeFromCache(world.name, bannerX shr 4, bannerZ shr 4, bannerX, bannerY, bannerZ)

        // Step 7: Release lock (before async DB step)
        lock.unlock()

        // Step 8 (async): Delete DB record — NFR-01, no blocking on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                dbDeleteFn()  // injected as lambda
            } catch (e: Exception) {
                plugin.logger.severe("DB deletion failed for flag $flagId: ${e.message} — manual cleanup may be required")
            }
        })

    } catch (e: Exception) {
        if (lock.isHeldByCurrentThread) lock.unlock()
        plugin.logger.severe("Cleanup failed for flag $flagId: ${e.message}")
    }
}
```

**Unloaded chunk handling (CC-02):**

```kotlin
// Before doCleanup, check if chunk is loaded
val isLoaded = world.isChunkLoaded(supportX shr 4, supportZ shr 4)
if (!isLoaded) {
    // Option: async load the chunk to perform cleanup
    world.getChunkAtAsync(supportX shr 4, supportZ shr 4).thenAccept { chunk ->
        Bukkit.getScheduler().runTask(plugin, Runnable {
            doCleanup(world, supportX, supportY, supportZ, bannerX, bannerY, bannerZ, flagId, manager, lock)
        })
    }
    return
}
```

**Concurrent deactivation (CC-03):** The chunk lock makes this idempotent. If a second call finds the support block already AIR and ArmorStand already gone, the operations are safe no-ops. DB delete on an already-deleted record returns 0 rows affected — no exception.

---

### 4.2 — Modify `OrderService.deleteByOwner`

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/OrderService.kt`

```kotlin
fun deleteByOwner(uuid: UUID) {
    val order = orderRepository.findByOwner(uuid) ?: return
    if (!order.isActivated) {
        // Not activated — just delete from DB (no world artifacts)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            orderRepository.deleteByOwner(uuid)
        })
        chunkCacheManager?.removeOrderChunk(order)
        return
    }
    val world = Bukkit.getWorld(order.centerWorld!!) ?: run {
        plugin.logger.warning("World '${order.centerWorld}' not found for order $uuid — DB-only delete")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { orderRepository.deleteByOwner(uuid) })
        return
    }
    val supportY = order.centerY - 1  // support block is 1 below the banner
    flagCleanupHelper.cleanupFlag(
        world = world,
        supportX = order.centerX, supportY = supportY, supportZ = order.centerZ,
        bannerX = order.centerX, bannerY = order.centerY, bannerZ = order.centerZ,
        flagId = "order/${uuid}",
        manager = flagStabilityManager,
        dbDeleteFn = { orderRepository.deleteByOwner(uuid) }
    )
    chunkCacheManager?.removeOrderChunk(world.getChunkAt(order.centerX shr 4, order.centerZ shr 4))
}
```

---

### 4.3 — Modify `WorkFrontService.deactivate`

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/WorkFrontService.kt`

Replaces existing manual block-break + PDC-clear code with `flagCleanupHelper.cleanupFlag(...)`.

**Full inventory — pending flag (AC-21, AC-39):**

```kotlin
fun deactivate(uuid: UUID) {
    val front = repository.findByOwner(uuid) ?: return
    
    // Try to give the flag item back
    val player = Bukkit.getPlayer(uuid)
    val flagItem = createFrontFlagItem(uuid)
    if (player != null && player.inventory.firstEmpty() >= 0) {
        player.inventory.addItem(flagItem)
    } else {
        // Write pending_flag marker to PDC of front's chunk (AC-39, AC-21)
        val world = Bukkit.getWorld(front.centerWorld) ?: return
        val supportX = front.centerX; val supportZ = front.centerZ
        val chunk = world.getChunkAt(supportX shr 4, supportZ shr 4)
        val pendingKey = NamespacedKey(plugin, "pending_flag/${front.ownerUuid}")
        val payload = "SENTINEL:FRONT:${front.ownerUuid}"
        chunk.persistentDataContainer.set(pendingKey, PersistentDataType.STRING, payload)
        player?.sendMessage("Инвентарь полон! Флаг сохранён. Освободите место и введите /party для получения флага.")
    }

    flagCleanupHelper.cleanupFlag(
        world = Bukkit.getWorld(front.centerWorld) ?: return,
        supportX = front.centerX, supportY = front.centerY - 1, supportZ = front.centerZ,
        bannerX = front.centerX, bannerY = front.centerY, bannerZ = front.centerZ,
        flagId = "front/${front.ownerUuid}",
        manager = flagStabilityManager,
        dbDeleteFn = { repository.deleteByOwner(uuid) }
    )
}
```

**Note:** the `pending_flag` key uses `{playerUuid}` as suffix per spec Section 3 Key 5 decision (flag ID = player UUID for fronts since one player = one front).

---

### 4.4 — Add `WorkFrontService.move` (Front Move, AC-11, AC-38)

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/WorkFrontService.kt`

16-step flow per spec Section 7. Summary:

1. Acquire new-chunk lock (lexicographic ordering if old != new chunk)
2. Acquire old-chunk lock
3. Async: update DB to new coordinates (DB-first per AC-38)
4. On callback: if DB failed → release both locks, notify player (error)
5. Write NEW chunk PDC crash-recovery anchor (`comminusm:flag/front/{uuid}` on new chunk)
6. Release old-chunk lock (after new PDC anchor written)
7. Delete old ArmorStand
8. Set old support block to AIR
9. Set old banner to AIR
10. Remove old PDC keys (flag, armorstand, support_material)
11. Evict old positions from cache
12. Release new-chunk lock
13. Place new support block (BEDROCK/OBSIDIAN)
14. Add new positions to cache
15. Spawn new ArmorStand
16. Write new armorstand PDC + update support_material PDC; if PDC write fails → retry × 1, then CRITICAL log

**Lock ordering rule (prevents AB/BA deadlock):**
```kotlin
val oldKey = manager.chunkKey(world.name, oldChunkX, oldChunkZ)
val newKey = manager.chunkKey(world.name, newChunkX, newChunkZ)
val (firstKey, secondKey) = if (oldKey < newKey) oldKey to newKey else newKey to oldKey
val firstLock = manager.getChunkLock(firstKey)
val secondLock = manager.getChunkLock(secondKey)
// Acquire in lexicographic order
```

---

## Tests for this Stage

- Order deletion: ArmorStand removed, support block = AIR, PDC keys gone, cache evicted (AC-10)
- Front deactivation with free inventory: flag returned to player, world cleaned up (AC-11)
- Front deactivation with full inventory: pending marker written, player notified (AC-21)
- Concurrent deactivation of same front → idempotent, no exception (CC-03)
- Deletion when chunk not loaded → deferred via getChunkAtAsync (CC-02)
- Front move: DB updated first, then world changes (AC-38)
- Front move ArmorStand spawn fails: new position rolled back, old position gone, startup scan picks up recovery (Section 7 Step 13R)

**Relevant TCs:** TC-05, TC-06, TC-24, TC-25, TC-39, TC-54, TC-55, TC-62, TC-63, TC-71, TC-72, TC-76, TC-77

---

## Completion Criteria

- [ ] Order deletion leaves no BEDROCK, no ArmorStand, no PDC keys in world
- [ ] Front deactivation: world clean + flag in inventory (or pending marker)
- [ ] Front move: DB updated before any world change; crash between steps recoverable
- [ ] `./gradlew compileKotlin` and `detekt ktlintCheck` pass
