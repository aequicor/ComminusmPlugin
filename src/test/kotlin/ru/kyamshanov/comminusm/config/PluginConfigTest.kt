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
}
