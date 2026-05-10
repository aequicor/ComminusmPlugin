@file:Suppress(
    "ReturnCount",
    "MaxLineLength",
    "MagicNumber",
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "LongParameterList",
    "ComplexCondition",
)

package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.application.usecases.order.FindOrdersInWorldUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.gui.GuiUtils
import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.UUID
import kotlin.math.abs

class BlockListener(
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val findOrdersInWorldUseCase: FindOrdersInWorldUseCase,
    private val workFrontService: WorkFrontService?,
) : Listener {
    private fun hasOrder(uuid: UUID): Boolean = getOrderByOwnerUseCase(uuid) != null

    @EventHandler
    @Suppress("ReturnCount")
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val loc = block.location
        val world = loc.world ?: return

        if (handleOrderFlagBreak(event, player, uuid, block, loc, world)) return
        if (handleFrontFlagBreak(event, player, uuid, block, loc, world)) return
        if (handleFlagSupportBreak(event, player, uuid, world, loc)) return
        handleZoneCheck(event, player, uuid, world, loc)
    }

    @Suppress("ReturnCount")
    private fun handleOrderFlagBreak(
        event: BlockBreakEvent,
        player: org.bukkit.entity.Player,
        uuid: UUID,
        block: org.bukkit.block.Block,
        loc: org.bukkit.Location,
        world: org.bukkit.World,
    ): Boolean {
        if (block.type != Material.WHITE_BANNER) return false
        val allOrders = findOrdersInWorldUseCase(world.name)
        for (order in allOrders) {
            if (order.centerWorld == world.name &&
                order.centerX == loc.blockX &&
                order.centerY == loc.blockY &&
                order.centerZ == loc.blockZ
            ) {
                event.isCancelled = true
                return when {
                    order.ownerUuid != uuid -> {
                        player.sendMessage(Component.text("§cНельзя сломать чужой флаг Ордера, товарищ!"))
                        true
                    }
                    else -> {
                        showDeleteOrderConfirmation(player)
                        true
                    }
                }
            }
        }
        return false
    }

    @Suppress("ReturnCount")
    private fun handleFrontFlagBreak(
        event: BlockBreakEvent,
        player: org.bukkit.entity.Player,
        uuid: UUID,
        block: org.bukkit.block.Block,
        loc: org.bukkit.Location,
        world: org.bukkit.World,
    ): Boolean {
        if (block.type != Material.RED_BANNER) return false
        val front = workFrontService?.getByOwner(uuid)
        if (front != null &&
            front.centerWorld == world.name &&
            front.centerX == loc.blockX &&
            front.centerY == loc.blockY &&
            front.centerZ == loc.blockZ
        ) {
            event.isCancelled = true
            deactivateFrontAndGiveFlag(player, uuid, front)
            return true
        }
        val allFronts = workFrontService?.getAllInWorld(world.name) ?: emptyList()
        for (f in allFronts) {
            if (f.centerWorld == world.name &&
                f.centerX == loc.blockX &&
                f.centerY == loc.blockY &&
                f.centerZ == loc.blockZ &&
                f.ownerUuid != uuid
            ) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cНельзя сломать чужой флаг Фронта, товарищ!"))
                return true
            }
        }
        return false
    }

    private fun deactivateFrontAndGiveFlag(
        player: org.bukkit.entity.Player,
        uuid: UUID,
        front: ru.kyamshanov.comminusm.model.WorkFront,
    ) {
        val frontRadius = front.radius
        checkNotNull(workFrontService) { "workFrontService must not be null" }.deactivate(uuid)
        val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
        val meta = flag.itemMeta
        meta.displayName(Component.text("§6Флаг Трудового Фронта"))
        meta.lore(
            listOf(
                Component.text("§7Установите в новом месте"),
                Component.text("§7Радиус добычи: §e$frontRadius §7блоков"),
            ),
        )
        flag.itemMeta = meta
        giveOrNotify(player, flag, "§6☭ Трудовой Фронт удалён. Флаг добавлен в инвентарь.")
    }

    private fun handleFlagSupportBreak(
        event: BlockBreakEvent,
        player: org.bukkit.entity.Player,
        uuid: UUID,
        world: org.bukkit.World,
        loc: org.bukkit.Location,
    ): Boolean {
        val supportInfo = getFlagSupportInfo(world, loc) ?: return false
        return when (supportInfo.type) {
            FlagSupportType.ORDER -> handleOrderFlagSupportBreak(event, player, uuid, world, supportInfo)
            FlagSupportType.FRONT -> handleFrontFlagSupportBreak(event, player, uuid, world, supportInfo)
        }
    }

    private fun handleOrderFlagSupportBreak(
        event: BlockBreakEvent,
        player: org.bukkit.entity.Player,
        uuid: UUID,
        world: org.bukkit.World,
        supportInfo: FlagSupportInfo,
    ): Boolean {
        val order =
            findOrdersInWorldUseCase(world.name).firstOrNull { o ->
                o.centerX == supportInfo.flagX &&
                    o.centerY == supportInfo.flagY &&
                    o.centerZ == supportInfo.flagZ
            } ?: return false
        event.isCancelled = true
        return when {
            order.ownerUuid == uuid -> {
                showDeleteOrderConfirmation(player)
                true
            }
            else -> {
                player.sendMessage(Component.text("§cНельзя разрушить опору чужого флага Ордера, товарищ!"))
                true
            }
        }
    }

    private fun handleFrontFlagSupportBreak(
        event: BlockBreakEvent,
        player: org.bukkit.entity.Player,
        uuid: UUID,
        world: org.bukkit.World,
        supportInfo: FlagSupportInfo,
    ): Boolean {
        val allFronts = workFrontService?.getAllInWorld(world.name) ?: emptyList()
        val front =
            allFronts.firstOrNull { f ->
                f.centerX == supportInfo.flagX &&
                    f.centerY == supportInfo.flagY &&
                    f.centerZ == supportInfo.flagZ
            } ?: return false
        event.isCancelled = true
        return when {
            front.ownerUuid == uuid -> {
                deactivateFrontAndGiveFlag(player, uuid, front)
                true
            }
            else -> {
                player.sendMessage(Component.text("§cНельзя разрушить опору чужого флага Фронта, товарищ!"))
                true
            }
        }
    }

    private fun handleZoneCheck(
        event: BlockBreakEvent,
        player: org.bukkit.entity.Player,
        uuid: UUID,
        world: org.bukkit.World,
        loc: org.bukkit.Location,
    ) {
        val myOrder = getOrderByOwnerUseCase(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return
        }
        val allOrders = findOrdersInWorldUseCase(world.name)
        for (order in allOrders) {
            if (order.ownerUuid != uuid && order.centerWorld == world.name && isInsideOrder(order, loc)) {
                event.isCancelled = true
                player.sendMessage(
                    Component.text(
                        "§cЧужая жилплощадь, товарищ! Обратитесь в партию за собственным Ордером.",
                    ),
                )
                return
            }
        }
        val myFront = workFrontService?.getByOwner(uuid)
        if (myFront != null && myFront.centerWorld == world.name && isInsideFront(myFront, loc)) {
            return
        }
        event.isCancelled = true
        val message =
            if (hasOrder(uuid)) {
                "§cВы находитесь вне вашей жилплощади, товарищ! " +
                    "Вернитесь в зону Ордера или активируйте Трудовой Фронт через §e/партия"
            } else {
                "§cНесанкционированная добыча ресурсов, товарищ! " +
                    "Получите Ордер или активируйте Трудовой Фронт через §e/партия"
            }
        player.sendMessage(Component.text(message))
    }

    @EventHandler
    @Suppress("ReturnCount")
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
        val myOrder = getOrderByOwnerUseCase(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return
        }

        // 2. Check: inside SOMEONE ELSE'S order? → DENY
        val allOrders = findOrdersInWorldUseCase(world.name)
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
            return
        }

        // 4. Outside all zones → DENY
        event.isCancelled = true
        sendOutsideZoneMessage(player, uuid)
    }

    private fun sendOutsideZoneMessage(
        player: org.bukkit.entity.Player,
        uuid: UUID,
    ) {
        if (hasOrder(uuid)) {
            player.sendMessage(
                Component.text(
                    "§cВы находитесь вне вашей жилплощади, товарищ! " +
                        "Вернитесь в зону Ордера или активируйте Трудовой Фронт через §e/партия",
                ),
            )
        } else {
            player.sendMessage(
                Component.text(
                    "§cНесанкционированное строительство, товарищ! " +
                        "Стройте только на своей жилплощади или в зоне Трудового Фронта через §e/партия",
                ),
            )
        }
    }

    @EventHandler
    @Suppress("ReturnCount")
    fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
        // Only care about right-click on blocks (not air, not left-click)
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return

        val player = event.player
        val uuid = player.uniqueId

        // Allow flag placement — OrderFlagListener/FrontFlagListener will handle interactions with their own flags
        val mainHandItem = player.inventory.itemInMainHand
        val offHandItem = player.inventory.itemInOffHand
        if (mainHandItem.type == Material.WHITE_BANNER || mainHandItem.type == Material.RED_BANNER ||
            offHandItem.type == Material.WHITE_BANNER || offHandItem.type == Material.RED_BANNER
        ) {
            return
        }

        val loc = block.location
        val world = loc.world ?: return

        // 1. Check: inside player's OWN order? → ALLOW
        val myOrder = getOrderByOwnerUseCase(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return
        }

        // 2. Check: inside SOMEONE ELSE'S order? → DENY
        val allOrders = findOrdersInWorldUseCase(world.name)
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
            return
        }

        // 4. Outside all zones → DENY
        event.isCancelled = true
        sendOutsideZoneInteractMessage(player, uuid)
    }

    private fun sendOutsideZoneInteractMessage(
        player: org.bukkit.entity.Player,
        uuid: UUID,
    ) {
        if (hasOrder(uuid)) {
            player.sendMessage(
                Component.text(
                    "§cВы находитесь вне вашей жилплощади, товарищ! " +
                        "Вернитесь в зону Ордера или активируйте Трудовой Фронт через §e/партия",
                ),
            )
        } else {
            player.sendMessage(
                Component.text(
                    "§cНесанкционированное взаимодействие, товарищ! " +
                        "Получите Ордер или активируйте Трудовой Фронт через §e/партия",
                ),
            )
        }
    }

    private fun isInsideOrder(
        order: ru.kyamshanov.comminusm.domain.entities.Order,
        loc: org.bukkit.Location,
    ): Boolean {
        val worldName = order.centerWorld ?: return false
        if (loc.world?.name != worldName) return false
        val dx = abs(order.centerX - loc.blockX)
        val dz = abs(order.centerZ - loc.blockZ)
        return dx <= order.radius && dz <= order.radius
    }

    private fun isInsideFront(
        front: ru.kyamshanov.comminusm.model.WorkFront,
        loc: org.bukkit.Location,
    ): Boolean {
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
    private fun getFlagSupportInfo(
        world: org.bukkit.World,
        loc: org.bukkit.Location,
    ): FlagSupportInfo? {
        // Only the block directly above can be the banner whose support this block is.
        val blockAbove = world.getBlockAt(loc.clone().add(0.0, 1.0, 0.0))
        return checkBannerDirectlyAbove(world, blockAbove)
    }

    /**
     * If blockAbove is a RED_BANNER or WHITE_BANNER, and this block (one Y-level below)
     * is registered as its support block, returns FlagSupportInfo; otherwise null.
     */
    private fun checkBannerDirectlyAbove(
        world: org.bukkit.World,
        blockAbove: org.bukkit.block.Block,
    ): FlagSupportInfo? {
        val bannerState = blockAbove.state as? org.bukkit.block.Banner ?: return null
        return when (blockAbove.type) {
            Material.RED_BANNER -> resolveFrontFlag(world, blockAbove, bannerState)
            Material.WHITE_BANNER -> resolveOrderFlag(world, blockAbove, bannerState)
            else -> null
        }
    }

    private fun resolveFrontFlag(
        world: org.bukkit.World,
        block: org.bukkit.block.Block,
        bannerState: org.bukkit.block.Banner,
    ): FlagSupportInfo? {
        val customName = bannerState.customName()
        if (customName != null) {
            val plainText = PlainTextComponentSerializer.plainText().serialize(customName)
            if (isFrontFlagName(plainText)) {
                return FlagSupportInfo(FlagSupportType.FRONT, block.x, block.y, block.z)
            }
        }
        return findFrontByCoordinates(world, block)
    }

    private fun isFrontFlagName(plainText: String): Boolean =
        plainText.contains("Флаг Трудового Фронта") || plainText.contains("Трудового Фронта")

    private fun findFrontByCoordinates(
        world: org.bukkit.World,
        block: org.bukkit.block.Block,
    ): FlagSupportInfo? {
        val allFronts = workFrontService?.getAllInWorld(world.name) ?: return null
        for (f in allFronts) {
            if (f.centerWorld == world.name && f.centerX == block.x && f.centerY == block.y && f.centerZ == block.z) {
                return FlagSupportInfo(FlagSupportType.FRONT, f.centerX, f.centerY, f.centerZ)
            }
        }
        return null
    }

    private fun resolveOrderFlag(
        world: org.bukkit.World,
        block: org.bukkit.block.Block,
        bannerState: org.bukkit.block.Banner,
    ): FlagSupportInfo? {
        val customName = bannerState.customName()
        if (customName != null) {
            val plainText = PlainTextComponentSerializer.plainText().serialize(customName)
            if (isOrderFlagName(plainText)) {
                return FlagSupportInfo(FlagSupportType.ORDER, block.x, block.y, block.z)
            }
        }
        return findOrderByCoordinates(world, block)
    }

    private fun isOrderFlagName(plainText: String): Boolean = plainText.contains("Флаг Ордера") || plainText.contains("Ордера")

    private fun findOrderByCoordinates(
        world: org.bukkit.World,
        block: org.bukkit.block.Block,
    ): FlagSupportInfo? {
        val allOrders = findOrdersInWorldUseCase(world.name)
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
        val flagZ: Int,
    )

    /**
     * Try to add [item] to player's inventory.
     * Priority: offHand (if empty) → main inventory → notify only (never drop on ground).
     */
    private fun giveOrNotify(
        player: org.bukkit.entity.Player,
        item: org.bukkit.inventory.ItemStack,
        successMsg: String,
    ) {
        val inv = player.inventory
        when {
            inv.itemInOffHand.type == org.bukkit.Material.AIR -> {
                inv.setItemInOffHand(item)
                player.sendMessage(Component.text(successMsg))
            }
            inv.firstEmpty() != -1 -> {
                inv.addItem(item)
                player.sendMessage(Component.text(successMsg))
            }
            else -> {
                player.sendMessage(
                    Component.text(
                        "§e⚠ Ваш инвентарь переполнен, товарищ! Флаг не потерян.\n" +
                            "§7Получите его через меню §e/партия",
                    ),
                )
            }
        }
    }

    private fun showDeleteOrderConfirmation(player: org.bukkit.entity.Player) {
        val inv = org.bukkit.Bukkit.createInventory(null, 9, Component.text("§cПодтверждение удаления"))

        inv.setItem(
            2,
            GuiUtils.namedItem(
                "§aДа, удалить Ордер",
                Material.LIME_CONCRETE,
                "§7Это действие необратимо!",
                "§7Флаг будет уничтожен.",
            ),
        )

        inv.setItem(
            6,
            GuiUtils.namedItem(
                "§cНет, оставить",
                Material.RED_CONCRETE,
                "§7Вернуться без изменений",
            ),
        )

        player.openInventory(inv)
    }
}
