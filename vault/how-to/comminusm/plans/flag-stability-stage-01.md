---
genre: how-to
module: comminusm
title: "Stage 01 — Infrastructure: FlagStabilityManager, Config, PDC Keys"
topic: flag-stability
stage: 1
status: Pending
date: 2026-05-05
---

# Stage 01 — Infrastructure

**Goal:** Introduce the foundational classes and config keys that all subsequent stages depend on. No behaviour changes to existing code — only additive.

**Spec refs:** Spec Section 2 (Architecture), Section 3 (PDC keys), Section 4 (Config), Section 10 (Concurrent Safety)

---

## Tasks

### 1.1 — PluginConfig additions

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/config/PluginConfig.kt`

Add six new lazy/computed properties following existing patterns:

```kotlin
val flagSupportBlockMaterial: Material
    get() {
        val name = config.getString("flag.supportBlockMaterial", "BEDROCK") ?: "BEDROCK"
        return runCatching { Material.valueOf(name) }
            .getOrElse {
                ComminusmPlugin.getInstance().logger.severe(
                    "flag.supportBlockMaterial '$name' is invalid — falling back to BEDROCK"
                )
                Material.BEDROCK
            }
    }

val flagMinAirAbove: Int
    get() = config.getInt("flag.minAirAbove", 2).coerceAtLeast(1)

val flagTitleFormat: String
    get() = config.getString("flag.titleFormat", "§6{type} — §f{player}") ?: "§6{type} — §f{player}"

val flagMaxPerChunk: Int
    get() {
        val v = config.getInt("flag.maxPerChunk", 50)
        if (v <= 0) {
            ComminusmPlugin.getInstance().logger.warning(
                "flag.maxPerChunk must be ≥ 1, got $v — using default 50"
            )
            return 50
        }
        return v
    }

val flagAllowedWorlds: Set<String>
    get() {
        val list = config.getStringList("flag.allowedWorlds")
        if (list.isEmpty()) {
            ComminusmPlugin.getInstance().logger.warning(
                "flag.allowedWorlds is empty — flag placement is disabled in all worlds"
            )
        }
        return list.toSet()
    }

val flagStartupScanBatchSize: Int
    get() = config.getInt("flag.startupScanBatchSize", 10).coerceAtLeast(1)
```

**config.yml** — add defaults under `flag:` section:
```yaml
flag:
  supportBlockMaterial: BEDROCK
  minAirAbove: 2
  titleFormat: "§6{type} — §f{player}"
  maxPerChunk: 50
  allowedWorlds:
    - world
  startupScanBatchSize: 10
```

---

### 1.2 — FlagStabilityManager

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/manager/FlagStabilityManager.kt`  
**New class.**

Responsibilities:
- PDC NamespacedKey constants
- In-memory flag position cache
- Chunk lock map

```kotlin
class FlagStabilityManager(plugin: Plugin) {

    // PDC key constants — all stored on the support block's chunk
    val keyFlag          = NamespacedKey(plugin, "flag")           // comminusm:flag/{id} → LongArray [x,y,z]
    val keyArmorStand    = NamespacedKey(plugin, "armorstand")     // comminusm:armorstand/{id} → UUID string
    val keySupportMat    = NamespacedKey(plugin, "support_material") // comminusm:support_material/{id} → Material name
    val keyDirtyAs       = NamespacedKey(plugin, "dirty_armorstand") // comminusm:dirty_armorstand/{id} → UUID string
    val keyPendingFlag   = NamespacedKey(plugin, "pending_flag")   // comminusm:pending_flag/{flagId} → ITEM:|SENTINEL: string

    // In-memory cache: chunkKey → set of encoded block positions (x.toLong() shl 32 or z.toLong() + y)
    // Encoding: (x.toLong() and 0xFFFFFFFFL) shl 32 or (z.toLong() and 0xFFFF_FFFFL)... use blockKey(x,y,z)
    private val flagPositionCache = ConcurrentHashMap<String, MutableSet<Long>>()

    // Chunk lock map — keyed by canonical chunk key string
    private val chunkLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun chunkKey(world: String, chunkX: Int, chunkZ: Int): String = "$world:$chunkX:$chunkZ"

    fun chunkKeyOf(block: Block): String = chunkKey(
        block.world.name,
        block.x shr 4,
        block.z shr 4
    )

    fun getChunkLock(key: String): ReentrantLock =
        chunkLocks.getOrPut(key) { ReentrantLock() }

    fun releaseChunkLock(key: String) {
        // Lock is NOT removed from map on release — only on ChunkUnloadEvent
        // (removing while locked would break waiting threads)
    }

    fun evictChunkLock(key: String) {
        // Called from ChunkUnloadEvent — safe if no operations are pending on unloaded chunk
        chunkLocks.remove(key)
    }

    // Cache operations
    private fun blockPos(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x7FFFFFFFL) shl 33 or
        (y.toLong() and 0x1FFL) shl 24 or
        (z.toLong() and 0x7FFFFFFFL)

    fun addToCache(worldName: String, chunkX: Int, chunkZ: Int, x: Int, y: Int, z: Int) {
        val key = chunkKey(worldName, chunkX, chunkZ)
        flagPositionCache.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(blockPos(x, y, z))
    }

    fun removeFromCache(worldName: String, chunkX: Int, chunkZ: Int, x: Int, y: Int, z: Int) {
        val key = chunkKey(worldName, chunkX, chunkZ)
        flagPositionCache[key]?.remove(blockPos(x, y, z))
    }

    fun isFlagPosition(block: Block): Boolean {
        val key = chunkKeyOf(block)
        val cached = flagPositionCache[key]
        if (cached != null) return blockPos(block.x, block.y, block.z) in cached
        // Cold-start fallback — read PDC (rare; only before ChunkLoadEvent fires)
        val chunk = block.chunk
        return chunk.persistentDataContainer.keys.any { it.namespace == "comminusm" && it.key.startsWith("flag/") }
    }

    fun evictChunkCache(worldName: String, chunkX: Int, chunkZ: Int) {
        flagPositionCache.remove(chunkKey(worldName, chunkX, chunkZ))
    }

    fun rebuildCacheFromPdc(chunk: Chunk) {
        val key = chunkKey(chunk.world.name, chunk.x, chunk.z)
        val positions = ConcurrentHashMap.newKeySet<Long>()
        val pdc = chunk.persistentDataContainer
        pdc.keys
            .filter { it.namespace == "comminusm" && it.key.startsWith("flag/") }
            .forEach { nsKey ->
                val coords = pdc.get(nsKey, PersistentDataType.LONG_ARRAY) ?: return@forEach
                if (coords.size == 3) positions.add(blockPos(coords[0].toInt(), coords[1].toInt(), coords[2].toInt()))
            }
        flagPositionCache[key] = positions
    }
}
```

**Note on block position encoding:** the encoding formula above is illustrative. Use a robust encoding that handles negative X/Z (Minecraft overworld extends to negative coords). Recommended: `BlockVector.hashCode()` approach or encode as separate fields. Verify before use.

---

### 1.3 — Wire into ComminusmPlugin

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/ComminusmPlugin.kt`

1. Instantiate `FlagStabilityManager` on `onEnable()` and store as a property (or inject into services).
2. Register `FlagChunkListener` (new class from Stage 5 — register as empty stub now, implement in Stage 5).
3. Pass `FlagStabilityManager` instance to `OrderFlagListener`, `FrontFlagListener`, `BlockListener`, `ExplosionListener` constructors (or via DI pattern already in use).

---

## Tests for this Stage

- `PluginConfigTest`: assert that invalid `supportBlockMaterial` falls back to BEDROCK; `maxPerChunk=0` falls back to 50; `allowedWorlds=[]` logs warning.
- `FlagStabilityManagerTest`: addToCache → isFlagPosition returns true; removeFromCache → returns false; evictChunkCache → clean.

**Relevant TCs:** TC-73 (CC-04 invalid config), TC-79 (CC-10 empty allowedWorlds), TC-82 (CC-13 maxPerChunk=0)

---

## Completion Criteria

- [ ] `./gradlew compileKotlin` passes
- [ ] Config properties read correct values with defaults
- [ ] `FlagStabilityManager` instantiated and accessible from listeners
- [ ] `./gradlew detekt ktlintCheck` passes
