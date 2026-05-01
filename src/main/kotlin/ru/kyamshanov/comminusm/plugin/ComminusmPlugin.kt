package ru.kyamshanov.comminusm.plugin

import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.command.PartyCommand
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.event.PlayerJoinHandler
import ru.kyamshanov.comminusm.gui.AdminMenu
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.gui.OrderMenu
import ru.kyamshanov.comminusm.gui.PartyMenu
import ru.kyamshanov.comminusm.gui.TreasuryMenu
import ru.kyamshanov.comminusm.listener.BlockListener
import ru.kyamshanov.comminusm.listener.ExplosionListener
import ru.kyamshanov.comminusm.listener.FrontFlagListener
import ru.kyamshanov.comminusm.listener.OrderFlagListener
import ru.kyamshanov.comminusm.listener.PlayerListener
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
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
        val chunkCache = ChunkCacheManager()
        val workdaysService = WorkdaysService(workdaysRepo)
        val orderService = OrderService(orderRepo, pluginConfig.orderLevels, workdaysService, pluginConfig.minDistanceBetweenCenters, chunkCache)
        val workFrontService = WorkFrontService(frontRepo, pluginConfig.frontRadius, chunkCache)

        // Register listeners
        server.pluginManager.registerEvents(PlayerJoinHandler(), this)
        server.pluginManager.registerEvents(PlayerListener(workdaysService, pluginConfig), this)

        // Register order flag listener
        server.pluginManager.registerEvents(OrderFlagListener(orderService, workdaysService, pluginConfig), this)

        // Register block protection
        server.pluginManager.registerEvents(BlockListener(orderService, workFrontService), this)

        // Register explosion protection for flags
        server.pluginManager.registerEvents(ExplosionListener(orderService, workFrontService), this)

        // Register front flag listener
        server.pluginManager.registerEvents(FrontFlagListener(workFrontService), this)

        // Register GUI listeners
        server.pluginManager.registerEvents(PartyMenu(pluginConfig, workdaysService, orderService, workFrontService), this)
        server.pluginManager.registerEvents(OrderMenu(orderService, workdaysService, pluginConfig), this)
        server.pluginManager.registerEvents(FrontMenu(workFrontService), this)
        server.pluginManager.registerEvents(TreasuryMenu(pluginConfig, workdaysService), this)
        server.pluginManager.registerEvents(AdminMenu(pluginConfig, orderService, workFrontService, workdaysService), this)

        // Register command
        val partyCmd = checkNotNull(getCommand("party")) { "Команда 'party' не объявлена в plugin.yml" }
        partyCmd.setExecutor(PartyCommand(pluginConfig, workdaysService, orderService, workFrontService))

        logger.info("☭ Плагин активирован! Трудодни начисляются, Ордера выдаются.")
    }

    override fun onDisable() {
        logger.info("☭ Плагин деактивирован. До встречи на собрании, товарищ!")
    }
}
