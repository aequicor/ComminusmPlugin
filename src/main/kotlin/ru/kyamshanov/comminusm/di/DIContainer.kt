@file:Suppress("TooManyFunctions", "LongParameterList")

package ru.kyamshanov.comminusm.di

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.application.usecases.commune.BroadcastToCommuneUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.commune.CheckCommuneFriendlyFireUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.commune.GetToggleModeUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.commune.RecalculateCrossOrderRightsUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.commune.RemoveOrderFromCommuneWithCascadeUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.ActivateOrderUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderOverlapUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.CreateOrderUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.DeleteOrderUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.FindOrdersInWorldUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.GetMaxOrderLevelUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.GetNativeOrdersOfPlayerUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByIdUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderCostForLevelUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.GetRadiusForLevelUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.order.UpgradeOrderUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.workdays.GetWorkdaysBalanceUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.workdays.IncrementWorkdaysUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.workdays.SpendWorkdaysUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.workfront.DeactivateWorkFrontUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.workfront.GetWorkFrontByOwnerUseCaseImpl
import ru.kyamshanov.comminusm.application.usecases.workfront.GetWorkFrontsInWorldUseCaseImpl
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
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
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
import ru.kyamshanov.comminusm.infrastructure.config.PluginConfig
import ru.kyamshanov.comminusm.infrastructure.repositories.CommuneRepositoryImpl
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

/**
 * DIContainer — centralized dependency instantiation for the entire plugin.
 * All "new" operations happen here, making dependencies explicit and testable.
 */
class DIContainer(
    private val plugin: JavaPlugin,
) {
    // ========== Core Infrastructure ==========
    private val database by lazy { DatabaseManager(plugin) }
    private val pluginConfig by lazy { PluginConfig(plugin.config) }
    private val chunkCache by lazy { ChunkCacheManager() }

    // ========== Managers & Helpers ==========
    val flagStabilityManager by lazy { FlagStabilityManager(plugin) }
    private val flagActivationHelper by lazy { FlagActivationHelper(plugin) }
    private val flagCleanupHelper by lazy { FlagCleanupHelper(plugin) }

    // ========== Domain Repositories ==========
    private val orderRepository: OrderRepository by lazy {
        OrderRepositoryImpl(database.connection)
    }
    private val workFrontRepository: WorkFrontRepository by lazy {
        WorkFrontRepositoryImpl(database.connection)
    }
    private val workdaysRepository: WorkdaysRepository by lazy {
        WorkdaysRepositoryImpl(database.connection)
    }

    // Note: communeRepository is kept for potential future use patterns
    @Suppress("UnusedPrivateProperty")
    private val communeRepository: CommuneRepository by lazy {
        CommuneRepositoryImpl(communeEntities)
    }

    // ========== Commune Domain Models (In-Memory) ==========
    @Suppress("MaxLineLength")
    private val communeModels by lazy {
        ConcurrentHashMap<UUID, ru.kyamshanov.comminusm.commune.model.Commune>()
    }

    private val orderToCommuneId by lazy { ConcurrentHashMap<Long, UUID>() }

    @Suppress("MaxLineLength")
    private val invitations by lazy {
        ConcurrentHashMap<UUID, ru.kyamshanov.comminusm.commune.model.CommuneInvitation>()
    }

    private val invitationTimers by lazy { ConcurrentHashMap<UUID, Any>() }

    @Suppress("MaxLineLength")
    private val communeEntities by lazy {
        ConcurrentHashMap<UUID, ru.kyamshanov.comminusm.domain.entities.Commune>()
    }

    // ========== Order Use Cases ==========
    val createOrderUseCase by lazy {
        CreateOrderUseCaseImpl(orderRepository, pluginConfig.orderLevels)
    }

    val checkOrderOverlapUseCase by lazy {
        CheckOrderOverlapUseCaseImpl(orderRepository)
    }

    val activateOrderUseCase by lazy {
        ActivateOrderUseCaseImpl(orderRepository, checkOrderOverlapUseCase)
    }

    val upgradeOrderUseCase by lazy {
        UpgradeOrderUseCaseImpl(orderRepository, workdaysRepository, pluginConfig.orderLevels)
    }

    val deleteOrderUseCase by lazy {
        DeleteOrderUseCaseImpl(orderRepository)
    }

    val getOrderByOwnerUseCase by lazy {
        GetOrderByOwnerUseCaseImpl(orderRepository)
    }

    val getOrderByIdUseCase by lazy {
        GetOrderByIdUseCaseImpl(orderRepository)
    }

    val findOrdersInWorldUseCase by lazy {
        FindOrdersInWorldUseCaseImpl(orderRepository)
    }

    val getNativeOrdersOfPlayerUseCase by lazy {
        GetNativeOrdersOfPlayerUseCaseImpl(orderMembershipService)
    }

    val checkOrderLeadershipUseCase by lazy {
        CheckOrderLeadershipUseCaseImpl(orderRepository)
    }

    val getOrderCostForLevelUseCase by lazy {
        GetOrderCostForLevelUseCaseImpl(pluginConfig.orderLevels)
    }

    val getMaxOrderLevelUseCase by lazy {
        GetMaxOrderLevelUseCaseImpl(pluginConfig.orderLevels)
    }

    val getRadiusForLevelUseCase by lazy {
        GetRadiusForLevelUseCaseImpl(pluginConfig.orderLevels)
    }

    // ========== Commune Use Cases ==========
    val getToggleModeUseCase by lazy {
        GetToggleModeUseCaseImpl(communeChatService)
    }

    val getCommuneOfOrderUseCase by lazy {
        GetCommuneOfOrderUseCaseImpl(communeService)
    }

    val broadcastToCommuneUseCase by lazy {
        BroadcastToCommuneUseCaseImpl(communeChatService)
    }

    val removeOrderFromCommuneWithCascadeUseCase by lazy {
        RemoveOrderFromCommuneWithCascadeUseCaseImpl(communeService, crossOrderMembershipService)
    }

    val checkCommuneFriendlyFireUseCase by lazy {
        CheckCommuneFriendlyFireUseCaseImpl(communeService, orderMembershipService)
    }

    val recalculateCrossOrderRightsUseCase by lazy {
        RecalculateCrossOrderRightsUseCaseImpl(orderMembershipService)
    }

    // ========== Workdays Use Cases ==========
    val incrementWorkdaysUseCase by lazy {
        IncrementWorkdaysUseCaseImpl(workdaysRepository)
    }

    val spendWorkdaysUseCase by lazy {
        SpendWorkdaysUseCaseImpl(workdaysRepository)
    }

    val getWorkdaysBalanceUseCase by lazy {
        GetWorkdaysBalanceUseCaseImpl(workdaysRepository)
    }

    // ========== WorkFront Use Cases ==========
    val getWorkFrontByOwnerUseCase by lazy {
        GetWorkFrontByOwnerUseCaseImpl(workFrontRepository)
    }

    val getWorkFrontsInWorldUseCase by lazy {
        GetWorkFrontsInWorldUseCaseImpl(workFrontRepository)
    }

    val deactivateWorkFrontUseCase by lazy {
        DeactivateWorkFrontUseCaseImpl(workFrontService)
    }

    // ========== Services ==========
    val workdaysService by lazy { WorkdaysService(workdaysRepository) }

    val orderService by lazy {
        OrderService(
            orderRepository,
            pluginConfig.orderLevels,
            workdaysService,
            pluginConfig.minDistanceBetweenCenters,
            chunkCache,
            flagCleanupHelper,
            flagStabilityManager,
            plugin,
        )
    }

    val workFrontService by lazy {
        WorkFrontService(
            workFrontRepository,
            pluginConfig.frontRadius,
            chunkCache,
            plugin,
            flagCleanupHelper,
            flagStabilityManager,
        )
    }

    val orderFlagStabilityManager by lazy { OrderFlagStabilityManager(orderRepository, plugin.logger) }

    val homeTimerManager by lazy { HomeTimerManager(plugin, orderFlagStabilityManager) }

    // ========== Commune Services ==========
    private val orderMembersRepository by lazy {
        OrderMembersRepository(ConcurrentHashMap(), database.connection)
    }

    private val orderMembershipService by lazy {
        OrderMembershipService(orderMembersRepository)
    }

    @Suppress("MaxLineLength")
    val communeService by lazy {
        CommuneService(communeModels, orderToCommuneId)
    }

    private val communeInvitationService by lazy {
        CommuneInvitationService(invitations, invitationTimers)
    }

    private val crossOrderMembershipService by lazy {
        CrossOrderMembershipService(orderMembershipService)
    }

    val communeChatService by lazy {
        CommuneChatServiceImpl(communeService, orderMembershipService)
    }

    val communeStartupTask by lazy {
        CommuneStartupTask(communeService, orderMembersRepository, database.connection, plugin)
    }

    // ========== Menu Creation (lazy properties for dependency injection) ==========
    val orderMenu by lazy {
        OrderMenu(
            getMaxOrderLevelUseCase,
            getOrderCostForLevelUseCase,
            getRadiusForLevelUseCase,
            getWorkdaysBalanceUseCase,
            getOrderByOwnerUseCase,
            upgradeOrderUseCase,
            getWorkFrontByOwnerUseCase,
            pluginConfig,
            orderService,
            workFrontService,
            homeTimerManager,
            orderFlagStabilityManager,
            plugin,
        )
    }

    val frontMenu by lazy {
        FrontMenu(
            getWorkFrontByOwnerUseCase,
            deactivateWorkFrontUseCase,
        )
    }

    val treasuryMenu by lazy {
        TreasuryMenu(
            pluginConfig,
            incrementWorkdaysUseCase,
            getWorkdaysBalanceUseCase,
        )
    }

    val adminMenu by lazy {
        AdminMenu(
            findOrdersInWorldUseCase,
            getWorkFrontsInWorldUseCase,
            deleteOrderUseCase,
            deactivateWorkFrontUseCase,
        )
    }

    // ========== Listener Creation ==========
    fun createListeners(): List<Listener> =
        listOf(
            PlayerJoinHandler(),
            PlayerListener(workdaysService, pluginConfig),
            OrderFlagListener(
                orderService,
                pluginConfig,
                orderMenu,
                plugin,
                flagActivationHelper,
                flagStabilityManager,
            ),
            BlockListener(getOrderByOwnerUseCase, findOrdersInWorldUseCase, workFrontService),
            ExplosionListener(orderService, workFrontService, flagStabilityManager),
            FrontFlagListener(
                workFrontService,
                orderService,
                plugin,
                flagActivationHelper,
                flagCleanupHelper,
                flagStabilityManager,
                pluginConfig,
                frontMenu,
            ),
            FlagDeletionConfirmListener(orderService),
            FlagItemProtectionListener(),
            FlagProtectionListener(flagStabilityManager),
            FlagChunkListener(plugin, flagStabilityManager, orderRepository, workFrontRepository),
            HomeTimerCancelListener(homeTimerManager),
            OrderRespawnListener(orderFlagStabilityManager, plugin.logger, getOrderByOwnerUseCase),
            FlagEventListener(homeTimerManager),
            CommuneOrderDestroyListener(removeOrderFromCommuneWithCascadeUseCase),
            CommuneMembershipListener(getCommuneOfOrderUseCase, recalculateCrossOrderRightsUseCase),
            CommunePlayerListener(getOrderByOwnerUseCase, getCommuneOfOrderUseCase),
            FriendlyFireListener(checkCommuneFriendlyFireUseCase),
            AsyncChatEventListener(
                getToggleModeUseCase,
                getOrderByOwnerUseCase,
                getCommuneOfOrderUseCase,
                broadcastToCommuneUseCase,
            ),
        )

    // ========== Menu Creation ==========
    fun createMenus(): List<Listener> =
        listOf(
            PartyMenu(
                pluginConfig,
                getWorkdaysBalanceUseCase,
                getOrderByOwnerUseCase,
                getWorkFrontByOwnerUseCase,
                orderService,
                workFrontService,
                plugin,
                orderMenu,
                frontMenu,
                treasuryMenu,
            ),
            orderMenu,
            frontMenu,
            treasuryMenu,
            adminMenu,
            CommunePartyMenu(checkOrderLeadershipUseCase),
            CommuneOrderMenu(checkOrderLeadershipUseCase, orderMembershipService),
            CommuneMenu(
                communeService,
                checkOrderLeadershipUseCase,
                getOrderByIdUseCase,
                communeInvitationService,
            ),
            OrderMembersMenu(checkOrderLeadershipUseCase, orderMembershipService),
        )

    // ========== Command Creation ==========
    fun createCommands(): List<Pair<String, org.bukkit.command.CommandExecutor>> =
        listOf(
            "party" to
                PartyCommand(
                    pluginConfig,
                    getWorkdaysBalanceUseCase,
                    getOrderByOwnerUseCase,
                    getWorkFrontByOwnerUseCase,
                    orderService,
                    workFrontService,
                    plugin,
                    adminMenu,
                ),
            "cc" to
                CommuneCommand(
                    getNativeOrdersOfPlayerUseCase,
                    getCommuneOfOrderUseCase,
                    getToggleModeUseCase,
                    broadcastToCommuneUseCase,
                    communeChatService,
                    null,
                ),
            "order" to
                DelegatingCommandExecutor(
                    null,
                    OrderCommuneInfoCommand(orderRepository, getCommuneOfOrderUseCase, communeService),
                ),
        )

    // ========== Public Repository Accessors (for startup operations) ==========

    /**
     * Public accessor for OrderRepository.
     * Used by startup repair scan and other initialization tasks.
     */
    fun provideOrderRepository(): OrderRepository = orderRepository

    /**
     * Public accessor for WorkFrontRepository.
     * Used by startup repair scan and other initialization tasks.
     */
    fun provideWorkFrontRepository(): WorkFrontRepository = workFrontRepository
}
