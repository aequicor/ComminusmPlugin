---
genre: how-to
module: comminusm
title: "Stage 06 — Startup Repair Scan & /party Pending Flag"
topic: flag-stability
stage: 6
status: Pending
date: 2026-05-05
---

# Stage 06 — Startup Repair Scan & /party Pending Flag

**Goal:** Implement the startup repair scan that re-creates ArmorStands for flags that lost them during a server crash between activation steps. Also wire the smart `/party` command behavior to handle pending flags (AC-39).

**Spec refs:** Section 15.1 (Startup repair scan), AC-39, AC-18, AC-24, CC-11  
**Depends on:** Stage 01, Stage 02, Stage 05

---

## Tasks

### 6.1 — Startup repair scan in `ComminusmPlugin.onEnable`

**Phase 1 (async): Query DB for all active flags**

```kotlin
// In ComminusmPlugin.onEnable(), after all services initialized and listeners registered:
Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
    val allOrders = orderRepository.findAllActivated()   // returns List<Order> with non-null centerWorld
    val allFronts = workFrontRepository.findAllActivated() // returns List<WorkFront>

    if (allOrders.size + allFronts.size > 100) {
        logger.warning("Startup repair scan: processing ${allOrders.size + allFronts.size} flags — this may take a moment")
    }

    // Phase 2: return to main thread, process in batches
    Bukkit.getScheduler().runTask(this, Runnable {
        startupRepairPhase2(allOrders, allFronts, 0)
    })
})
```

**Phase 2 (main thread, tick-spread): Process N flags per tick**

```kotlin
private fun startupRepairPhase2(
    orders: List<Order>, fronts: List<WorkFront>, offset: Int
) {
    val batchSize = pluginConfig.flagStartupScanBatchSize
    val allFlags: List<Pair<String, Triple<String, Int, Int>>> = buildList {
        orders.forEach { o ->
            if (o.isActivated) add("order/${o.ownerUuid}" to Triple(o.centerWorld!!, o.centerX, o.centerY))
        }
        fronts.forEach { f ->
            add("front/${f.ownerUuid}" to Triple(f.centerWorld, f.centerX, f.centerY))
        }
    }
    val batch = allFlags.drop(offset).take(batchSize)
    if (batch.isEmpty()) return  // Done

    batch.forEach { (flagId, pos) ->
        val (worldName, bx, by) = pos
        // world.isChunkLoaded() called here on main thread — safe (Q4 fix)
        val world = Bukkit.getWorld(worldName) ?: return@forEach
        val chunkX = bx shr 4; val chunkZ = (pos.third) shr 4  // use Z from full position
        if (!world.isChunkLoaded(chunkX, chunkZ)) return@forEach  // Only repair loaded chunks

        val chunk = world.getChunkAt(chunkX, chunkZ)
        val pdc = chunk.persistentDataContainer
        val asKey = NamespacedKey(this, "armorstand/$flagId")

        // Double-spawn guard (Q3 fix — CC-11)
        val existingUuid = pdc.get(asKey, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (existingUuid != null && world.getEntity(existingUuid) != null) {
            return@forEach  // ArmorStand already exists — skip
        }

        // Need to recreate ArmorStand
        val ownerUuid = extractOwnerUuid(flagId)
        val ownerName = ownerUuid?.let {
            runCatching { Bukkit.getOfflinePlayer(it).name }.getOrNull() ?: it.toString()
        } ?: "Unknown"
        val bannerBlock = world.getBlockAt(bx, by, /* z= */ /* need full coords */ 0)  // use actual Z
        spawnArmorStand(bannerBlock, flagId, ownerName, pdc)
    }

    // Schedule next batch on the following tick
    if (offset + batchSize < allFlags.size) {
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            startupRepairPhase2(orders, fronts, offset + batchSize)
        }, 1L)
    }
}
```

**Note:** The `allFlags` list must carry full coordinates (X, Y, Z). The sketch above uses a `Triple` — in the real implementation use a data class or the existing model object directly.

---

### 6.2 — Smart `/party` command: pending flag (AC-39)

**File:** wherever the `/party` command handler is implemented (search for `PartyMenu` or the command executor).

When the player requests a new flag via `/party`, before creating a new flag item:

```kotlin
fun handlePartyFlagRequest(player: Player, flagType: FlagType) {
    val ownerUuid = player.uniqueId

    // Determine the player's active flag ID
    val activeFlag = when (flagType) {
        FlagType.ORDER -> orderRepository.findByOwner(ownerUuid)
            ?.let { "order/${ownerUuid}" to it.centerWorld to it.centerX to it.centerZ }
        FlagType.FRONT -> workFrontRepository.findByOwner(ownerUuid)
            ?.let { "front/${ownerUuid}" to it.centerWorld to it.centerX to it.centerZ }
    }

    if (activeFlag != null) {
        val (flagId, worldName, cx, cz) = activeFlag
        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            val chunk = world.getChunkAt(cx shr 4, cz shr 4)
            val pendingKey = NamespacedKey(plugin, "pending_flag/$flagId")
            val pendingPayload = chunk.persistentDataContainer.get(pendingKey, PersistentDataType.STRING)
            if (pendingPayload != null) {
                // Pending flag exists — try to deliver it
                val flagItem = parsePendingFlagItem(pendingPayload, player)
                if (flagItem != null && player.inventory.firstEmpty() >= 0) {
                    player.inventory.addItem(flagItem)
                    chunk.persistentDataContainer.remove(pendingKey)
                    player.sendMessage("Ваш флаг возвращён.")
                    return
                } else {
                    player.sendMessage("Освободите место в инвентаре, чтобы получить ваш флаг.")
                    return
                }
            }
        }
    }

    // No pending flag — proceed with normal /party flag issuance
    issueNewFlag(player, flagType)
}
```

**`parsePendingFlagItem`:** handles `SENTINEL:FRONT:{uuid}` and `SENTINEL:ORDER:{uuid}` by constructing the appropriate flag item; handles `ITEM:{base64}` by deserialising the ItemStack:

```kotlin
fun parsePendingFlagItem(payload: String, player: Player): ItemStack? {
    val colonIdx = payload.indexOf(':')
    if (colonIdx < 0) {
        plugin.logger.severe("Malformed pending_flag payload for ${player.name} — deleting marker")
        return null
    }
    return when (val type = payload.substring(0, colonIdx)) {
        "SENTINEL" -> {
            val rest = payload.substring(colonIdx + 1)
            when {
                rest.startsWith("FRONT:") -> createFrontFlagItem(UUID.fromString(rest.removePrefix("FRONT:")))
                rest.startsWith("ORDER:") -> createOrderFlagItem(UUID.fromString(rest.removePrefix("ORDER:")))
                else -> { plugin.logger.severe("Unknown SENTINEL type '$rest'"); null }
            }
        }
        "ITEM" -> {
            val b64 = payload.substring(colonIdx + 1)
            if (b64.isEmpty()) { plugin.logger.severe("Empty ITEM payload for ${player.name}"); return null }
            try {
                ItemStack.deserializeBytes(Base64.getDecoder().decode(b64))
            } catch (e: Exception) {
                plugin.logger.severe("Failed to deserialize pending flag item for ${player.name}: ${e.message}")
                null
            }
        }
        else -> { plugin.logger.severe("Unknown pending_flag type '$type' for ${player.name}"); null }
    }
}
```

---

## Tests for this Stage

- Startup scan with loaded chunk + missing ArmorStand → ArmorStand created (AC-18, AC-24)
- Startup scan with existing ArmorStand (PDC key + entity both present) → no duplicate created (CC-11 guard)
- Startup scan processes flags in batches of N per tick (flagStartupScanBatchSize)
- `/party` with pending SENTINEL marker + free inventory slot → delivers pending flag, clears marker (AC-39)
- `/party` with pending SENTINEL marker + full inventory → player notified, marker not cleared
- `/party` with malformed pending payload → marker deleted, falls through to normal issuance (Section 3 Key 5 corruption handling)
- `/party` with no pending marker → normal new flag issuance

**Relevant TCs:** TC-35, TC-42, TC-43, TC-64, TC-65, TC-80

---

## Completion Criteria

- [ ] Startup scan: flags with missing ArmorStands repaired on server start
- [ ] Startup scan: processes in tick-spread batches
- [ ] `/party` delivers pending flag when marker present and inventory free
- [ ] No duplicate ArmorStands from scan + ChunkLoadEvent race
- [ ] `./gradlew compileKotlin` and `detekt ktlintCheck` pass
