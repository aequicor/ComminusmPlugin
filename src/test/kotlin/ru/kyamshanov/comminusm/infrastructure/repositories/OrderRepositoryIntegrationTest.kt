package ru.kyamshanov.comminusm.infrastructure.repositories

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OrderRepositoryIntegrationTest {
    private val orderRepository = mockk<OrderRepository>()
    private lateinit var ownerUuid: UUID

    @BeforeEach
    fun setUp() {
        ownerUuid = UUID.randomUUID()
    }

    @Test
    fun `should find order by owner when exists`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = ownerUuid, level = 1, radius = 50)
        every { orderRepository.findByOwner(ownerUuid) } returns order

        // Act
        val found = orderRepository.findByOwner(ownerUuid)

        // Assert
        assertNotNull(found)
        assertEquals(ownerUuid, found?.ownerUuid)
    }

    @Test
    fun `should return null for non-existent owner`() {
        // Arrange
        every { orderRepository.findByOwner(any()) } returns null

        // Act
        val found = orderRepository.findByOwner(UUID.randomUUID())

        // Assert
        assertNull(found)
    }

    @Test
    fun `should find activated orders`() {
        // Arrange
        val order1 = Order(id = 1, ownerUuid = UUID.randomUUID(), level = 1, radius = 50)
        val order2 = Order(id = 2, ownerUuid = UUID.randomUUID(), level = 1, radius = 50, centerWorld = "world")
        every { orderRepository.findAllActivated() } returns listOf(order2)

        // Act
        val activated = orderRepository.findAllActivated()

        // Assert
        assertEquals(1, activated.size)
        assertEquals("world", activated[0].centerWorld)
    }

    @Test
    fun `should find orders in world`() {
        // Arrange
        val order1 = Order(id = 1, ownerUuid = UUID.randomUUID(), level = 1, radius = 50, centerWorld = "world")
        val order2 = Order(id = 2, ownerUuid = UUID.randomUUID(), level = 1, radius = 50, centerWorld = "nether")
        every { orderRepository.findAllInWorld("world") } returns listOf(order1)
        every { orderRepository.findAllInWorld("nether") } returns listOf(order2)

        // Act
        val worldOrders = orderRepository.findAllInWorld("world")
        val netherOrders = orderRepository.findAllInWorld("nether")

        // Assert
        assertEquals(1, worldOrders.size)
        assertEquals(1, netherOrders.size)
    }
}
