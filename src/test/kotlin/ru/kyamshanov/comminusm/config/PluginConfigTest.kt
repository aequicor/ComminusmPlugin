package ru.kyamshanov.comminusm.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PluginConfigTest {
    @Test
    fun `default workdays rates are not empty`() {
        val rates = PluginConfig.defaultResourceRates()
        assertFalse(rates.isEmpty())
        assertEquals(4, rates["COBBLESTONE"])
        assertEquals(12, rates["IRON_INGOT"])
    }

    @Test
    fun `default order levels has 5 entries`() {
        val levels = PluginConfig.defaultOrderLevels()
        assertEquals(5, levels.size)
        assertEquals(2, levels[0].radius)
        assertEquals(7, levels[4].radius)
    }

    @Test
    fun `DEFAULT_FLAG_MAX_PER_CHUNK is 50`() {
        assertEquals(50, PluginConfig.DEFAULT_FLAG_MAX_PER_CHUNK)
    }

    @Test
    fun `DEFAULT_FLAG_STARTUP_SCAN_BATCH_SIZE is 10`() {
        assertEquals(10, PluginConfig.DEFAULT_FLAG_STARTUP_SCAN_BATCH_SIZE)
    }

    @Test
    fun `MIN_FLAG_AIR_ABOVE is 1`() {
        assertEquals(1, PluginConfig.MIN_FLAG_AIR_ABOVE)
    }

    @Test
    fun `DEFAULT_FLAG_MIN_AIR_ABOVE is 2`() {
        assertEquals(2, PluginConfig.DEFAULT_FLAG_MIN_AIR_ABOVE)
    }

    @Test
    fun `DEFAULT_FLAG_MIN_AIR_ABOVE is greater than MIN_FLAG_AIR_ABOVE`() {
        assertTrue(PluginConfig.DEFAULT_FLAG_MIN_AIR_ABOVE > PluginConfig.MIN_FLAG_AIR_ABOVE)
    }
}
