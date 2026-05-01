package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.gui.GuiUtils
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

        // Prevent breaking Order flags
        if (block.type == Material.WHITE_BANNER) {
            val allOrders = orderService.findAllInWorld(world.name)
            for (order in allOrders) {
                if (order.centerWorld == world.name
                    && order.centerX == loc.blockX
                    && order.centerY == loc.blockY
                    && order.centerZ == loc.blockZ) {
                    if (order.ownerUuid != uuid) {
                        event.isCancelled = true
                        player.sendMessage(Component.text("§cНельзя сломать чужой флаг Ордера, товарищ!"))
                        return
                    } else {
                        event.isCancelled = true
                        showDeleteOrderConfirmation(player)
                        return
                    }
                }
            }
        }

        // Prevent breaking Front flags
        if (block.type == Material.RED_BANNER) {
            val front = workFrontService?.getByOwner(uuid)
            if (front != null && front.centerWorld == world.name
                && front.centerX == loc.blockX && front.centerY == loc.blockY && front.centerZ == loc.blockZ) {
                workFrontService?.deactivate(uuid)
                player.sendMessage(Component.text("§6☭ Трудовой Фронт закрыт. Флаг удалён."))
                return
            }
            val allFronts = workFrontService?.getAllInWorld(world.name) ?: emptyList()
            for (f in allFronts) {
                if (f.centerWorld == world.name && f.centerX == loc.blockX
                    && f.centerY == loc.blockY && f.centerZ == loc.blockZ
                    && f.ownerUuid != uuid) {
                    event.isCancelled = true
                    player.sendMessage(Component.text("§cНельзя сломать чужой флаг Фронта, товарищ!"))
                    return
                }
            }
        }

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
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ!"))
                return
            }
        }

        // 3. Check: inside ANY front (own or cooperative)? → ALLOW
        if (workFrontService != null) {
            val allFronts = workFrontService.getAllInWorld(world.name)
            if (allFronts.any { isInsideFront(it, loc) }) {
                return // allowed (building inside front zones is OK)
            }
        }

        // 4. Outside all zones → DENY
        event.isCancelled = true
        player.sendMessage(Component.text("§cНесанкционированное строительство, товарищ! Стройте только на своей жилплощади или в зоне Трудового Фронта через §e/партия"))
    }

    @EventHandler
    fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
        // Only care about right-click on blocks (not air, not left-click)
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return

        val player = event.player
        val uuid = player.uniqueId
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
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ!"))
                return
            }
        }

        // 3. Check: inside ANY front (cooperative)? → ALLOW
        if (workFrontService != null) {
            val allFronts = workFrontService.getAllInWorld(world.name)
            if (allFronts.any { isInsideFront(it, loc) }) {
                return // allowed
            }
        }

        // 4. Outside all zones → DENY
        event.isCancelled = true
        player.sendMessage(Component.text("§cНесанкционированное взаимодействие, товарищ! Получите Ордер или активируйте Трудовой Фронт через §e/партия"))
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

    private fun showDeleteOrderConfirmation(player: org.bukkit.entity.Player) {
        val inv = org.bukkit.Bukkit.createInventory(null, 9, Component.text("§cПодтверждение удаления"))

        inv.setItem(2, GuiUtils.namedItem(
            "§aДа, удалить Ордер",
            Material.LIME_CONCRETE,
            "§7Это действие необратимо!",
            "§7Флаг будет уничтожен."
        ))

        inv.setItem(6, GuiUtils.namedItem(
            "§cНет, оставить",
            Material.RED_CONCRETE,
            "§7Вернуться без изменений"
        ))

        player.openInventory(inv)
    }
}
