package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import ru.kyamshanov.comminusm.manager.FlagStabilityManager

/**
 * Protects flag positions (banners and support blocks) from indirect destruction:
 * pistons, water/lava flow, and entity block changes (endermen, silverfish, etc.).
 */
class FlagProtectionListener(
    private val manager: FlagStabilityManager
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { manager.isFlagPosition(it) }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { manager.isFlagPosition(it) }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        // Prevent water/lava flow from replacing flag or support blocks
        if (manager.isFlagPosition(event.toBlock)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        // Prevent endermen, silverfish, etc. from modifying flag blocks
        if (manager.isFlagPosition(event.block)) {
            event.isCancelled = true
        }
    }
}
