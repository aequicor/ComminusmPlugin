package ru.kyamshanov.comminusm.plugin

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.command.PartyCommand
import ru.kyamshanov.comminusm.manager.FlagActivationHelper
import ru.kyamshanov.comminusm.manager.FlagCleanupHelper
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.event.PlayerJoinHandler
import ru.kyamshanov.comminusm.gui.AdminMenu
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.gui.OrderMenu
import ru.kyamshanov.comminusm.gui.PartyMenu
import ru.kyamshanov.comminusm.gui.TreasuryMenu
import ru.kyamshanov.comminusm.listener.BlockListener
import ru.kyamshanov.comminusm.listener.ExplosionListener
import ru.kyamshanov.comminusm.listener.FlagChunkListener
import ru.kyamshanov.comminusm.listener.FlagDeletionConfirmListener
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.listener.FlagProtectionListener
import ru.kyamshanov.comminusm.listener.FrontFlagListener
import ru.kyamshanov.comminusm.listener.OrderFlagListener
import ru.kyamshanov.comminusm.listener.PlayerListener
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.OrderRepository
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import ru.kyamshanov.comminusm.storage.WorkdaysRepository
import java.util.UUID

class ComminusmPlugin : JavaPlugin() {

    lateinit var flagStabilityManager: FlagStabilityManager

    companion object {
        private lateinit var INSTANCE: ComminusmPlugin
        fun getInstance() = INSTANCE

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
        INSTANCE = this
        flagStabilityManager = FlagStabilityManager(this)
        val flagActivationHelper = FlagActivationHelper(this)
        val flagCleanupHelper = FlagCleanupHelper(this)

        // Save default config
        saveDefaultConfig()

        // Database
        val db = DatabaseManager(this)
        if (!db.integrityCheck()) {
            logger.severe("☭ БАЗА ДАННЫХ ПОВРЕЖДЕНА! Плагин отключён.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Config
        val pluginConfig = PluginConfig(config)

        // Repositories
        val orderRepo = OrderRepository(db.connection)
        val frontRepo = WorkFrontRepository(db.connection)
        val workdaysRepo = WorkdaysRepository(db.connection)

        // Services
        val chunkCache = ChunkCacheManager()
        val workdaysService = WorkdaysService(workdaysRepo)
        val orderService = OrderService(orderRepo, pluginConfig.orderLevels, workdaysService, pluginConfig.minDistanceBetweenCenters, chunkCache, flagCleanupHelper, flagStabilityManager)
        val workFrontService = WorkFrontService(frontRepo, pluginConfig.frontRadius, chunkCache, this, flagCleanupHelper, flagStabilityManager)

        // Register listeners
        server.pluginManager.registerEvents(PlayerJoinHandler(), this)
        server.pluginManager.registerEvents(PlayerListener(workdaysService, pluginConfig), this)

        // Register order flag listener
        server.pluginManager.registerEvents(OrderFlagListener(orderService, workdaysService, pluginConfig, workFrontService, this, flagActivationHelper, flagStabilityManager), this)

        // Register block protection
        server.pluginManager.registerEvents(BlockListener(orderService, workFrontService, flagStabilityManager), this)

        // Register explosion protection for flags
        server.pluginManager.registerEvents(ExplosionListener(orderService, workFrontService, flagStabilityManager), this)

        // Register front flag listener
        server.pluginManager.registerEvents(FrontFlagListener(workFrontService, orderService, this, flagActivationHelper, flagCleanupHelper, flagStabilityManager, pluginConfig), this)

        // Register flag deletion confirmation listener
        server.pluginManager.registerEvents(FlagDeletionConfirmListener(orderService, workFrontService), this)

        // Flag item protection (no dropping, no chesting)
        server.pluginManager.registerEvents(FlagItemProtectionListener(), this)

        // Indirect destruction protection (pistons, water flow, entity block changes)
        server.pluginManager.registerEvents(FlagProtectionListener(flagStabilityManager), this)

        // Chunk load/unload: crash recovery, passive verification, dirty_armorstand cleanup
        server.pluginManager.registerEvents(
            FlagChunkListener(this, flagStabilityManager, orderRepo, frontRepo),
            this,
        )

        // Register GUI listeners
        server.pluginManager.registerEvents(PartyMenu(pluginConfig, workdaysService, orderService, workFrontService, this), this)
        server.pluginManager.registerEvents(OrderMenu(orderService, workdaysService, pluginConfig, workFrontService), this)
        server.pluginManager.registerEvents(FrontMenu(workFrontService), this)
        server.pluginManager.registerEvents(TreasuryMenu(pluginConfig, workdaysService), this)
        server.pluginManager.registerEvents(AdminMenu(pluginConfig, orderService, workFrontService, workdaysService), this)

        // Register command
        val partyCmd = checkNotNull(getCommand("party")) { "Команда 'party' не объявлена в plugin.yml" }
        partyCmd.setExecutor(PartyCommand(pluginConfig, workdaysService, orderService, workFrontService))

        logger.info("☭ Плагин активирован! Трудодни начисляются, Ордера выдаются.")

        startupRepairScan(orderRepo, frontRepo, pluginConfig.flagStartupScanBatchSize)
    }

    private fun startupRepairScan(
        orderRepo: OrderRepository,
        frontRepo: WorkFrontRepository,
        batchSize: Int,
    ) {
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                val allFlags = buildList<FlagEntry> {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        orderRepo.findAllActivated().forEach { o ->
                            val world = o.centerWorld
                            if (world != null) {
                                add(FlagEntry("order/${o.ownerUuid}", world, o.centerX, o.centerY, o.centerZ))
                            }
                        }
                    } catch (e: Exception) {
                        logger.warning("Startup repair: DB error reading orders — ${e.message}")
                    }
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        frontRepo.findAllActivated().forEach { f ->
                            add(FlagEntry("front/${f.ownerUuid}", f.centerWorld, f.centerX, f.centerY, f.centerZ))
                        }
                    } catch (e: Exception) {
                        logger.warning("Startup repair: DB error reading fronts — ${e.message}")
                    }
                }
                if (allFlags.size > STARTUP_SCAN_WARN_THRESHOLD) {
                    logger.warning("Startup repair scan: processing ${allFlags.size} flags — this may take a moment")
                }
                server.scheduler.runTask(
                    this,
                    Runnable { startupRepairBatch(allFlags, 0, batchSize) },
                )
            },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun startupRepairBatch(allFlags: List<FlagEntry>, offset: Int, batchSize: Int) {
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

            // Double-spawn guard: check if ArmorStand already exists
            val existingUuidStr = pdc.get(asKey, PersistentDataType.STRING)
            if (existingUuidStr != null) {
                val existingUuid = runCatching { UUID.fromString(existingUuidStr) }.getOrNull()
                if (existingUuid != null && world.getEntity(existingUuid) != null) {
                    continue
                }
            }

            // Confirm this flag's PDC entry exists (flag was truly activated)
            val flagKey = NamespacedKey(this, "flag/${entry.flagId}")
            val coords = pdc.get(flagKey, PersistentDataType.LONG_ARRAY)
            if (coords == null || coords.size != COORD_ARRAY_SIZE) continue

            // ArmorStand missing — recreate
            val bannerBlock = world.getBlockAt(entry.bx, entry.by, entry.bz)
            val ownerUuid = extractStartupFlagOwnerUuid(entry.flagId) ?: continue
            val ownerName = runCatching { Bukkit.getOfflinePlayer(ownerUuid).name }.getOrNull() ?: ownerUuid.toString()
            val flagType = if (entry.flagId.startsWith("order/")) "Ордер" else "Трудовой Фронт"
            val asLocation = bannerBlock.location.clone().add(AS_OFFSET_XZ, AS_OFFSET_Y, AS_OFFSET_XZ)

            @Suppress("TooGenericExceptionCaught")
            try {
                val armorStand = world.spawn(asLocation, ArmorStand::class.java) { stand ->
                    stand.setVisible(false)
                    stand.setGravity(false)
                    stand.setMarker(true)
                    stand.customName(Component.text("§6$flagType — §f$ownerName"))
                    stand.isCustomNameVisible = true
                }
                pdc.set(asKey, PersistentDataType.STRING, armorStand.uniqueId.toString())
            } catch (e: Exception) {
                logger.severe("Startup repair: ArmorStand spawn failed for ${entry.flagId}: ${e.message}")
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
        val uuidStr = when {
            flagId.startsWith("order/") -> flagId.removePrefix("order/")
            flagId.startsWith("front/") -> flagId.removePrefix("front/")
            else -> return null
        }
        return runCatching { UUID.fromString(uuidStr) }.getOrNull()
    }

    override fun onDisable() {
        logger.info("☭ Плагин деактивирован. До встречи на собрании, товарищ!")
    }
}
