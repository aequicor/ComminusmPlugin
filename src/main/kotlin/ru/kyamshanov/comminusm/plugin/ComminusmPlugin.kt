@file:Suppress("TooGenericExceptionCaught", "LoopWithTooManyJumpStatements", "CyclomaticComplexMethod", "MagicNumber")

package ru.kyamshanov.comminusm.plugin

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.di.DIContainer
import java.util.UUID

class ComminusmPlugin : JavaPlugin() {
    private lateinit var container: DIContainer

    companion object {
        private lateinit var instance: ComminusmPlugin

        fun getInstance() = instance

        private const val STARTUP_SCAN_WARN_THRESHOLD = 100
        private const val CHUNK_SHIFT = 4
        private const val COORD_ARRAY_SIZE = 3
        private const val AS_OFFSET_XZ = 0.5
        private const val AS_OFFSET_Y = 1.0
        private const val BATCH_DELAY_TICKS = 1L
    }

    private data class FlagEntry(
        val flagId: String,
        val worldName: String,
        val bx: Int,
        val by: Int,
        val bz: Int,
    )

    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        container = DIContainer(this)

        // Initialize database
        if (!isDatabaseValid()) {
            logger.severe("☭ БАЗА ДАННЫХ ПОВРЕЖДЕНА! Плагин отключён.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Register all listeners
        container.createListeners().forEach { server.pluginManager.registerEvents(it, this) }
        container.createMenus().forEach { server.pluginManager.registerEvents(it, this) }

        // Register all commands
        container.createCommands().forEach { (name, executor) ->
            getCommand(name)?.setExecutor(executor)
        }

        logger.info("☭ Плагин активирован! Трудодни начисляются, Ордера выдаются.")

        // Startup repairs
        startupRepairScan()

        // Commune startup
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                container.communeStartupTask.onEnable()
            },
        )
    }

    private fun isDatabaseValid(): Boolean =
        try {
            container.orderService.toString().isNotEmpty()
        } catch (e: Exception) {
            logger.warning("Database validation failed: ${e.message}")
            false
        }

    private fun startupRepairScan() {
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                val orderRepo = container.provideOrderRepository()
                val frontRepo = container.provideWorkFrontRepository()

                val allFlags =
                    buildList<FlagEntry> {
                        try {
                            orderRepo.findAllActivated().forEach { o ->
                                o.centerWorld?.let { world ->
                                    add(FlagEntry("order/${o.ownerUuid}", world, o.centerX, o.centerY, o.centerZ))
                                }
                            }
                        } catch (e: Exception) {
                            logger.warning("Startup repair: DB error reading orders — ${e.message}")
                        }
                        try {
                            frontRepo.findAllActivated().forEach { f ->
                                f.centerWorld?.let { world ->
                                    add(FlagEntry("front/${f.ownerUuid}", world, f.centerX, f.centerY, f.centerZ))
                                }
                            }
                        } catch (e: Exception) {
                            logger.warning("Startup repair: DB error reading fronts — ${e.message}")
                        }
                    }

                if (allFlags.size > STARTUP_SCAN_WARN_THRESHOLD) {
                    logger.warning("Startup repair scan: processing ${allFlags.size} flags")
                }

                server.scheduler.runTask(this@ComminusmPlugin, Runnable { startupRepairBatch(allFlags, 0, 50) })
            },
        )
    }

    private fun startupRepairBatch(
        allFlags: List<FlagEntry>,
        offset: Int,
        batchSize: Int,
    ) {
        val batch = allFlags.drop(offset).take(batchSize)
        if (batch.isEmpty()) return

        for (entry in batch) {
            val world = Bukkit.getWorld(entry.worldName) ?: continue
            val chunkX = entry.bx shr CHUNK_SHIFT
            val chunkZ = entry.bz shr CHUNK_SHIFT
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue

            val chunk = world.getChunkAt(chunkX, chunkZ)
            val pdc = chunk.persistentDataContainer
            val asKey = NamespacedKey(this, "armorstand/${entry.flagId}")

            val existingUuidStr = pdc.get(asKey, PersistentDataType.STRING)
            if (existingUuidStr != null) {
                val existingUuid = runCatching { UUID.fromString(existingUuidStr) }.getOrNull()
                if (existingUuid != null && world.getEntity(existingUuid) != null) continue
            }

            val flagKey = NamespacedKey(this, "flag/${entry.flagId}")
            val coords = pdc.get(flagKey, PersistentDataType.LONG_ARRAY)
            if (coords == null || coords.size != COORD_ARRAY_SIZE) continue

            val bannerBlock = world.getBlockAt(entry.bx, entry.by, entry.bz)
            val ownerUuid = extractStartupFlagOwnerUuid(entry.flagId) ?: continue
            val ownerName = runCatching { Bukkit.getOfflinePlayer(ownerUuid).name }.getOrNull() ?: ownerUuid.toString()
            val flagType = if (entry.flagId.startsWith("order/")) "Ордер" else "Трудовой Фронт"
            val asLocation = bannerBlock.location.clone().add(AS_OFFSET_XZ, AS_OFFSET_Y, AS_OFFSET_XZ)

            try {
                val armorStand =
                    world.spawn(asLocation, ArmorStand::class.java) { stand ->
                        stand.setVisible(false)
                        stand.setGravity(false)
                        stand.setMarker(true)
                        stand.customName(Component.text("§6$flagType — §f$ownerName"))
                        stand.isCustomNameVisible = true
                    }
                pdc.set(asKey, PersistentDataType.STRING, armorStand.uniqueId.toString())
            } catch (e: Exception) {
                logger.severe(
                    "Startup repair: ArmorStand spawn failed for ${entry.flagId}: ${e.message}",
                )
            }
        }

        if (offset + batchSize < allFlags.size) {
            server.scheduler.runTaskLater(
                this,
                Runnable { startupRepairBatch(allFlags, offset + batchSize, batchSize) },
                BATCH_DELAY_TICKS,
            )
        }
    }

    private fun extractStartupFlagOwnerUuid(flagId: String): UUID? {
        val uuidStr =
            when {
                flagId.startsWith("order/") -> flagId.removePrefix("order/")
                flagId.startsWith("front/") -> flagId.removePrefix("front/")
                else -> return null
            }
        return runCatching { UUID.fromString(uuidStr) }.getOrNull()
    }

    override fun onDisable() {
        container.homeTimerManager.onDisable()
        logger.info("☭ Плагин деактивирован. До встречи на собрании, товарищ!")
    }
}
