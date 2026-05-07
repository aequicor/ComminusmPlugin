package ru.kyamshanov.comminusm.commune.listener

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CommunePendingNotificationService
import ru.kyamshanov.comminusm.domain.entities.Order
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunePlayerListenerTest {
    private lateinit var listener: CommunePlayerListener
    private lateinit var orderMembersRepository: OrderMembersRepository
    private lateinit var getOrderByOwnerUseCase: GetOrderByOwnerUseCase
    private lateinit var getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase
    private lateinit var pendingNotifications: CommunePendingNotificationService
    private val playerUuid = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        orderMembersRepository = OrderMembersRepository(ConcurrentHashMap(), connection = null)
        pendingNotifications = CommunePendingNotificationService()

        getOrderByOwnerUseCase =
            object : GetOrderByOwnerUseCase {
                override fun invoke(ownerUuid: UUID): Order? =
                    if (ownerUuid == playerUuid) {
                        Order(
                            id = 1L,
                            ownerUuid = playerUuid,
                            level = 1,
                            centerX = 0,
                            centerY = 0,
                            centerZ = 0,
                        )
                    } else {
                        null
                    }
            }

        getCommuneOfOrderUseCase =
            object : GetCommuneOfOrderUseCase {
                override fun invoke(orderId: Long): Commune? =
                    if (orderId == 1L) {
                        Commune(
                            id = UUID.randomUUID(),
                            orderIds = setOf(1L, 2L, 3L),
                            version = 1L,
                            createdAt = LocalDateTime.now(),
                            createdBy = UUID.randomUUID(),
                        )
                    } else {
                        null
                    }
            }

        listener =
            CommunePlayerListener(
                getOrderByOwnerUseCase,
                getCommuneOfOrderUseCase,
                orderMembersRepository,
                pendingNotifications,
                plugin = null, // Test mode: run synchronously
            )
    }

    @Test
    fun staleGrant_revokedWhenPlayerOrderNotInCommune() {
        // Setup: player has "commune" grant in orderId=10, but commune only has orders [1, 2, 3]
        orderMembersRepository.addMember(
            10L,
            playerUuid,
            "commune",
            LocalDateTime.now(),
        )

        assertTrue(orderMembersRepository.isMember(10L, playerUuid))

        listener.checkAndRevokeStaleGrants(playerUuid, setOf(1L, 2L, 3L))

        assertFalse(orderMembersRepository.isMember(10L, playerUuid))
    }

    @Test
    fun validGrant_keptWhenOrderInCommune() {
        // Setup: player has "commune" grant in orderId=10, and commune has order 10
        orderMembersRepository.addMember(
            10L,
            playerUuid,
            "commune",
            LocalDateTime.now(),
        )

        listener.checkAndRevokeStaleGrants(playerUuid, setOf(10L))

        assertTrue(orderMembersRepository.isMember(10L, playerUuid))
    }

    @Test
    fun noGrants_noSideEffects() {
        // Setup: player has no grants
        // This should not throw and repository should remain unchanged
        listener.checkAndRevokeStaleGrants(playerUuid, emptySet())

        assertFalse(orderMembersRepository.isMember(10L, playerUuid))
    }

    @Test
    fun multipleStaleGrants_allRevoked() {
        // Setup: player has "commune" grants in orders 10, 11, 12
        // Commune only has order 11
        orderMembersRepository.addMember(10L, playerUuid, "commune", LocalDateTime.now())
        orderMembersRepository.addMember(11L, playerUuid, "commune", LocalDateTime.now())
        orderMembersRepository.addMember(12L, playerUuid, "commune", LocalDateTime.now())

        listener.checkAndRevokeStaleGrants(playerUuid, setOf(11L))

        assertFalse(orderMembersRepository.isMember(10L, playerUuid))
        assertTrue(orderMembersRepository.isMember(11L, playerUuid))
        assertFalse(orderMembersRepository.isMember(12L, playerUuid))
    }

    @Test
    fun nativeGrant_notRevoked() {
        // Setup: player has "native" grant in orderId=10
        // "native" grants should not be touched by commune-type filter
        orderMembersRepository.addMember(10L, playerUuid, "native", LocalDateTime.now())

        listener.checkAndRevokeStaleGrants(playerUuid, emptySet())

        assertTrue(orderMembersRepository.isMember(10L, playerUuid))
    }
}
