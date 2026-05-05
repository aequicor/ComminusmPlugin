package ru.kyamshanov.comminusm.manager

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.config.PluginConfig
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/** Result of flag placement pre-condition checks. */
sealed class ActivationCheckResult {
    /** The check failed; [reason] is a player-facing message. */
    data class Denied(val reason: String) : ActivationCheckResult()

    /** The check passed; [chunkKey] is the canonical chunk lock key. */
    data class Ok(val chunkKey: String) : ActivationCheckResult()
}

/**
 * Stateless helper that encapsulates the two-phase flag activation flow:
 * synchronous pre-checks + world mutation (main thread) and async DB write
 * followed by ArmorStand spawn (main thread callback).
 */
class FlagActivationHelper(private val plugin: Plugin) {

    /**
     * Validates placement constraints before acquiring the chunk lock.
     *
     * Checks performed (in order):
     * 1. World is in the allowed-worlds allowlist.
     * 2. Sufficient air above the banner.
     * 3. Chunk flag count does not exceed the configured limit.
     */
    fun checkPreconditions(
        bannerBlock: Block,
        config: PluginConfig,
        manager: FlagStabilityManager
    ): ActivationCheckResult {
        // World allowlist
        if (bannerBlock.world.name !in config.flagAllowedWorlds) {
            return ActivationCheckResult.Denied("Флаги можно устанавливать только в разрешённых мирах.")
        }

        // Free air above the banner
        val airRequired = if (bannerBlock.y >= MAX_BUILD_HEIGHT) 1 else config.flagMinAirAbove
        val freeAbove = (1..airRequired).count { offset ->
            val mat = bannerBlock.world.getBlockAt(bannerBlock.x, bannerBlock.y + offset, bannerBlock.z).type
            mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR
        }
        if (freeAbove < airRequired) {
            return ActivationCheckResult.Denied("Недостаточно места над флагом.")
        }

        // Chunk flag limit
        val count = bannerBlock.chunk.persistentDataContainer.keys
            .count { it.namespace == "comminusm" && it.key.startsWith("flag/") }
        if (count >= config.flagMaxPerChunk) {
            return ActivationCheckResult.Denied("Достигнут лимит флагов в этом чанке.")
        }

        val chunkX = bannerBlock.x shr CHUNK_SHIFT
        val chunkZ = bannerBlock.z shr CHUNK_SHIFT
        return ActivationCheckResult.Ok(manager.chunkKey(bannerBlock.world.name, chunkX, chunkZ))
    }

    /**
     * Resolves the offline player name for [ownerUuid].
     * Falls back to the UUID string if the name cannot be retrieved.
     */
    fun resolveOwnerName(ownerUuid: UUID): String =
        runCatching { Bukkit.getOfflinePlayer(ownerUuid).name }
            .getOrNull()
            .also { name ->
                if (name == null) {
                    plugin.logger.warning(
                        "Could not resolve name for UUID $ownerUuid — using UUID as fallback"
                    )
                }
            }
            ?: ownerUuid.toString()

    /**
     * Executes the two-phase activation flow.
     *
     * **Must be called on the main thread with [lock] already held.**
     *
     * Phase 1 (synchronous, inside lock):
     * - Saves the original material of the support block.
     * - Replaces the support block with [PluginConfig.flagSupportBlockMaterial].
     * - Writes PDC keys (flag position + support material).
     * - Adds both positions to the in-memory cache.
     * - Releases [lock].
     *
     * Phase 2 (async → main-thread callback):
     * - Invokes [dbWrite] on an async thread.
     * - On success: spawns the ArmorStand, writes armorstand PDC key, calls [onSuccess].
     * - On failure: rolls back all world changes, calls [onDbFailure].
     *
     * @param bannerBlock  the placed banner block
     * @param flagId       logical flag identifier, e.g. "order/{uuid}" or "front/{uuid}"
     * @param ownerUuid    owner UUID — used for null-safe player lookup after async
     * @param ownerName    resolved display name (may be UUID string if lookup failed)
     * @param flagType     human-readable type label, e.g. "Ордер" or "Трудовой Фронт"
     * @param config       plugin configuration
     * @param manager      FlagStabilityManager instance
     * @param lock         already-acquired chunk lock; **this function releases it**
     * @param dbWrite      blocking DB operation — runs on an async thread; throw to signal failure
     * @param onSuccess    called on main thread after ArmorStand spawned; receives online Player or null
     * @param onDbFailure  called on main thread after rollback; receives online Player or null
     */
    @Suppress("LongParameterList")
    fun activate(
        bannerBlock: Block,
        flagId: String,
        ownerUuid: UUID,
        ownerName: String,
        flagType: String,
        config: PluginConfig,
        manager: FlagStabilityManager,
        lock: ReentrantLock,
        dbWrite: () -> Unit,
        onSuccess: (Player?) -> Unit,
        onDbFailure: (Player?) -> Unit
    ) {
        val supportBlock = bannerBlock.world.getBlockAt(bannerBlock.x, bannerBlock.y - 1, bannerBlock.z)
        val originalMaterial = supportBlock.type

        // Phase 1a: Replace support block
        supportBlock.type = config.flagSupportBlockMaterial

        // Phase 1b: Write PDC keys
        val chunk = supportBlock.chunk
        val pdc = chunk.persistentDataContainer
        val flagKey = NamespacedKey(plugin, "flag/$flagId")
        val supportMatKey = NamespacedKey(plugin, "support_material/$flagId")
        pdc.set(
            flagKey,
            PersistentDataType.LONG_ARRAY,
            longArrayOf(bannerBlock.x.toLong(), bannerBlock.y.toLong(), bannerBlock.z.toLong())
        )
        pdc.set(supportMatKey, PersistentDataType.STRING, originalMaterial.name)

        // Phase 1c: Add both positions to cache
        manager.addToCache(
            bannerBlock.world.name, bannerBlock.x shr CHUNK_SHIFT, bannerBlock.z shr CHUNK_SHIFT,
            bannerBlock.x, bannerBlock.y, bannerBlock.z
        )
        manager.addToCache(
            supportBlock.world.name, supportBlock.x shr CHUNK_SHIFT, supportBlock.z shr CHUNK_SHIFT,
            supportBlock.x, supportBlock.y, supportBlock.z
        )

        // Phase 1d: Release lock before async work
        lock.unlock()

        // Phase 2: Async DB write
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                try {
                    dbWrite()
                } catch (e: Exception) {
                    plugin.logger.severe("DB write failed for flag $flagId: ${e.message}")
                    Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                            rollback(bannerBlock, supportBlock, originalMaterial, flagId, manager, pdc, flagKey, supportMatKey)
                            onDbFailure(Bukkit.getPlayer(ownerUuid))
                        }
                    )
                    return@Runnable
                }

                // DB succeeded — spawn ArmorStand on main thread
                Bukkit.getScheduler().runTask(
                    plugin,
                    Runnable {
                        val asLocation = bannerBlock.location.clone().add(0.5, 1.0, 0.5)
                        val armorStand = try {
                            bannerBlock.world.spawn(asLocation, ArmorStand::class.java) { stand ->
                                stand.setVisible(false)
                                stand.setGravity(false)
                                stand.setMarker(true)
                                val title = config.flagTitleFormat
                                    .replace("{type}", flagType)
                                    .replace("{player}", ownerName)
                                stand.customName(Component.text(title))
                                stand.isCustomNameVisible = true
                            }
                        } catch (e: Exception) {
                            plugin.logger.severe("ArmorStand spawn failed for flag $flagId: ${e.message}")
                            rollback(bannerBlock, supportBlock, originalMaterial, flagId, manager, pdc, flagKey, supportMatKey)
                            onDbFailure(Bukkit.getPlayer(ownerUuid))
                            return@Runnable
                        }

                        // Write armorstand PDC key
                        val asKey = NamespacedKey(plugin, "armorstand/$flagId")
                        pdc.set(asKey, PersistentDataType.STRING, armorStand.uniqueId.toString())

                        onSuccess(Bukkit.getPlayer(ownerUuid))
                    }
                )
            }
        )
    }

    private fun rollback(
        bannerBlock: Block,
        supportBlock: Block,
        originalMaterial: Material,
        flagId: String,
        manager: FlagStabilityManager,
        pdc: PersistentDataContainer,
        flagKey: NamespacedKey,
        supportMatKey: NamespacedKey
    ) {
        supportBlock.type = originalMaterial
        pdc.remove(flagKey)
        pdc.remove(supportMatKey)
        manager.removeFromCache(
            bannerBlock.world.name, bannerBlock.x shr CHUNK_SHIFT, bannerBlock.z shr CHUNK_SHIFT,
            bannerBlock.x, bannerBlock.y, bannerBlock.z
        )
        manager.removeFromCache(
            supportBlock.world.name, supportBlock.x shr CHUNK_SHIFT, supportBlock.z shr CHUNK_SHIFT,
            supportBlock.x, supportBlock.y, supportBlock.z
        )
    }

    private companion object {
        /** Y-coordinate threshold above which only 1 air block is required. */
        const val MAX_BUILD_HEIGHT = 318

        /** Bit-shift to convert block X/Z to chunk coordinate. */
        const val CHUNK_SHIFT = 4
    }
}
