package ru.kyamshanov.comminusm.plugin

import org.bukkit.plugin.java.JavaPlugin
import ru.kyamshanov.comminusm.event.PlayerJoinHandler

class ComminusmPlugin : JavaPlugin() {

    override fun onEnable() {
        server.pluginManager.registerEvents(PlayerJoinHandler(), this)
        logger.info(
            """
            ╔═══════════════════════════════════════════════════╗
            ║   ☭ COMMINUSM PLUGIN v1.0.0 ☭                    ║
            ║   "Пролетарии всех биомов, соединяйтесь!"        ║
            ╠═══════════════════════════════════════════════════╣
            ║   ✓ Общая казна активирована                      ║
            ║   ✓ Трудовая повинность в силе                   ║
            ║   ✓ Равенство перед лицом партии                 ║
            ╚═══════════════════════════════════════════════════╝
        """.trimIndent()
        )
    }

    override fun onDisable() {
        logger.info("☭ Плагин деактивирован. До встречи на собрании, товарищ!")
    }
}
