package ru.kyamshanov.comminusm.event

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.kyamshanov.comminusm.book.ManifestoBook

class PlayerJoinHandler : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        player.openBook(ManifestoBook.create())

        event.joinMessage(
            Component.text("§e[ВХОД] §a${event.player.name}§e явился на собрание трудового коллектива!")
        )
    }
}
