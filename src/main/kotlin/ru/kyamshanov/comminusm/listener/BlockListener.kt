package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import kotlin.math.abs

class BlockListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?
) : Listener {

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val loc = block.location
        val world = loc.world ?: return

        // 1. Check: inside player's OWN order? → ALLOW
        val myOrder = orderService.findByOwner(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return // allowed
        }

        // 2. Check: inside SOMEONE ELSE'S order? → DENY
        val allOrders = orderService.findAllInWorld(world.name)
        for (order in allOrders) {
            if (order.ownerUuid != uuid && order.centerWorld == world.name && isInsideOrder(order, loc)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ! Обратитесь в партию за собственным Ордером."))
                return
            }
        }

        // 3. Check: inside player's OWN front? → ALLOW
        val myFront = workFrontService?.getByOwner(uuid)
        if (myFront != null && myFront.centerWorld == world.name && isInsideFront(myFront, loc)) {
            return // allowed
        }

        // 4. Check: inside ANY front (cooperative)? → ALLOW
        if (workFrontService != null) {
            val allFronts = workFrontService.getAllInWorld(world.name)
            if (allFronts.any { isInsideFront(it, loc) }) {
                return // cooperative mining allowed
            }
        }

        // 5. No order, no front → DENY
        event.isCancelled = true
        player.sendMessage(Component.text("§cНесанкционированная добыча ресурсов, товарищ! Получите Ордер или активируйте Трудовой Фронт через §e/партия"))
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val loc = block.location
        val world = loc.world ?: return

        // Allow placement inside own order
        val myOrder = orderService.findByOwner(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return
        }

        // Deny placement inside someone else's order
        val allOrders = orderService.findAllInWorld(world.name)
        for (order in allOrders) {
            if (order.ownerUuid != uuid && order.centerWorld == world.name && isInsideOrder(order, loc)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ!"))
                return
            }
        }
        // Allow placement everywhere else (building is allowed, only breaking is locked outside zones)
    }

    private fun isInsideOrder(order: ru.kyamshanov.comminusm.model.Order, loc: org.bukkit.Location): Boolean {
        if (order.centerWorld == null) return false
        if (loc.world?.name != order.centerWorld) return false
        val dx = abs(order.centerX - loc.blockX)
        val dy = abs(order.centerY - loc.blockY)
        val dz = abs(order.centerZ - loc.blockZ)
        return dx <= order.radius && dy <= order.radius && dz <= order.radius
    }

    private fun isInsideFront(front: ru.kyamshanov.comminusm.model.WorkFront, loc: org.bukkit.Location): Boolean {
        if (loc.world?.name != front.centerWorld) return false
        val dx = abs(front.centerX - loc.blockX)
        val dy = abs(front.centerY - loc.blockY)
        val dz = abs(front.centerZ - loc.blockZ)
        return dx <= front.radius && dy <= front.radius && dz <= front.radius
    }
}
