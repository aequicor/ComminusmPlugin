package ru.kyamshanov.comminusm.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderTest {
    @Test
    fun `Order can be created with basic properties`() {
        val ownerUuid = UUID.randomUUID()
        val order =
            Order(
                id = 1L,
                ownerUuid = ownerUuid,
                level = 2,
                radius = 3,
            )

        assertEquals(1L, order.id)
        assertEquals(ownerUuid, order.ownerUuid)
        assertEquals(2, order.level)
        assertEquals(3, order.radius)
        assertNull(order.centerWorld)
    }

    @Test
    fun `Order isActivated returns false when centerWorld is null`() {
        val order =
            Order(
                id = 1L,
                ownerUuid = UUID.randomUUID(),
                centerWorld = null,
            )

        assertFalse(order.isActivated())
    }

    @Test
    fun `Order isActivated returns true when centerWorld is set`() {
        val order =
            Order(
                id = 1L,
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 10,
                centerY = 20,
                centerZ = 30,
            )

        assertTrue(order.isActivated())
    }

    @Test
    fun `Order getLocation returns null when not activated`() {
        val order =
            Order(
                id = 1L,
                ownerUuid = UUID.randomUUID(),
                centerWorld = null,
            )

        assertNull(order.getLocation())
    }

    @Test
    fun `Order getLocation returns WorldLocation when activated`() {
        val order =
            Order(
                id = 1L,
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 10,
                centerY = 20,
                centerZ = 30,
            )

        val location = order.getLocation()
        assertNotNull(location)
        assertEquals("world", location!!.world)
        assertEquals(10, location.x)
        assertEquals(20, location.y)
        assertEquals(30, location.z)
    }

    @Test
    fun `Order respects default values`() {
        val ownerUuid = UUID.randomUUID()
        val order = Order(ownerUuid = ownerUuid)

        assertEquals(0L, order.id)
        assertEquals(1, order.level)
        assertEquals(2, order.radius)
        assertEquals(0, order.centerX)
        assertEquals(0, order.centerY)
        assertEquals(0, order.centerZ)
        assertNull(order.centerWorld)
    }
}
