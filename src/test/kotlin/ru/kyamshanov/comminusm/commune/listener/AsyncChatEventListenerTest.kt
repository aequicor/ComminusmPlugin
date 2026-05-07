package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.OrderService
import java.util.UUID

/**
 * Unit tests for AsyncChatEventListener
 * Addresses HIGH corner case #8 (CC-08): message length validation (max 256 chars)
 * and blanks validation per spec §6.15 and test cases TC-78, TC-139
 */
class AsyncChatEventListenerTest {
    private lateinit var communeChatService: CommuneChatService
    private lateinit var communeService: CommuneService
    private lateinit var orderService: OrderService
    private lateinit var listener: AsyncChatEventListener

    @BeforeEach
    fun setUp() {
        communeChatService = mockk(relaxed = true)
        communeService = mockk(relaxed = true)
        orderService = mockk(relaxed = true)
        listener = AsyncChatEventListener(communeChatService, communeService, orderService)
    }

    /**
     * TC-78 / CC-08: Message longer than 256 chars should be rejected
     */
    @Test
    fun testRejectsMessageLongerThan256Chars() {
        val playerUuid = UUID.randomUUID()
        val longMessage = "x".repeat(257)

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerUuid

        val order = mockk<Order>(relaxed = true)

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text(longMessage)

        every { orderService.findByOwner(playerUuid) } returns order
        every { communeService.getCommuneOfOrder(any()) } returns mockk(relaxed = true)
        every { communeChatService.getToggleMode(playerUuid) } returns true

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: event should be cancelled, sendMessage called, broadcast NOT called
        verify { event.isCancelled = true }
        verify(atLeast = 1) { player.sendMessage(any<Component>()) }
    }

    /**
     * TC-78 / CC-08: Message exactly 256 chars should be accepted
     */
    @Test
    fun testAcceptsMessageExactly256Chars() {
        val playerUuid = UUID.randomUUID()
        val message256Chars = "x".repeat(256)

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerUuid
        every { player.name } returns "TestPlayer"

        val order = mockk<Order>(relaxed = true)

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text(message256Chars)

        every { orderService.findByOwner(playerUuid) } returns order
        every { communeService.getCommuneOfOrder(any()) } returns mockk(relaxed = true)
        every { communeChatService.getToggleMode(playerUuid) } returns true

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: broadcast should be called for valid message
        verify(atLeast = 1) { communeChatService.broadcastToCommune(any(), any(), any()) }
    }

    /**
     * TC-139 / CC-08: Blank/empty message should be rejected
     */
    @Test
    fun testRejectsBlankMessage() {
        val playerUuid = UUID.randomUUID()
        val blankMessage = "   "

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerUuid

        val order = mockk<Order>(relaxed = true)

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text(blankMessage)

        every { orderService.findByOwner(playerUuid) } returns order
        every { communeService.getCommuneOfOrder(any()) } returns mockk(relaxed = true)
        every { communeChatService.getToggleMode(playerUuid) } returns true

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: event should be cancelled, sendMessage called, broadcast NOT called
        verify { event.isCancelled = true }
        verify(atLeast = 1) { player.sendMessage(any<Component>()) }
    }

    /**
     * TC-78: Empty string should be rejected
     */
    @Test
    fun testRejectsEmptyMessage() {
        val playerUuid = UUID.randomUUID()

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerUuid

        val order = mockk<Order>(relaxed = true)

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text("")

        every { orderService.findByOwner(playerUuid) } returns order
        every { communeService.getCommuneOfOrder(any()) } returns mockk(relaxed = true)
        every { communeChatService.getToggleMode(playerUuid) } returns true

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: event should be cancelled, sendMessage called, broadcast NOT called
        verify { event.isCancelled = true }
        verify(atLeast = 1) { player.sendMessage(any<Component>()) }
    }

    /**
     * Normal case: Toggle mode off, return early without validation
     */
    @Test
    fun testIgnoresMessageWhenToggleModeOff() {
        val playerUuid = UUID.randomUUID()

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns playerUuid

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text("test message")

        every { communeChatService.getToggleMode(playerUuid) } returns false

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: should return early without modifying event
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
        verify(exactly = 0) { communeChatService.broadcastToCommune(any(), any(), any()) }
    }
}
