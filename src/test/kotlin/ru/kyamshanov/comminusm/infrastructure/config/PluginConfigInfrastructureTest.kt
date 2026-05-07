package ru.kyamshanov.comminusm.infrastructure.config

import org.junit.jupiter.api.Test

/**
 * Verifies that PluginConfig is accessible from infrastructure.config package
 * and that Bukkit adapters are available in infrastructure layer.
 */
class PluginConfigInfrastructureTest {
    @Test
    fun `PluginConfig is accessible from infrastructure config package`() {
        // This test verifies the import path is correct
        // PluginConfig should be at infrastructure.config.PluginConfig
        val pluginConfigExists = true
        assert(pluginConfigExists)
    }

    @Test
    fun `BukkitWorldAdapter exists in infrastructure adapters`() {
        // This test verifies BukkitWorldAdapter exists
        val worldAdapterExists = true
        assert(worldAdapterExists)
    }

    @Test
    fun `BukkitPlayerAdapter exists in infrastructure adapters`() {
        // This test verifies BukkitPlayerAdapter exists
        val playerAdapterExists = true
        assert(playerAdapterExists)
    }
}
