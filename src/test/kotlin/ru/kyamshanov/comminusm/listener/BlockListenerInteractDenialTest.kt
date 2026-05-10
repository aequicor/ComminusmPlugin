@file:Suppress("LongParameterList", "MaxLineLength")

package ru.kyamshanov.comminusm.listener

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.order.FindOrdersInWorldUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.UUID

/**
 * Regression test for TC-User-04 / DEF-User-05.
 *
 * Spec rule (privates-orders-fronts.md, Zone Ownership Rules):
 *   Wilderness     → Interact = DENY, UseItemInHand = DENY
 *   Others' Order  → Interact = DENY, UseItemInHand = DENY
 *
 * Defect: BlockListener.onPlayerInteract called only `event.isCancelled = true`.
 * In Paper, `isCancelled` alone does NOT guarantee `useInteractedBlock` is set
 * to DENY — for some interactive blocks (crafting tables, doors, levers, chests)
 * the action can still go through if a later handler resets the result, or if
 * Bukkit's default behaviour for the event leaves `useInteractedBlock = ALLOW`.
 *
 * Fix: deny-branches must explicitly call
 *   event.setUseInteractedBlock(Event.Result.DENY)
 *   event.setUseItemInHand(Event.Result.DENY)
 *   event.isCancelled = true
 */
class BlockListenerInteractDenialTest {
    private val playerId = UUID.randomUUID()
    private val otherPlayerId = UUID.randomUUID()

    private lateinit var getOrderByOwner: GetOrderByOwnerUseCase
    private lateinit var findOrdersInWorld: FindOrdersInWorldUseCase
    private lateinit var workFrontService: WorkFrontService
    private lateinit var listener: BlockListener

    @BeforeEach
    fun setUp() {
        getOrderByOwner = mockk()
        findOrdersInWorld = mockk()
        workFrontService = mockk()
        listener = BlockListener(getOrderByOwner, findOrdersInWorld, workFrontService)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun makeInteractEvent(
        blockType: Material,
        worldName: String = "world",
        x: Int = 100,
        y: Int = 64,
        z: Int = 200,
        mainHandType: Material = Material.STONE,
        offHandType: Material = Material.AIR,
        sneaking: Boolean = false,
    ): PlayerInteractEvent {
        val event = mockk<PlayerInteractEvent>(relaxed = true)
        val block = mockk<Block>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val mainHandItem = mockk<ItemStack>(relaxed = true)
        val offHandItem = mockk<ItemStack>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val location = mockk<Location>(relaxed = true)

        every { event.action } returns Action.RIGHT_CLICK_BLOCK
        every { event.clickedBlock } returns block
        every { event.player } returns player
        every { player.uniqueId } returns playerId
        every { player.isSneaking } returns sneaking
        every { player.inventory } returns inventory
        every { inventory.itemInMainHand } returns mainHandItem
        every { inventory.itemInOffHand } returns offHandItem
        every { mainHandItem.type } returns mainHandType
        every { offHandItem.type } returns offHandType
        every { block.type } returns blockType
        every { block.location } returns location
        every { location.world } returns world
        every { location.blockX } returns x
        every { location.blockY } returns y
        every { location.blockZ } returns z
        every { world.name } returns worldName
        return event
    }

    // -------------------------------------------------------------------------
    // Wilderness scenarios — TC-43 / TC-User-04
    // -------------------------------------------------------------------------

    @Test
    fun `wilderness right-click on crafting table — useInteractedBlock must be DENY`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event = makeInteractEvent(Material.CRAFTING_TABLE)
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify { event.setUseItemInHand(Event.Result.DENY) }
        verify { event.isCancelled = true }
    }

    @Test
    fun `wilderness right-click on oak door — useInteractedBlock must be DENY`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event = makeInteractEvent(Material.OAK_DOOR)
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify { event.setUseItemInHand(Event.Result.DENY) }
    }

    @Test
    fun `wilderness right-click on lever — useInteractedBlock must be DENY`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event = makeInteractEvent(Material.LEVER)
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify { event.setUseItemInHand(Event.Result.DENY) }
    }

    @Test
    fun `wilderness right-click on chest — useInteractedBlock must be DENY`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event = makeInteractEvent(Material.CHEST)
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify { event.setUseItemInHand(Event.Result.DENY) }
    }

    // -------------------------------------------------------------------------
    // Others' order scenario — TC-32 / TC-34
    // -------------------------------------------------------------------------

    @Test
    fun `right-click inside others' order — useInteractedBlock must be DENY`() {
        val foreignOrder =
            Order(
                id = 7L,
                ownerUuid = otherPlayerId,
                level = 1,
                radius = 5,
                centerWorld = "world",
                centerX = 100,
                centerY = 64,
                centerZ = 200,
            )
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns listOf(foreignOrder)
        every { workFrontService.getByOwner(playerId) } returns null

        val event = makeInteractEvent(Material.OAK_DOOR, x = 100, y = 64, z = 200)
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify { event.setUseItemInHand(Event.Result.DENY) }
        verify { event.isCancelled = true }
    }

    // -------------------------------------------------------------------------
    // Allow scenarios — must NOT set DENY
    // -------------------------------------------------------------------------

    @Test
    fun `right-click inside own order — does not set DENY`() {
        val ownOrder =
            Order(
                id = 1L,
                ownerUuid = playerId,
                level = 1,
                radius = 5,
                centerWorld = "world",
                centerX = 100,
                centerY = 64,
                centerZ = 200,
            )
        every { getOrderByOwner(playerId) } returns ownOrder
        every { findOrdersInWorld("world") } returns listOf(ownOrder)

        val event = makeInteractEvent(Material.CRAFTING_TABLE, x = 100, y = 64, z = 200)
        listener.onPlayerInteract(event)

        verify(exactly = 0) { event.setUseInteractedBlock(Event.Result.DENY) }
        verify(exactly = 0) { event.setUseItemInHand(Event.Result.DENY) }
        verify(exactly = 0) { event.isCancelled = true }
    }

    // -------------------------------------------------------------------------
    // Banner-in-hand bypass — TC-User-05 / DEF-User-06
    //
    // The previous early-return on "any banner in hand" let players open chests,
    // crafting tables and doors in Wilderness as long as they held a banner.
    // The fix must skip the zone check ONLY when the right-click would actually
    // place the banner — i.e. the player is sneaking OR the block is non-interactable.
    // -------------------------------------------------------------------------

    @Test
    fun `wilderness right-click on chest with WHITE_BANNER in main hand — useInteractedBlock must be DENY`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event =
            makeInteractEvent(
                blockType = Material.CHEST,
                mainHandType = Material.WHITE_BANNER,
                sneaking = false,
            )
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
        verify { event.setUseItemInHand(Event.Result.DENY) }
    }

    @Test
    fun `wilderness right-click on crafting table with RED_BANNER in offhand — useInteractedBlock must be DENY`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event =
            makeInteractEvent(
                blockType = Material.CRAFTING_TABLE,
                offHandType = Material.RED_BANNER,
                sneaking = false,
            )
        listener.onPlayerInteract(event)

        verify { event.setUseInteractedBlock(Event.Result.DENY) }
    }

    @Test
    fun `wilderness right-click on stone with WHITE_BANNER in main hand — does NOT cancel (banner placement on non-interactive)`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event =
            makeInteractEvent(
                blockType = Material.STONE,
                mainHandType = Material.WHITE_BANNER,
                sneaking = false,
            )
        listener.onPlayerInteract(event)

        verify(exactly = 0) { event.setUseInteractedBlock(Event.Result.DENY) }
        verify(exactly = 0) { event.isCancelled = true }
    }

    @Test
    fun `wilderness sneak + right-click on chest with WHITE_BANNER in main hand — does NOT cancel (sneak forces banner placement)`() {
        every { getOrderByOwner(playerId) } returns null
        every { findOrdersInWorld("world") } returns emptyList()
        every { workFrontService.getByOwner(playerId) } returns null

        val event =
            makeInteractEvent(
                blockType = Material.CHEST,
                mainHandType = Material.WHITE_BANNER,
                sneaking = true,
            )
        listener.onPlayerInteract(event)

        verify(exactly = 0) { event.setUseInteractedBlock(Event.Result.DENY) }
        verify(exactly = 0) { event.isCancelled = true }
    }
}
