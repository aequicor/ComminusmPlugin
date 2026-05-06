package ru.kyamshanov.comminusm.event

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.kyamshanov.comminusm.book.ManifestoBook

class PlayerJoinHandler : Listener {

    private val mm = MiniMessage.miniMessage()

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        player.openBook(ManifestoBook.create())

        event.joinMessage(
            mm.deserialize("<yellow>[ВХОД] <green>${event.player.name}<yellow> явился на собрание трудового коллектива!")
        )
    }
}
