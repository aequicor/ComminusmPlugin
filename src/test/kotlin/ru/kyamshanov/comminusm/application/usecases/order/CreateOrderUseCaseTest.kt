package ru.kyamshanov.comminusm.application.usecases.order

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID

class CreateOrderUseCaseTest {
    private lateinit var createOrderUseCase: CreateOrderUseCase
    private lateinit var mockRepository: MockOrderRepository

    @BeforeEach
    fun setUp() {
        mockRepository = MockOrderRepository()
        val levels =
            listOf(
                OrderLevelConfig(level = 1, radius = 2, cost = 0),
            )
        createOrderUseCase = CreateOrderUseCaseImpl(mockRepository, levels)
    }

    @Test
    fun `invoke creates order for new owner`() {
        val ownerUuid = UUID.randomUUID()

        val result = createOrderUseCase(ownerUuid)

        assertTrue(result is Result.Success)
        assertEquals(ownerUuid, (result as? Result.Success)?.data?.ownerUuid)
        assertEquals(1, (result as? Result.Success)?.data?.level)
        assertEquals(2, (result as? Result.Success)?.data?.radius)
    }

    @Test
    fun `invoke returns failure when order already exists`() {
        val ownerUuid = UUID.randomUUID()
        mockRepository.insert(Order(ownerUuid = ownerUuid, level = 1, radius = 2))

        val result = createOrderUseCase(ownerUuid)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `invoke returns failure when no level config`() {
        val ownerUuid = UUID.randomUUID()
        val emptyLevels = emptyList<OrderLevelConfig>()
        val useCase = CreateOrderUseCaseImpl(mockRepository, emptyLevels)

        val result = useCase(ownerUuid)

        assertTrue(result is Result.Failure)
    }

    private class MockOrderRepository : OrderRepository {
        private val orders = mutableMapOf<UUID, Order>()
        private var nextId = 1L

        override fun findByOwner(uuid: UUID): Order? = orders[uuid]

        override fun findById(id: Long): Order? = orders.values.find { it.id == id }

        override fun findAllInWorld(world: String): List<Order> = orders.values.filter { it.centerWorld == world }

        override fun findAllActivated(): List<Order> = orders.values.filter { it.isActivated() }

        override fun insert(order: Order): Long {
            val id = nextId++
            val withId = order.copy(id = id)
            orders[order.ownerUuid] = withId
            return id
        }

        override fun update(order: Order) {
            orders[order.ownerUuid] = order
        }

        override fun activate(
            uuid: UUID,
            world: String,
            x: Int,
            y: Int,
            z: Int,
        ) {
            val order = orders[uuid]
            if (order != null) {
                orders[uuid] = order.copy(centerWorld = world, centerX = x, centerY = y, centerZ = z)
            }
        }

        override fun updateLevel(
            uuid: UUID,
            level: Int,
            radius: Int,
        ) {
            val order = orders[uuid]
            if (order != null) {
                orders[uuid] = order.copy(level = level, radius = radius)
            }
        }

        override fun deleteByOwner(uuid: UUID) {
            orders.remove(uuid)
        }
    }
}
