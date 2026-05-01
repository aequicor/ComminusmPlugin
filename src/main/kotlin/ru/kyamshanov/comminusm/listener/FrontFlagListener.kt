package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.service.WorkFrontService

class FrontFlagListener(
    private val workFrontService: WorkFrontService
) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.RED_BANNER) return
        val meta = item.itemMeta ?: return
        val displayName = meta.displayName().toString()
        if (!displayName.contains("Флаг Трудового Фронта")) return

        val player = event.player
        val location = event.block.location
        val world = checkNotNull(location.world) { "World is null" }.name

        workFrontService.activate(player.uniqueId, world, location.blockX, location.blockY, location.blockZ)

        player.sendMessage(Component.text("§6☭ Трудовой Фронт активирован! Радиус: §e25 §6блоков. Партия ждёт перевыполнения нормы!"))
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.RED_BANNER) return

        val player = event.player
        val front = workFrontService.getByOwner(player.uniqueId) ?: return

        val loc = block.location
        if (loc.world?.name != front.centerWorld) return
        if (loc.blockX != front.centerX || loc.blockY != front.centerY || loc.blockZ != front.centerZ) return

        event.isCancelled = true
        FrontMenu(workFrontService).open(player, front)
    }
}
