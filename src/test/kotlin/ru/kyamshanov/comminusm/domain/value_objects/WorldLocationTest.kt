@file:Suppress("PackageName")

package ru.kyamshanov.comminusm.domain.value_objects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Suppress("PackageName")
class WorldLocationTest {
    @Test
    fun `WorldLocation can be created with coordinates`() {
        val location =
            WorldLocation(
                world = "world",
                x = 100,
                y = 64,
                z = 200,
            )

        assertEquals("world", location.world)
        assertEquals(100, location.x)
        assertEquals(64, location.y)
        assertEquals(200, location.z)
    }

    @Test
    fun `WorldLocation is a data class with equality`() {
        val location1 = WorldLocation("world", 100, 64, 200)
        val location2 = WorldLocation("world", 100, 64, 200)
        val location3 = WorldLocation("world", 101, 64, 200)

        assertEquals(location1, location2)
        assert(location1 != location3)
    }

    @Test
    fun `WorldLocation supports copy`() {
        val location = WorldLocation("world", 100, 64, 200)
        val copied = location.copy(x = 101)

        assertEquals("world", copied.world)
        assertEquals(101, copied.x)
        assertEquals(64, copied.y)
        assertEquals(200, copied.z)
    }
}
