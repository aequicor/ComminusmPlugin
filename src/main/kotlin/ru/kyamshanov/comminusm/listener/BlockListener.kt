package ru.kyamshanov.comminusm.listener

import java.util.UUID
import kotlin.math.abs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.gui.GuiUtils
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class BlockListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?,
    private val manager: FlagStabilityManager? = null
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
                checkNotNull(workFrontService) { "workFrontService must not be null" }.deactivate(uuid)
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
                giveOrNotify(player, flag, "§6☭ Трудовой Фронт удалён. Флаг добавлен в инвентарь.")
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

        // Check: is this block a support block for any order or front flag?
        val supportInfo = getFlagSupportInfo(world, loc)
        if (supportInfo != null) {
            when (supportInfo.type) {
                FlagSupportType.ORDER -> {
                    val order = orderService.findAllInWorld(world.name).firstOrNull { o ->
                        o.centerX == supportInfo.flagX &&
                            o.centerY == supportInfo.flagY &&
                            o.centerZ == supportInfo.flagZ
                    }
                    if (order != null) {
                        if (order.ownerUuid == uuid) {
                            // Owner breaking their own order flag support → show deletion confirmation
                            event.isCancelled = true
                            showDeleteOrderConfirmation(player, order.id)
                        } else {
                            // Foreign player breaking someone's order flag support → deny
                            event.isCancelled = true
                            player.sendMessage(Component.text("§cНельзя разрушить опору чужого флага Ордера, товарищ!"))
                        }
                        return
                    }
                }
                FlagSupportType.FRONT -> {
                    val allFronts = workFrontService?.getAllInWorld(world.name) ?: emptyList()
                    val front = allFronts.firstOrNull { f ->
                        f.centerX == supportInfo.flagX &&
                            f.centerY == supportInfo.flagY &&
                            f.centerZ == supportInfo.flagZ
                    }
                    if (front != null) {
                        if (front.ownerUuid == uuid) {
                            // Owner breaking their own front flag support → deactivate front, give flag to inventory
                            // Note: deactivate() calls FlagCleanupHelper which restores the original support material.
                            // Do NOT set event.block.type = Material.AIR here — that would overwrite the restoration.
                            event.isCancelled = true
                            val frontRadius = front.radius
                            checkNotNull(workFrontService) { "workFrontService must not be null" }.deactivate(uuid)
                            val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
                            val meta = flag.itemMeta
                            meta.displayName(Component.text("§6Флаг Трудового Фронта"))
                            meta.lore(listOf(
                                Component.text("§7Установите в новом месте"),
                                Component.text("§7Радиус добычи: §e${frontRadius} §7блоков")
                            ))
                            flag.itemMeta = meta
                            giveOrNotify(player, flag, "§6☭ Трудовой Фронт удалён. Флаг добавлен в инвентарь.")
                        } else {
                            // Foreign player breaking someone's front flag support → deny
                            event.isCancelled = true
                            player.sendMessage(Component.text("§cНельзя разрушить опору чужого флага Фронта, товарищ!"))
                        }
                        return
                    }
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
     * Check if the broken block is the structural support of any order or front flag.
     *
     * A support block is ALWAYS exactly one Y-level below the banner
     * (confirmed by FlagActivationHelper.activate: bannerBlock.y - 1).
     * Therefore, the only candidate above the broken block to inspect is
     * the single block at (loc.x, loc.y + 1, loc.z).
     *
     * The previous 6-direction scan was too broad: any block adjacent to a banner
     * (including flowers, torches, etc.) falsely triggered flag cleanup.
     */
    private fun getFlagSupportInfo(world: org.bukkit.World, loc: org.bukkit.Location): FlagSupportInfo? {
        // Only the block directly above can be the banner whose support this block is.
        val blockAbove = world.getBlockAt(loc.clone().add(0.0, 1.0, 0.0))
        return checkBannerDirectlyAbove(world, blockAbove)
    }

    /**
     * If blockAbove is a RED_BANNER or WHITE_BANNER, and this block (one Y-level below)
     * is registered as its support block, returns FlagSupportInfo; otherwise null.
     */
    private fun checkBannerDirectlyAbove(world: org.bukkit.World, blockAbove: org.bukkit.block.Block): FlagSupportInfo? {
        val bannerState = blockAbove.state as? org.bukkit.block.Banner ?: return null
        return when (blockAbove.type) {
            Material.RED_BANNER -> resolveFrontFlag(world, blockAbove, bannerState)
            Material.WHITE_BANNER -> resolveOrderFlag(world, blockAbove, bannerState)
            else -> null
        }
    }

    private fun resolveFrontFlag(
        world: org.bukkit.World, block: org.bukkit.block.Block, bannerState: org.bukkit.block.Banner
    ): FlagSupportInfo? {
        val customName = bannerState.customName()
        if (customName != null) {
            val plainText = PlainTextComponentSerializer.plainText().serialize(customName)
            if (plainText.contains("Флаг Трудового Фронта") ||
                plainText.contains("Трудового Фронта")) {
                return FlagSupportInfo(FlagSupportType.FRONT, block.x, block.y, block.z)
            }
        }
        val allFronts = workFrontService?.getAllInWorld(world.name) ?: return null
        for (f in allFronts) {
            if (f.centerWorld == world.name && f.centerX == block.x && f.centerY == block.y && f.centerZ == block.z) {
                return FlagSupportInfo(FlagSupportType.FRONT, f.centerX, f.centerY, f.centerZ)
            }
        }
        return null
    }

    private fun resolveOrderFlag(
        world: org.bukkit.World, block: org.bukkit.block.Block, bannerState: org.bukkit.block.Banner
    ): FlagSupportInfo? {
        val customName = bannerState.customName()
        if (customName != null) {
            val plainText = PlainTextComponentSerializer.plainText().serialize(customName)
            if (plainText.contains("Флаг Ордера") ||
                plainText.contains("Ордера")) {
                return FlagSupportInfo(FlagSupportType.ORDER, block.x, block.y, block.z)
            }
        }
        val allOrders = orderService.findAllInWorld(world.name)
        for (o in allOrders) {
            if (o.centerWorld == world.name && o.centerX == block.x && o.centerY == block.y && o.centerZ == block.z) {
                return FlagSupportInfo(FlagSupportType.ORDER, o.centerX, o.centerY, o.centerZ)
            }
        }
        return null
    }

    /** Whether the support block belongs to an order front or a work front. */
    private enum class FlagSupportType { ORDER, FRONT }

    /** Coordinates of the flag this block supports and its type. */
    private data class FlagSupportInfo(
        val type: FlagSupportType,
        val flagX: Int,
        val flagY: Int,
        val flagZ: Int
    )

    /**
     * Try to add [item] to player's inventory. Falls back to dropping on ground
     * only when inventory is full, with an overflow warning.
     */
    private fun giveOrNotify(
        player: org.bukkit.entity.Player,
        item: org.bukkit.inventory.ItemStack,
        successMsg: String
    ) {
        if (player.inventory.firstEmpty() == -1) {
            // Inventory full — fallback to ground with warning, but flag still NOT lost:
            // player can retrieve it anytime via /партия
            player.world.dropItemNaturally(player.location, item)
            player.sendMessage(Component.text(
                "§e⚠ Ваш инвентарь переполнен, товарищ! Флаг выброшен на землю.\n" +
                    "§7Вы всегда можете получить новый флаг через меню §e/партия"
            ))
        } else {
            player.inventory.addItem(item)
            player.sendMessage(Component.text(successMsg))
        }
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
