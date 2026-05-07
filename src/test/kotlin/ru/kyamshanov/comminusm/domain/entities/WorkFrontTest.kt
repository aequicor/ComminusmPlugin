package ru.kyamshanov.comminusm.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkFrontTest {
    @Test
    fun `WorkFront can be created with basic properties`() {
        val ownerUuid = UUID.randomUUID()
        val workFront =
            WorkFront(
                id = 1L,
                ownerUuid = ownerUuid,
                radius = 25,
            )

        assertEquals(1L, workFront.id)
        assertEquals(ownerUuid, workFront.ownerUuid)
        assertEquals(25, workFront.radius)
    }

    @Test
    fun `WorkFront isActivated returns false when centerWorld is null`() {
        val workFront =
            WorkFront(
                id = 1L,
                ownerUuid = UUID.randomUUID(),
                centerWorld = null,
            )

        assertFalse(workFront.isActivated())
    }

    @Test
    fun `WorkFront isActivated returns true when centerWorld is set`() {
        val workFront =
            WorkFront(
                id = 1L,
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 100,
                centerY = 64,
                centerZ = 100,
            )

        assertTrue(workFront.isActivated())
    }

    @Test
    fun `WorkFront respects default values`() {
        val ownerUuid = UUID.randomUUID()
        val workFront =
            WorkFront(
                id = 1L,
                ownerUuid = ownerUuid,
            )

        assertEquals(2, workFront.radius)
        assertEquals(0, workFront.centerX)
        assertEquals(0, workFront.centerY)
        assertEquals(0, workFront.centerZ)
    }
}
