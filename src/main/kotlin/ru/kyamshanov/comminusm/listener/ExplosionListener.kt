package ru.kyamshanov.comminusm.listener

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class ExplosionListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?,
    private val manager: FlagStabilityManager? = null
) : Listener {

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { block ->
            isOrderFlag(block) || isFrontFlag(block) || isFlagSupportBlock(block)
        }
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { block ->
            isOrderFlag(block) || isFrontFlag(block) || isFlagSupportBlock(block)
        }
    }

    private fun isOrderFlag(block: org.bukkit.block.Block): Boolean {
        if (block.type != Material.WHITE_BANNER) return false
        if (manager?.isFlagPosition(block) == true) return true
        val world = block.world.name
        val orders = orderService.findAllInWorld(world)
        return orders.any { o ->
            o.centerWorld == world && o.centerX == block.x && o.centerY == block.y && o.centerZ == block.z
        }
    }

    private fun isFrontFlag(block: org.bukkit.block.Block): Boolean {
        if (block.type != Material.RED_BANNER) return false
        if (manager?.isFlagPosition(block) == true) return true
        val world = block.world.name
        val fronts = workFrontService?.getAllInWorld(world) ?: emptyList()
        return fronts.any { f ->
            f.centerWorld == world && f.centerX == block.x && f.centerY == block.y && f.centerZ == block.z
        }
    }

    private fun isFlagSupportBlock(block: org.bukkit.block.Block): Boolean =
        manager?.isFlagPosition(block) == true
}
