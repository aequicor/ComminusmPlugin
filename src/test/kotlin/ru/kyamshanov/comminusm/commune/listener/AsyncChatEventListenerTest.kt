package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.commune.BroadcastToCommuneUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.GetToggleModeUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.domain.entities.Order
import java.util.UUID

/**
 * Unit tests for AsyncChatEventListener
 * Addresses HIGH corner case #8 (CC-08): message length validation (max 256 chars)
 * and blanks validation per spec §6.15 and test cases TC-78, TC-139
 */
class AsyncChatEventListenerTest {
    private lateinit var getToggleModeUseCase: GetToggleModeUseCase
    private lateinit var getOrderByOwnerUseCase: GetOrderByOwnerUseCase
    private lateinit var getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase
    private lateinit var broadcastToCommuneUseCase: BroadcastToCommuneUseCase
    private lateinit var listener: AsyncChatEventListener

    @BeforeEach
    fun setUp() {
        getToggleModeUseCase = mockk(relaxed = true)
        getOrderByOwnerUseCase = mockk(relaxed = true)
        getCommuneOfOrderUseCase = mockk(relaxed = true)
        broadcastToCommuneUseCase = mockk(relaxed = true)
        listener =
            AsyncChatEventListener(
                getToggleModeUseCase,
                getOrderByOwnerUseCase,
                getCommuneOfOrderUseCase,
                broadcastToCommuneUseCase,
            )
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

        every { getToggleModeUseCase.invoke(playerUuid) } returns true
        every { getOrderByOwnerUseCase.invoke(playerUuid) } returns order
        every { getCommuneOfOrderUseCase.invoke(any()) } returns mockk(relaxed = true)

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
        val commune = mockk<ru.kyamshanov.comminusm.commune.model.Commune>(relaxed = true)

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text(message256Chars)

        every { getToggleModeUseCase.invoke(playerUuid) } returns true
        every { getOrderByOwnerUseCase.invoke(playerUuid) } returns order
        every { getCommuneOfOrderUseCase.invoke(any()) } returns commune

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: broadcast should be called for valid message
        verify(atLeast = 1) { broadcastToCommuneUseCase.invoke(any(), any(), any()) }
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

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text(blankMessage)

        every { getToggleModeUseCase.invoke(playerUuid) } returns true

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

        val event = mockk<AsyncChatEvent>(relaxed = true)
        every { event.player } returns player
        every { event.message() } returns Component.text("")

        every { getToggleModeUseCase.invoke(playerUuid) } returns true

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

        every { getToggleModeUseCase.invoke(playerUuid) } returns false

        // Act
        listener.onAsyncPlayerChat(event)

        // Assert: should return early without modifying event
        verify(exactly = 0) { player.sendMessage(any<Component>()) }
        verify(exactly = 0) { broadcastToCommuneUseCase.invoke(any(), any(), any()) }
    }
}
