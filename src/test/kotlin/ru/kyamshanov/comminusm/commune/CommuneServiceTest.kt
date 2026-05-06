package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.service.CommuneService
import java.time.LocalDateTime
import java.util.UUID

class CommuneServiceTest {
    private lateinit var communeService: CommuneService
    private val communes = mutableMapOf<UUID, Commune>()
    private val orderToCommuneId = mutableMapOf<Long, UUID>()

    @BeforeEach
    fun setUp() {
        communes.clear()
        orderToCommuneId.clear()
        communeService = CommuneService(communes, orderToCommuneId)
    }

    @Test
    fun `createCommune creates a new commune with version 0`() {
        val leadingOrderId = 1L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(leadingOrderId, createdBy)

        assertTrue(result is Result.Success)
        val commune = (result as Result.Success).data
        assertNotNull(commune.id)
        assertEquals(0, commune.version)
        assertTrue(commune.orderIds.contains(leadingOrderId))
        assertEquals(createdBy, commune.createdBy)
    }

    @Test
    fun `getCommune returns commune by ID`() {
        val leadingOrderId = 1L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(leadingOrderId, createdBy)
        val commune = (result as Result.Success).data
        val communeId = commune.id

        val retrieved = communeService.getCommune(communeId)
        assertNotNull(retrieved)
        assertEquals(communeId, retrieved?.id)
        assertEquals(0, retrieved?.version)
    }

    @Test
    fun `getCommuneOfOrder returns commune containing an order`() {
        val order1 = 1L
        val order2 = 2L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(order1, createdBy)
        val commune = (result as Result.Success).data

        communeService.addOrderToCommune(commune.id, order2)

        val retrievedCommune = communeService.getCommuneOfOrder(order2)
        assertNotNull(retrievedCommune)
        assertEquals(commune.id, retrievedCommune?.id)
    }

    @Test
    fun `addOrderToCommune increments version`() {
        val order1 = 1L
        val order2 = 2L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(order1, createdBy)
        val commune = (result as Result.Success).data
        val initialVersion = commune.version

        communeService.addOrderToCommune(commune.id, order2)

        val updated = communeService.getCommune(commune.id)
        assertEquals(initialVersion + 1, updated?.version)
    }

    @Test
    fun `removeOrderFromCommune removes order and increments version`() {
        val order1 = 1L
        val order2 = 2L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(order1, createdBy)
        val commune = (result as Result.Success).data
        communeService.addOrderToCommune(commune.id, order2)

        val versionBefore = communeService.getCommune(commune.id)?.version ?: 0

        communeService.removeOrderFromCommune(commune.id, order2)

        val updated = communeService.getCommune(commune.id)
        assertEquals(versionBefore + 1, updated?.version)
        assertFalse(updated?.orderIds?.contains(order2) ?: true)
    }

    @Test
    fun `dissolveCommune removes commune from maps`() {
        val order1 = 1L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(order1, createdBy)
        val commune = (result as Result.Success).data

        communeService.dissolveCommune(commune.id)

        val retrieved = communeService.getCommune(commune.id)
        assertNull(retrieved)
    }

    @Test
    fun `incrementVersion atomically updates version`() {
        val order1 = 1L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(order1, createdBy)
        val commune = (result as Result.Success).data
        val communeId = commune.id

        val newVersion = communeService.incrementVersion(communeId)

        assertEquals(1, newVersion)
        val updated = communeService.getCommune(communeId)
        assertEquals(1, updated?.version)
    }

    @Test
    fun `getCommuneOrders returns all order IDs in commune`() {
        val order1 = 1L
        val order2 = 2L
        val order3 = 3L
        val createdBy = UUID.randomUUID()

        val result = communeService.createCommune(order1, createdBy)
        val commune = (result as Result.Success).data

        communeService.addOrderToCommune(commune.id, order2)
        communeService.addOrderToCommune(commune.id, order3)

        val orders = communeService.getCommuneOrders(commune.id)

        assertEquals(3, orders.size)
        assertTrue(orders.contains(order1))
        assertTrue(orders.contains(order2))
        assertTrue(orders.contains(order3))
    }
}
