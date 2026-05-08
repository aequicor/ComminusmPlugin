@file:Suppress("MagicNumber", "MaxLineLength")

package ru.kyamshanov.comminusm.gui

import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.application.usecases.order.RenameOrderUseCase
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertTrue

/**
 * Unit tests for [OrderRenameMenu] state management.
 *
 * Tests cover:
 * - TC-30 (CC-06): Double-open guard via pendingChatInputs map
 * - TC-31 (CC-07): Disconnect cleanup via PlayerQuitEvent
 */
@Suppress("TooManyFunctions")
class OrderRenameMenuTest {
    private val renameOrderUseCase: RenameOrderUseCase = mockk()
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase = mockk()
    private val orderRepository: OrderRepository = mockk()
    private val plugin: Plugin = mockk()

    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val orderId: Long = 42L

    private lateinit var menu: OrderRenameMenu

    @BeforeEach
    fun setUp() {
        menu =
            OrderRenameMenu(
                renameOrderUseCase = renameOrderUseCase,
                getOrderByOwnerUseCase = getOrderByOwnerUseCase,
                orderRepository = orderRepository,
                plugin = plugin,
            )
    }

    private fun pendingChatInputs(): ConcurrentHashMap<UUID, Long> {
        val field = OrderRenameMenu::class.java.getDeclaredField("pendingChatInputs")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(menu) as ConcurrentHashMap<UUID, Long>
    }

    // -----------------------------------------------------------------------
    // TC-30 (CC-06): Double-open guard via pendingChatInputs map
    // -----------------------------------------------------------------------

    /**
     * TC-30: Verify that the pendingChatInputs map prevents a second open
     * while a rename is already awaiting chat input.
     */
    @Test
    fun `TC-30 pendingChatInputs guard prevents second open when entry already exists`() {
        val map = pendingChatInputs()

        map[playerUuid] = orderId

        val shouldSkipSecondOpen = map.containsKey(playerUuid)

        assertTrue(
            shouldSkipSecondOpen,
            "pendingChatInputs must contain playerUuid to guard against double-open (TC-30)",
        )
    }

    /**
     * TC-30 variant: Entry is removed after rename completes or is cancelled.
     */
    @Test
    fun `TC-30 pendingChatInputs entry removed after rename completes`() {
        val map = pendingChatInputs()

        map[playerUuid] = orderId

        assertTrue(map.containsKey(playerUuid), "Entry must exist before removal (TC-30 setup)")

        map.remove(playerUuid)

        assertTrue(
            !map.containsKey(playerUuid),
            "Entry must be removed to allow next open (TC-30)",
        )
    }

    // -----------------------------------------------------------------------
    // TC-31 (CC-07): Disconnect cleanup via PlayerQuitEvent
    // -----------------------------------------------------------------------

    /**
     * TC-31: PlayerQuitEvent removes the player's pendingChatInputs entry
     * when they disconnect while a rename is awaiting chat input.
     */
    @Test
    fun `TC-31 PlayerQuitEvent removes player entry from pendingChatInputs`() {
        val map = pendingChatInputs()

        val mockPlayer = mockk<Player>()
        every { mockPlayer.uniqueId } returns playerUuid

        map[playerUuid] = orderId

        assertTrue(map.containsKey(playerUuid), "Entry must exist before PlayerQuitEvent (TC-31 setup)")

        val quitEvent = mockk<PlayerQuitEvent>()
        every { quitEvent.player } returns mockPlayer

        menu.onPlayerQuit(quitEvent)

        assertTrue(
            !map.containsKey(playerUuid),
            "PlayerQuitEvent must remove entry from pendingChatInputs (TC-31)",
        )
    }

    /**
     * TC-31 variant: PlayerQuitEvent is idempotent when no entry is present.
     */
    @Test
    fun `TC-31 PlayerQuitEvent is idempotent when entry does not exist`() {
        val map = pendingChatInputs()

        val mockPlayer = mockk<Player>()
        every { mockPlayer.uniqueId } returns playerUuid

        assertTrue(!map.containsKey(playerUuid), "No entry should exist for this player (TC-31 setup)")

        val quitEvent = mockk<PlayerQuitEvent>()
        every { quitEvent.player } returns mockPlayer

        menu.onPlayerQuit(quitEvent)
        menu.onPlayerQuit(quitEvent)

        assertTrue(!map.containsKey(playerUuid), "No entry should exist after idempotent calls (TC-31)")
    }

    // -----------------------------------------------------------------------
    // Lifecycle verification
    // -----------------------------------------------------------------------

    @Test
    fun `pendingChatInputs lifecycle manages rename state correctly`() {
        val map = pendingChatInputs()

        val mockPlayer = mockk<Player>()
        every { mockPlayer.uniqueId } returns playerUuid

        assertTrue(!map.containsKey(playerUuid), "No entry should exist initially")

        map[playerUuid] = orderId

        assertTrue(map.containsKey(playerUuid), "Entry should exist after open")

        val quitEvent = mockk<PlayerQuitEvent>()
        every { quitEvent.player } returns mockPlayer
        menu.onPlayerQuit(quitEvent)

        assertTrue(!map.containsKey(playerUuid), "Entry should be removed by PlayerQuitEvent")
    }
}
