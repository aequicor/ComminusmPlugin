package ru.kyamshanov.comminusm.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.infrastructure.config.PluginConfig
import ru.kyamshanov.comminusm.infrastructure.repositories.OrderRepositoryImpl
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.storage.DatabaseManager
import java.util.UUID

class OrderServiceTest {
    private lateinit var repo: OrderRepositoryImpl
    private lateinit var service: OrderService
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        repo = OrderRepositoryImpl(db.connection)
        service = OrderService(repo, PluginConfig.defaultOrderLevels(), null, 30)
    }

    @Test
    fun `create returns Order when no existing order for player`() {
        val order = service.create(uuid)
        assertNotNull(order, "should create a new order")
        assertEquals(1, order!!.level)
        assertEquals(2, order.radius)
    }

    @Test
    fun `create returns null when player already has an order`() {
        service.create(uuid)
        val second = service.create(uuid)
        assertNull(second, "should not create second order")
    }

    @Test
    fun `getRadiusForLevel returns correct values`() {
        assertEquals(2, service.getRadiusForLevel(1))
        assertEquals(3, service.getRadiusForLevel(2))
        assertEquals(4, service.getRadiusForLevel(3))
        assertEquals(5, service.getRadiusForLevel(4))
        assertEquals(7, service.getRadiusForLevel(5))
    }

    @Test
    fun `getCostForLevel returns correct values`() {
        assertEquals(0, service.getCostForLevel(1))
        assertEquals(30, service.getCostForLevel(2))
        assertEquals(300, service.getCostForLevel(5))
    }

    @Test
    fun `checkOverlap returns false for far-away orders`() {
        val order =
            Order(
                ownerUuid = uuid,
                centerWorld = "world",
                centerX = 0,
                centerY = 64,
                centerZ = 0,
                radius = 2,
            )
        repo.insert(DomainToModelAdapter.toDomain(order))
        repo.activate(uuid, "world", 0, 64, 0)
        val orders = repo.findAllInWorld("world")
        assertFalse(service.checkOverlap(orders, 100, 64, 100, 2))
    }

    @Test
    fun `checkOverlap returns true for nearby orders`() {
        val otherUuid = UUID.randomUUID()
        repo.insert(DomainToModelAdapter.toDomain(Order(ownerUuid = otherUuid)))
        repo.activate(otherUuid, "world", 0, 64, 0)
        val orders = repo.findAllInWorld("world")
        assertTrue(service.checkOverlap(orders, 5, 64, 0, 2))
    }
}
