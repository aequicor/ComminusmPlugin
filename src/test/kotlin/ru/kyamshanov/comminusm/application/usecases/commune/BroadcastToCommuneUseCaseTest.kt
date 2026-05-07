package ru.kyamshanov.comminusm.application.usecases.commune

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import java.util.UUID

class BroadcastToCommuneUseCaseTest {
    private val communeChatService = mockk<CommuneChatService>()
    private val useCase = BroadcastToCommuneUseCaseImpl(communeChatService)

    private lateinit var communeId: UUID
    private lateinit var senderUuid: UUID
    private val senderPlayer = mockk<Player>()

    @BeforeEach
    fun setUp() {
        communeId = UUID.randomUUID()
        senderUuid = UUID.randomUUID()
        mockkStatic(Bukkit::class)
    }

    @Test
    fun `should broadcast message when sender found`() {
        // Arrange
        every { Bukkit.getPlayer(senderUuid) } returns senderPlayer
        every { communeChatService.broadcastToCommune(communeId, senderPlayer, "test message") } returns Unit

        // Act
        useCase(communeId, senderUuid, "test message")

        // Assert
        verify { communeChatService.broadcastToCommune(communeId, senderPlayer, "test message") }
    }

    @Test
    fun `should not broadcast when sender not found`() {
        // Arrange
        every { Bukkit.getPlayer(senderUuid) } returns null

        // Act
        useCase(communeId, senderUuid, "test message")

        // Assert
        verify(exactly = 0) { communeChatService.broadcastToCommune(any(), any(), any()) }
    }

    @Test
    fun `should broadcast with correct message`() {
        // Arrange
        val message = "Hello commune!"
        every { Bukkit.getPlayer(senderUuid) } returns senderPlayer
        every { communeChatService.broadcastToCommune(communeId, senderPlayer, message) } returns Unit

        // Act
        useCase(communeId, senderUuid, message)

        // Assert
        verify { communeChatService.broadcastToCommune(communeId, senderPlayer, message) }
    }
}
