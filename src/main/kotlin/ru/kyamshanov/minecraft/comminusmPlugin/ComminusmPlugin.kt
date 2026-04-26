package ru.kyamshanov.minecraft.comminusmPlugin

import org.bukkit.plugin.java.JavaPlugin

class ComminusmPlugin : JavaPlugin() {

    override fun onEnable() {
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
