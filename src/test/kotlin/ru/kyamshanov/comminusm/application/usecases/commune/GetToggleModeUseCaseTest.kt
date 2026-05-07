package ru.kyamshanov.comminusm.application.usecases.commune

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetToggleModeUseCaseTest {
    private val communeChatService = mockk<CommuneChatService>()
    private val useCase = GetToggleModeUseCaseImpl(communeChatService)

    private lateinit var playerUuid: UUID

    @BeforeEach
    fun setUp() {
        playerUuid = UUID.randomUUID()
    }

    @Test
    fun `should return false when toggle mode off`() {
        // Arrange
        every { communeChatService.getToggleMode(playerUuid) } returns false

        // Act
        val result = useCase(playerUuid)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `should return true when toggle mode on`() {
        // Arrange
        every { communeChatService.getToggleMode(playerUuid) } returns true

        // Act
        val result = useCase(playerUuid)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `should return correct mode for different players`() {
        // Arrange
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        every { communeChatService.getToggleMode(player1) } returns true
        every { communeChatService.getToggleMode(player2) } returns false

        // Act
        val result1 = useCase(player1)
        val result2 = useCase(player2)

        // Assert
        assertTrue(result1)
        assertFalse(result2)
    }
}
