package ru.kyamshanov.comminusm.service

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.PluginLoader
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

/**
 * Minimal [Plugin] stub for unit tests. Override what you need.
 * All other methods throw [UnsupportedOperationException].
 */
@Suppress("TooManyFunctions")
abstract class StubPluginBase : Plugin {
    override fun getDescription(): PluginDescriptionFile = throw UnsupportedOperationException()
    override fun getConfig(): FileConfiguration = throw UnsupportedOperationException()
    override fun reloadConfig() = throw UnsupportedOperationException()
    override fun saveConfig() = throw UnsupportedOperationException()
    override fun saveDefaultConfig() {}
    override fun saveResource(resourcePath: String, replace: Boolean) {}
    override fun getResource(filename: String): InputStream? = null
    override fun getServer(): Server = throw UnsupportedOperationException()
    override fun isEnabled(): Boolean = true
    override fun getPluginMeta(): io.papermc.paper.plugin.configuration.PluginMeta =
        throw UnsupportedOperationException()
    override fun onDisable() {}
    override fun onLoad() {}
    override fun onEnable() {}
    override fun isNaggable(): Boolean = false
    override fun setNaggable(canNag: Boolean) {}
    override fun getName(): String = "test-plugin"
    override fun getLogger(): Logger = Logger.getLogger("test")
    override fun getDataFolder(): File = throw UnsupportedOperationException()
    override fun getDefaultWorldGenerator(worldName: String, id: String?): ChunkGenerator? = null
    override fun getDefaultBiomeProvider(worldName: String, id: String?): BiomeProvider? = null
    @Deprecated("Deprecated in Java")
    override fun getPluginLoader(): PluginLoader = throw UnsupportedOperationException()
    override fun getLifecycleManager(): LifecycleEventManager<Plugin> =
        throw UnsupportedOperationException()
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String>? = null
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean = false
    override fun namespace(): String = "test-plugin"
}
