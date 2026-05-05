package ru.kyamshanov.comminusm.listener

import java.util.UUID
import kotlin.math.abs
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

class BlockListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?
) : Listener {

    private fun hasOrder(uuid: UUID): Boolean = orderService.findByOwner(uuid) != null

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
                        showDeleteOrderConfirmation(player, order.id)
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
                event.isCancelled = true
                val frontRadius = front.radius
                workFrontService?.deactivate(uuid)
                // Remove orphaned banner block from the world
                event.block.type = Material.AIR
                val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
                val meta = flag.itemMeta
                meta.displayName(Component.text("§6Флаг Трудового Фронта"))
                meta.lore(listOf(
                    Component.text("§7Установите в новом месте"),
                    Component.text("§7Радиус добычи: §e${frontRadius} §7блоков")
                ))
                flag.itemMeta = meta
                world.dropItemNaturally(loc, flag)
                player.sendMessage(Component.text("§6☭ Трудовой Фронт удалён. Флаг подобран."))
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
            // Prevent breaking support block of someone else's front flag
            if (isForeignFrontSupportBlock(world, loc, uuid)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cНельзя разрушить опору чужого флага, товарищ!"))
                return
            }
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
            // Prevent breaking support block of someone else's front flag
            if (isForeignFrontSupportBlock(world, loc, uuid)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cНельзя разрушить опору чужого флага, товарищ!"))
                return
            }
            return // allowed
        }

        // 4. No order, no front → DENY
        event.isCancelled = true
        if (hasOrder(uuid)) {
            player.sendMessage(Component.text("§cВы находитесь вне вашей жилплощади, товарищ! Вернитесь в зону Ордера или активируйте Трудовой Фронт через §e/партия"))
        } else {
            player.sendMessage(Component.text("§cНесанкционированная добыча ресурсов, товарищ! Получите Ордер или активируйте Трудовой Фронт через §e/партия"))
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val loc = block.location
        val world = loc.world ?: return

        // Allow flag placement — OrderFlagListener/FrontFlagListener will handle activation
        val item = event.itemInHand
        if (item.type == Material.WHITE_BANNER || item.type == Material.RED_BANNER) {
            return
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
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ!"))
                return
            }
        }

        // 3. Check: inside player's OWN front? → ALLOW
        val myFront = workFrontService?.getByOwner(uuid)
        if (myFront != null && myFront.centerWorld == world.name && isInsideFront(myFront, loc)) {
            return // allowed
        }

        // 4. Outside all zones → DENY
        event.isCancelled = true
        if (hasOrder(uuid)) {
            player.sendMessage(Component.text("§cВы находитесь вне вашей жилплощади, товарищ! Вернитесь в зону Ордера или активируйте Трудовой Фронт через §e/партия"))
        } else {
            player.sendMessage(Component.text("§cНесанкционированное строительство, товарищ! Стройте только на своей жилплощади или в зоне Трудового Фронта через §e/партия"))
        }
    }

    @EventHandler
    fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
        // Only care about right-click on blocks (not air, not left-click)
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return

        val player = event.player
        val uuid = player.uniqueId

        // Allow flag placement — OrderFlagListener/FrontFlagListener will handle interactions with their own flags
        val heldItem = player.inventory.itemInMainHand
        if (heldItem.type == Material.WHITE_BANNER || heldItem.type == Material.RED_BANNER) {
            return
        }

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

        // 3. Check: inside player's OWN front? → ALLOW
        val myFront = workFrontService?.getByOwner(uuid)
        if (myFront != null && myFront.centerWorld == world.name && isInsideFront(myFront, loc)) {
            return // allowed
        }

        // 4. Outside all zones → DENY
        event.isCancelled = true
        if (hasOrder(uuid)) {
            player.sendMessage(Component.text("§cВы находитесь вне вашей жилплощади, товарищ! Вернитесь в зону Ордера или активируйте Трудовой Фронт через §e/партия"))
        } else {
            player.sendMessage(Component.text("§cНесанкционированное взаимодействие, товарищ! Получите Ордер или активируйте Трудовой Фронт через §e/партия"))
        }
    }

    private fun isInsideOrder(order: ru.kyamshanov.comminusm.model.Order, loc: org.bukkit.Location): Boolean {
        if (order.centerWorld == null) return false
        if (loc.world?.name != order.centerWorld) return false
        val dx = abs(order.centerX - loc.blockX)
        val dz = abs(order.centerZ - loc.blockZ)
        return dx <= order.radius && dz <= order.radius
    }

    private fun isInsideFront(front: ru.kyamshanov.comminusm.model.WorkFront, loc: org.bukkit.Location): Boolean {
        if (loc.world?.name != front.centerWorld) return false
        val dx = abs(front.centerX - loc.blockX)
        val dy = abs(front.centerY - loc.blockY)
        val dz = abs(front.centerZ - loc.blockZ)
        return dx <= front.radius && dy <= front.radius && dz <= front.radius
    }

    /**
     * Check if the broken block is a support block of a foreign front flag or order flag.
     * Both red banners (work fronts) and white banners (orders) are attached to a face
     * of the target block. If the support block is destroyed, the banner drops silently
     * without a BlockBreakEvent for the banner itself — so we must intercept at the support
     * block level.
     */
    private fun isForeignFrontSupportBlock(
        world: org.bukkit.World,
        loc: org.bukkit.Location,
        breakerUuid: UUID
    ): Boolean {
        // Check all 4 horizontal directions + top/bottom for an attached banner
        val directions = listOf(
            loc.clone().add(1.0, 0.0, 0.0),
            loc.clone().add(-1.0, 0.0, 0.0),
            loc.clone().add(0.0, 0.0, 1.0),
            loc.clone().add(0.0, 0.0, -1.0),
            loc.clone().add(0.0, 1.0, 0.0),
            loc.clone().add(0.0, -1.0, 0.0)
        )
        for (neighbor in directions) {
            val neighborBlock = world.getBlockAt(neighbor)
            when (neighborBlock.type) {
                Material.RED_BANNER -> {
                    val allFronts = workFrontService?.getAllInWorld(world.name) ?: continue
                    for (f in allFronts) {
                        if (f.centerWorld == world.name
                            && f.centerX == neighbor.blockX
                            && f.centerY == neighbor.blockY
                            && f.centerZ == neighbor.blockZ
                            && f.ownerUuid != breakerUuid) {
                            return true
                        }
                    }
                }
                Material.WHITE_BANNER -> {
                    val allOrders = orderService.findAllInWorld(world.name)
                    for (o in allOrders) {
                        if (o.centerWorld == world.name
                            && o.centerX == neighbor.blockX
                            && o.centerY == neighbor.blockY
                            && o.centerZ == neighbor.blockZ
                            && o.ownerUuid != breakerUuid) {
                            return true
                        }
                    }
                }
                else -> { /* not our concern */ }
            }
        }
        return false
    }

    private fun showDeleteOrderConfirmation(player: org.bukkit.entity.Player, orderId: Long? = null) {
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
