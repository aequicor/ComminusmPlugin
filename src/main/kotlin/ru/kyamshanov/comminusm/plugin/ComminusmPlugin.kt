package ru.kyamshanov.comminusm.plugin

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.command.CommuneCommand
import ru.kyamshanov.comminusm.command.DelegatingCommandExecutor
import ru.kyamshanov.comminusm.command.OrderCommuneInfoCommand
import ru.kyamshanov.comminusm.command.PartyCommand
import ru.kyamshanov.comminusm.commune.listener.AsyncChatEventListener
import ru.kyamshanov.comminusm.commune.listener.CommuneMembershipListener
import ru.kyamshanov.comminusm.commune.listener.CommuneOrderDestroyListener
import ru.kyamshanov.comminusm.commune.listener.CommunePlayerListener
import ru.kyamshanov.comminusm.commune.listener.CommuneStartupTask
import ru.kyamshanov.comminusm.commune.listener.FriendlyFireListener
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CommuneChatServiceImpl
import ru.kyamshanov.comminusm.commune.service.CommuneInvitationService
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.event.PlayerJoinHandler
import ru.kyamshanov.comminusm.gui.AdminMenu
import ru.kyamshanov.comminusm.gui.CommuneMenu
import ru.kyamshanov.comminusm.gui.CommuneOrderMenu
import ru.kyamshanov.comminusm.gui.CommunePartyMenu
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.gui.OrderMembersMenu
import ru.kyamshanov.comminusm.gui.OrderMenu
import ru.kyamshanov.comminusm.gui.PartyMenu
import ru.kyamshanov.comminusm.gui.TreasuryMenu
import ru.kyamshanov.comminusm.infrastructure.repositories.OrderRepositoryImpl
import ru.kyamshanov.comminusm.infrastructure.repositories.WorkFrontRepositoryImpl
import ru.kyamshanov.comminusm.infrastructure.repositories.WorkdaysRepositoryImpl
import ru.kyamshanov.comminusm.listener.BlockListener
import ru.kyamshanov.comminusm.listener.ExplosionListener
import ru.kyamshanov.comminusm.listener.FlagChunkListener
import ru.kyamshanov.comminusm.listener.FlagDeletionConfirmListener
import ru.kyamshanov.comminusm.listener.FlagEventListener
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.listener.FlagProtectionListener
import ru.kyamshanov.comminusm.listener.FrontFlagListener
import ru.kyamshanov.comminusm.listener.HomeTimerCancelListener
import ru.kyamshanov.comminusm.listener.OrderFlagListener
import ru.kyamshanov.comminusm.listener.OrderRespawnListener
import ru.kyamshanov.comminusm.listener.PlayerListener
import ru.kyamshanov.comminusm.manager.FlagActivationHelper
import ru.kyamshanov.comminusm.manager.FlagCleanupHelper
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.service.HomeTimerManager
import ru.kyamshanov.comminusm.service.OrderFlagStabilityManager
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
import ru.kyamshanov.comminusm.storage.DatabaseManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("TooManyFunctions")
class ComminusmPlugin : JavaPlugin() {
    lateinit var flagStabilityManager: FlagStabilityManager
    private var homeTimerManager: HomeTimerManager? = null

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

    private data class FlagListenersDependencies(
        val orderService: OrderService,
        val workFrontService: WorkFrontService,
        val pluginConfig: PluginConfig,
        val workdaysService: WorkdaysService,
        val flagActivationHelper: FlagActivationHelper,
        val flagCleanupHelper: FlagCleanupHelper,
        val orderRepo: OrderRepository,
        val frontRepo: WorkFrontRepository,
    )

    private data class MenuDependencies(
        val orderService: OrderService,
        val workdaysService: WorkdaysService,
        val pluginConfig: PluginConfig,
        val workFrontService: WorkFrontService,
        val htManager: HomeTimerManager,
        val orderFlagStabilityManager: OrderFlagStabilityManager,
    )

    private data class ListenerDependencies(
        val orderService: OrderService,
        val workFrontService: WorkFrontService,
        val pluginConfig: PluginConfig,
        val workdaysService: WorkdaysService,
        val flagActivationHelper: FlagActivationHelper,
        val flagCleanupHelper: FlagCleanupHelper,
        val orderRepo: OrderRepository,
        val frontRepo: WorkFrontRepository,
    )

    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        flagStabilityManager = FlagStabilityManager(this)
        val flagActivationHelper = FlagActivationHelper(this)
        val flagCleanupHelper = FlagCleanupHelper(this)

        val db = initializePersistence()
        val services = initializeServicesAndRepos(db, flagCleanupHelper)

        registerAllListeners(
            ListenerDependencies(
                services.orderService,
                services.workFrontService,
                services.pluginConfig,
                services.workdaysService,
                flagActivationHelper,
                flagCleanupHelper,
                services.orderRepo,
                services.frontRepo,
            ),
        )

        val orderFlagStabilityManager = OrderFlagStabilityManager(services.orderRepo, logger)
        val htManager = HomeTimerManager(this, orderFlagStabilityManager)
        homeTimerManager = htManager
        registerMenusAndHomeTimer(
            MenuDependencies(
                services.orderService,
                services.workdaysService,
                services.pluginConfig,
                services.workFrontService,
                htManager,
                orderFlagStabilityManager,
            ),
        )

        val communeResources = wireCommuneSystem(services.orderService, services.orderRepo)
        registerCommands(
            services.pluginConfig,
            services.workdaysService,
            services.orderService,
            services.workFrontService,
        )

        logger.info("☭ Плагин активирован! Трудодни начисляются, Ордера выдаются.")
        startupRepairScan(services.orderRepo, services.frontRepo, services.pluginConfig.flagStartupScanBatchSize)
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                communeResources.communeStartupTask.onEnable()
            },
        )
    }

    private fun initializePersistence(): DatabaseManager {
        val db = DatabaseManager(this)
        if (!db.integrityCheck()) {
            logger.severe("☭ БАЗА ДАННЫХ ПОВРЕЖДЕНА! Плагин отключён.")
            server.pluginManager.disablePlugin(this)
            error("Database integrity check failed")
        }
        return db
    }

    private data class InitializedServices(
        val orderService: OrderService,
        val workFrontService: WorkFrontService,
        val orderRepo: OrderRepository,
        val frontRepo: WorkFrontRepository,
        val pluginConfig: PluginConfig,
        val workdaysService: WorkdaysService,
        val chunkCache: ChunkCacheManager,
    )

    private fun initializeServicesAndRepos(
        db: DatabaseManager,
        flagCleanupHelper: FlagCleanupHelper,
    ): InitializedServices {
        val pluginConfig = PluginConfig(config)
        val chunkCache = ChunkCacheManager()
        val orderRepo: OrderRepository = OrderRepositoryImpl(db.connection)
        val frontRepo: WorkFrontRepository = WorkFrontRepositoryImpl(db.connection)
        val workdaysRepository: WorkdaysRepository = WorkdaysRepositoryImpl(db.connection)
        val workdaysService = WorkdaysService(workdaysRepository)
        val orderService =
            initializeOrderService(
                orderRepo,
                pluginConfig,
                chunkCache,
                workdaysService,
                flagCleanupHelper,
            )
        val workFrontService =
            WorkFrontService(
                frontRepo,
                pluginConfig.frontRadius,
                chunkCache,
                this,
                flagCleanupHelper,
                flagStabilityManager,
            )
        return InitializedServices(
            orderService,
            workFrontService,
            orderRepo,
            frontRepo,
            pluginConfig,
            workdaysService,
            chunkCache,
        )
    }

    private fun registerAllListeners(deps: ListenerDependencies) {
        wireFlagListeners(
            FlagListenersDependencies(
                deps.orderService,
                deps.workFrontService,
                deps.pluginConfig,
                deps.workdaysService,
                deps.flagActivationHelper,
                deps.flagCleanupHelper,
                deps.orderRepo,
                deps.frontRepo,
            ),
        )
    }

    private fun registerMenusAndHomeTimer(deps: MenuDependencies) {
        wireHomeTimerListeners(deps.htManager, deps.orderService, deps.orderFlagStabilityManager)
        wireMenus(deps)
    }

    private fun registerCommands(
        pluginConfig: PluginConfig,
        workdaysService: WorkdaysService,
        orderService: OrderService,
        workFrontService: WorkFrontService,
    ) {
        val partyCmd =
            checkNotNull(getCommand("party")) {
                "Команда 'party' не объявлена в plugin.yml"
            }
        partyCmd.setExecutor(PartyCommand(pluginConfig, workdaysService, orderService, workFrontService))
    }

    private fun initializeOrderService(
        orderRepo: OrderRepository,
        pluginConfig: PluginConfig,
        chunkCache: ChunkCacheManager,
        workdaysService: WorkdaysService,
        flagCleanupHelper: FlagCleanupHelper,
    ): OrderService =
        OrderService(
            orderRepo,
            pluginConfig.orderLevels,
            workdaysService,
            pluginConfig.minDistanceBetweenCenters,
            chunkCache,
            flagCleanupHelper,
            flagStabilityManager,
            this,
        )

    private fun wireFlagListeners(deps: FlagListenersDependencies) {
        server.pluginManager.registerEvents(PlayerJoinHandler(), this)
        server.pluginManager.registerEvents(PlayerListener(deps.workdaysService, deps.pluginConfig), this)
        server.pluginManager.registerEvents(
            OrderFlagListener(
                deps.orderService,
                deps.workdaysService,
                deps.pluginConfig,
                deps.workFrontService,
                this,
                deps.flagActivationHelper,
                flagStabilityManager,
            ),
            this,
        )
        server.pluginManager.registerEvents(
            BlockListener(deps.orderService, deps.workFrontService),
            this,
        )
        server.pluginManager.registerEvents(
            ExplosionListener(deps.orderService, deps.workFrontService, flagStabilityManager),
            this,
        )
        server.pluginManager.registerEvents(
            FrontFlagListener(
                deps.workFrontService,
                deps.orderService,
                this,
                deps.flagActivationHelper,
                deps.flagCleanupHelper,
                flagStabilityManager,
                deps.pluginConfig,
            ),
            this,
        )
        server.pluginManager.registerEvents(
            FlagDeletionConfirmListener(deps.orderService),
            this,
        )
        server.pluginManager.registerEvents(FlagItemProtectionListener(), this)
        server.pluginManager.registerEvents(FlagProtectionListener(flagStabilityManager), this)
        server.pluginManager.registerEvents(
            FlagChunkListener(this, flagStabilityManager, deps.orderRepo, deps.frontRepo),
            this,
        )
    }

    private fun wireHomeTimerListeners(
        htManager: HomeTimerManager,
        orderService: OrderService,
        orderFlagStabilityManager: OrderFlagStabilityManager,
    ) {
        server.pluginManager.registerEvents(HomeTimerCancelListener(htManager), this)
        server.pluginManager.registerEvents(
            OrderRespawnListener(orderService, orderFlagStabilityManager, logger),
            this,
        )
        server.pluginManager.registerEvents(FlagEventListener(htManager), this)
    }

    private fun wireMenus(deps: MenuDependencies) {
        val orderMenu =
            OrderMenu(
                deps.orderService,
                deps.workdaysService,
                deps.pluginConfig,
                deps.workFrontService,
                deps.htManager,
                deps.orderFlagStabilityManager,
                this,
            )
        server.pluginManager.registerEvents(
            PartyMenu(
                deps.pluginConfig,
                deps.workdaysService,
                deps.orderService,
                deps.workFrontService,
                this,
                orderMenu,
            ),
            this,
        )
        server.pluginManager.registerEvents(orderMenu, this)
        server.pluginManager.registerEvents(FrontMenu(deps.workFrontService), this)
        server.pluginManager.registerEvents(TreasuryMenu(deps.pluginConfig, deps.workdaysService), this)
        server.pluginManager.registerEvents(
            AdminMenu(deps.orderService, deps.workFrontService),
            this,
        )
    }

    private data class CommuneResources(
        val communeStartupTask: CommuneStartupTask,
    )

    private fun wireCommuneSystem(
        orderService: OrderService,
        orderRepo: OrderRepository,
    ): CommuneResources {
        // In-memory caches for communes
        val communes = ConcurrentHashMap<UUID, ru.kyamshanov.comminusm.commune.model.Commune>()
        val orderToCommuneId = ConcurrentHashMap<Long, UUID>()
        val invitations = ConcurrentHashMap<UUID, ru.kyamshanov.comminusm.commune.model.CommuneInvitation>()
        val invitationTimers = ConcurrentHashMap<UUID, Any>()

        // Repositories & Services
        val orderMembersRepository = OrderMembersRepository(ConcurrentHashMap())
        val orderMembershipService = OrderMembershipService(orderMembersRepository)
        val communeService = CommuneService(communes, orderToCommuneId)
        val communeInvitationService = CommuneInvitationService(invitations, invitationTimers)
        val crossOrderMembershipService = CrossOrderMembershipService(orderMembershipService)
        val communeChatService = CommuneChatServiceImpl(communeService, orderMembershipService)

        // Startup task for commune initialization
        val communeStartupTask = CommuneStartupTask(communeService, this)

        // Listeners - Stage 06
        server.pluginManager.registerEvents(
            CommuneOrderDestroyListener(communeService, crossOrderMembershipService),
            this,
        )
        server.pluginManager.registerEvents(
            CommuneMembershipListener(communeService, orderMembershipService),
            this,
        )
        server.pluginManager.registerEvents(
            CommunePlayerListener(communeService, orderService),
            this,
        )
        server.pluginManager.registerEvents(
            FriendlyFireListener(communeService, orderMembershipService),
            this,
        )

        // Chat system
        server.pluginManager.registerEvents(
            AsyncChatEventListener(communeChatService),
            this,
        )

        // Commands
        getCommand("cc")?.setExecutor(
            CommuneCommand(communeService, orderMembershipService, communeChatService, null),
        )

        getCommand("order")?.let { orderCmd ->
            val existingExecutor = orderCmd.executor
            orderCmd.setExecutor(
                DelegatingCommandExecutor(
                    existingExecutor,
                    OrderCommuneInfoCommand(orderRepo, communeService),
                ),
            )
        }

        // Menus - Stage 06 Decorators
        val communePartyMenu = CommunePartyMenu(communeService, orderService)
        server.pluginManager.registerEvents(communePartyMenu, this)

        val communeOrderMenu = CommuneOrderMenu(orderService, orderMembershipService)
        server.pluginManager.registerEvents(communeOrderMenu, this)

        server.pluginManager.registerEvents(
            CommuneMenu(communeService, orderService, communeInvitationService, orderMembershipService),
            this,
        )

        server.pluginManager.registerEvents(
            OrderMembersMenu(orderMembershipService, orderService),
            this,
        )

        return CommuneResources(communeStartupTask)
    }

    private fun startupRepairScan(
        orderRepo: OrderRepository,
        frontRepo: WorkFrontRepository,
        batchSize: Int,
    ) {
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                val allFlags =
                    buildList<FlagEntry> {
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
        val uuidStr =
            when {
                flagId.startsWith("order/") -> flagId.removePrefix("order/")
                flagId.startsWith("front/") -> flagId.removePrefix("front/")
                else -> return null
            }
        return runCatching { UUID.fromString(uuidStr) }.getOrNull()
    }

    override fun onDisable() {
        homeTimerManager?.onDisable()
        logger.info("☭ Плагин деактивирован. До встречи на собрании, товарищ!")
    }
}
