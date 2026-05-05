---
genre: how-to
module: comminusm
title: "Stage 05 — ChunkLoadEvent Handler: Lazy Repair & Passive Verification"
topic: flag-stability
stage: 5
status: Pending
date: 2026-05-05
---

# Stage 05 — ChunkLoadEvent Handler

**Goal:** Implement `FlagChunkListener` to handle crash recovery, passive verification, dirty_armorstand cleanup, and cache rebuilding — as a single combined async task per event.

**Spec refs:** Section 9 (ChunkLoadEvent), AC-14, AC-18, AC-23, AC-24, AC-26, AC-27, AC-36, AC-37, AC-40  
**Depends on:** Stage 01, Stage 02, Stage 04

---

## Tasks

### 5.1 — FlagChunkListener

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/listener/FlagChunkListener.kt`  
**New class.**

```kotlin
class FlagChunkListener(
    private val plugin: Plugin,
    private val manager: FlagStabilityManager,
    private val orderRepository: OrderRepository,
    private val workFrontRepository: WorkFrontRepository
) : Listener {

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        // Step A (main thread): rebuild cache from PDC
        manager.rebuildCacheFromPdc(chunk)

        // Collect PDC state synchronously (main thread)
        val pdc = chunk.persistentDataContainer
        val flagKeys = pdc.keys.filter { it.namespace == "comminusm" && it.key.startsWith("flag/") }
        val dirtyAsKeys = pdc.keys.filter { it.namespace == "comminusm" && it.key.startsWith("dirty_armorstand/") }

        if (flagKeys.isEmpty() && dirtyAsKeys.isEmpty()) return  // nothing to do

        // Dispatch single combined async task (Q5 fix — no interleaving)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            processChunk(chunk, flagKeys, dirtyAsKeys, pdc)
        })
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        manager.evictChunkCache(chunk.world.name, chunk.x, chunk.z)
        manager.evictChunkLock(manager.chunkKey(chunk.world.name, chunk.x, chunk.z))
    }
}
```

### 5.2 — Combined async task body

```kotlin
private fun processChunk(
    chunk: Chunk, flagKeys: List<NamespacedKey>, dirtyAsKeys: List<NamespacedKey>,
    pdc: PersistentDataContainer
) {
    // Step B: dirty_armorstand cleanup (from previous partial rollback, AC-37)
    val dirtyAsActions = mutableListOf<() -> Unit>()
    for (key in dirtyAsKeys) {
        val uuidStr = pdc.get(key, PersistentDataType.STRING) ?: continue
        val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
        dirtyAsActions += { chunk.world.getEntity(uuid)?.remove() }
        dirtyAsActions += { pdc.remove(key) }
    }

    // Step C: for each registered flag — check DB + support block material
    data class FlagRepairAction(val flagId: String, val decision: RepairDecision)
    enum class RepairDecision { REPAIR_AS, COMPLETE_DELETION, RESTORE_SUPPORT, DB_UNAVAILABLE }

    val repairActions = mutableListOf<FlagRepairAction>()
    for (nsKey in flagKeys) {
        val flagId = nsKey.key.removePrefix("flag/")
        val coords = pdc.get(nsKey, PersistentDataType.LONG_ARRAY) ?: continue
        if (coords.size != 3) continue
        val (bx, by, bz) = Triple(coords[0].toInt(), coords[1].toInt(), coords[2].toInt())

        val asKey = NamespacedKey(chunk.world.name, "armorstand/$flagId")
        val supportMatKey = NamespacedKey(chunk.world.name, "support_material/$flagId")

        val hasAsKey = pdc.has(asKey, PersistentDataType.STRING)
        val hasSupportMatKey = pdc.has(supportMatKey, PersistentDataType.STRING)
        val storedMaterial = if (hasSupportMatKey)
            runCatching { Material.valueOf(pdc.get(supportMatKey, PersistentDataType.STRING)!!) }.getOrNull()
        else null

        // Step C: passive verification — is support block still the expected material?
        // (deferred to main thread — needs world access)

        // Step D/E disambiguation: check DB
        val dbRecord = try {
            if (flagId.startsWith("order/")) orderRepository.findById(flagId.removePrefix("order/"))
            else workFrontRepository.findById(flagId.removePrefix("front/"))
        } catch (e: Exception) {
            // AC-36: DB unavailable → fail-safe
            plugin.logger.warning("DB unavailable during ChunkLoadEvent for flag $flagId: ${e.message}")
            repairActions += FlagRepairAction(flagId, RepairDecision.DB_UNAVAILABLE)
            continue
        }

        val decision = when {
            !hasAsKey && dbRecord != null -> RepairDecision.REPAIR_AS       // AC-24: crashed during activation
            !hasAsKey && dbRecord == null -> RepairDecision.COMPLETE_DELETION // AC-23: crashed during deletion
            hasAsKey && dbRecord != null  -> RepairDecision.RESTORE_SUPPORT   // AC-14/AC-27: check support block
            else -> RepairDecision.COMPLETE_DELETION
        }
        repairActions += FlagRepairAction(flagId, decision)
    }

    // Step F: apply all decisions on main thread
    Bukkit.getScheduler().runTask(plugin, Runnable {
        applyRepairActions(chunk, pdc, flagKeys, repairActions, dirtyAsActions)
    })
}
```

### 5.3 — Main-thread repair application

```kotlin
private fun applyRepairActions(
    chunk: Chunk, pdc: PersistentDataContainer,
    flagKeys: List<NamespacedKey>, repairActions: List<FlagRepairAction>,
    dirtyAsActions: List<() -> Unit>
) {
    // Apply dirty_armorstand cleanup first
    dirtyAsActions.forEach { it() }

    for (action in repairActions) {
        val nsKey = flagKeys.first { it.key == "flag/${action.flagId}" }
        val coords = pdc.get(nsKey, PersistentDataType.LONG_ARRAY) ?: continue
        val (bx, by, bz) = Triple(coords[0].toInt(), coords[1].toInt(), coords[2].toInt())
        val supportBlock = chunk.world.getBlockAt(bx, by - 1, bz)
        val bannerBlock = chunk.world.getBlockAt(bx, by, bz)
        val flagId = action.flagId

        when (action.decision) {
            RepairDecision.REPAIR_AS -> {
                // AC-24: create missing ArmorStand (double-spawn guard)
                val asKey = NamespacedKey(chunk.world.name, "armorstand/$flagId")
                if (!pdc.has(asKey, PersistentDataType.STRING)) {
                    val ownerUuid = extractOwnerUuid(flagId)
                    val ownerName = ownerUuid?.let {
                        runCatching { Bukkit.getOfflinePlayer(it).name }.getOrNull() ?: it.toString()
                    } ?: "Unknown"
                    spawnArmorStand(bannerBlock, flagId, ownerName, pdc)
                }
            }
            RepairDecision.COMPLETE_DELETION -> {
                // AC-23: partial deletion — finish it
                chunk.world.getEntity(
                    pdc.get(NamespacedKey(chunk.world.name, "armorstand/$flagId"), PersistentDataType.STRING)
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@forEach
                )?.remove()
                supportBlock.type = Material.AIR
                if (bannerBlock.type == Material.WHITE_BANNER || bannerBlock.type == Material.RED_BANNER)
                    bannerBlock.type = Material.AIR
                pdc.remove(nsKey)
                pdc.remove(NamespacedKey(chunk.world.name, "armorstand/$flagId"))
                pdc.remove(NamespacedKey(chunk.world.name, "support_material/$flagId"))
                manager.removeFromCache(chunk.world.name, chunk.x, chunk.z, bx, by, bz)
                manager.removeFromCache(chunk.world.name, chunk.x, chunk.z, bx, by - 1, bz)
                plugin.logger.warning("Completed partial deletion for flag $flagId at $bx,$by,$bz")
            }
            RepairDecision.RESTORE_SUPPORT -> {
                // AC-14/AC-27: check if support block is correct material
                val storedMat = pdc.get(
                    NamespacedKey(chunk.world.name, "support_material/$flagId"), PersistentDataType.STRING
                )?.let { runCatching { Material.valueOf(it) }.getOrNull() }

                if (storedMat == null) {
                    plugin.logger.warning("Missing support_material PDC for flag $flagId — skipping passive verification")
                } else if (supportBlock.type != storedMat) {
                    if (supportBlock.type == Material.AIR) {
                        // AC-14 lazy repair: restore support block (replaced by /setblock or crash)
                        supportBlock.type = storedMat
                        plugin.logger.warning("Restored support block for flag $flagId at $bx,${by-1},$bz — was AIR")
                    } else {
                        // Support block material changed (admin /setblock to different material)
                        supportBlock.type = storedMat
                        plugin.logger.warning("Passive verification: restored support block for $flagId — was ${supportBlock.type}")
                    }
                }
                // Check ArmorStand entity still exists (double-spawn guard)
                val asUuidStr = pdc.get(NamespacedKey(chunk.world.name, "armorstand/$flagId"), PersistentDataType.STRING)
                val asUuid = asUuidStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (asUuid == null || chunk.world.getEntity(asUuid) == null) {
                    // ArmorStand lost — recreate (double-spawn guard: check key absent or entity absent)
                    val ownerUuid = extractOwnerUuid(flagId)
                    val ownerName = ownerUuid?.let {
                        runCatching { Bukkit.getOfflinePlayer(it).name }.getOrNull() ?: it.toString()
                    } ?: "Unknown"
                    spawnArmorStand(bannerBlock, flagId, ownerName, pdc)
                }
                // AC-25: lazy refresh — check if name matches current owner
                refreshArmorStandName(asUuid, flagId, pdc)
            }
            RepairDecision.DB_UNAVAILABLE -> {
                // AC-36: fail-safe — do nothing, retry next ChunkLoadEvent
            }
        }
    }
}
```

---

### 5.4 — Register FlagChunkListener in ComminusmPlugin

```kotlin
// In onEnable(), after all services/managers initialized:
server.pluginManager.registerEvents(
    FlagChunkListener(this, flagStabilityManager, orderRepository, workFrontRepository),
    this
)
```

---

## Tests for this Stage

- ChunkLoad with support block + no armorstand PDC key → ArmorStand created (AC-24)
- ChunkLoad with armorstand PDC key present but entity gone → AS recreated (RESTORE_SUPPORT path)
- ChunkLoad with support block = AIR, DB active → support block restored + AS recreated (AC-14)
- ChunkLoad with armorstand key but no DB record → deletion completed (AC-23)
- ChunkLoad with DB unavailable → no world changes, chunk loads normally (AC-36)
- ChunkLoad with dirty_armorstand marker, entity exists → entity removed, marker cleared (AC-37)
- ChunkLoad with dirty_armorstand marker, entity gone → marker cleared (AC-37)
- Rapid ChunkLoad cycles → idempotent, no duplicate ArmorStands (CC-11)

**Relevant TCs:** TC-29, TC-30, TC-35, TC-41..TC-47, TC-58..TC-61, TC-66..TC-67, TC-80

---

## Completion Criteria

- [ ] Crash-recovery scenarios produce correct world state on next chunk load
- [ ] No world changes when DB is unavailable (fail-safe)
- [ ] Passive verification restores tampered support blocks
- [ ] dirty_armorstand markers cleaned up on both success and not-found paths
- [ ] `./gradlew compileKotlin` and `detekt ktlintCheck` pass
