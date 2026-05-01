package ru.kyamshanov.comminusm.plugin

import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.event.PlayerJoinHandler
import ru.kyamshanov.comminusm.listener.PlayerListener
import ru.kyamshanov.comminusm.service.WorkdaysService
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.OrderRepository
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import ru.kyamshanov.comminusm.storage.WorkdaysRepository

class ComminusmPlugin : JavaPlugin() {

    companion object {
        private lateinit var INSTANCE: ComminusmPlugin
        fun getInstance() = INSTANCE
    }

    override fun onEnable() {
        INSTANCE = this

        // Save default config
        saveDefaultConfig()

        // Database
        val db = DatabaseManager(this)
        if (!db.integrityCheck()) {
            logger.severe("☭ БАЗА ДАННЫХ ПОВРЕЖДЕНА! Плагин отключён.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Config
        val pluginConfig = PluginConfig(config)

        // Repositories
        val orderRepo = OrderRepository(db.connection)
        val frontRepo = WorkFrontRepository(db.connection)
        val workdaysRepo = WorkdaysRepository(db.connection)

        // Services
        val workdaysService = WorkdaysService(workdaysRepo)

        // Register listeners
        server.pluginManager.registerEvents(PlayerJoinHandler(), this)
        server.pluginManager.registerEvents(PlayerListener(workdaysService, pluginConfig), this)

        logger.info("☭ Плагин активирован! Трудодни начисляются, Ордера выдаются.")
    }

    override fun onDisable() {
        logger.info("☭ Плагин деактивирован. До встречи на собрании, товарищ!")
    }
}
