package ru.kyamshanov.comminusm.infrastructure.repositories

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkdaysRepositoryIntegrationTest {
    private val workdaysRepository = mockk<WorkdaysRepository>()

    private lateinit var playerUuid: UUID

    @BeforeEach
    fun setUp() {
        playerUuid = UUID.randomUUID()
    }

    @Test
    fun `should get zero balance for new player`() {
        // Arrange
        every { workdaysRepository.getBalance(any()) } returns 0

        // Act
        val balance = workdaysRepository.getBalance(UUID.randomUUID())

        // Assert
        assertEquals(0, balance)
    }

    @Test
    fun `should return current balance`() {
        // Arrange
        every { workdaysRepository.getBalance(playerUuid) } returns 100

        // Act
        val balance = workdaysRepository.getBalance(playerUuid)

        // Assert
        assertEquals(100, balance)
    }

    @Test
    fun `should spend workdays when sufficient balance`() {
        // Arrange
        every { workdaysRepository.spend(playerUuid, 50) } returns true

        // Act
        val success = workdaysRepository.spend(playerUuid, 50)

        // Assert
        assertTrue(success)
    }

    @Test
    fun `should fail to spend when insufficient balance`() {
        // Arrange
        every { workdaysRepository.spend(playerUuid, 100) } returns false

        // Act
        val success = workdaysRepository.spend(playerUuid, 100)

        // Assert
        assertFalse(success)
    }

    @Test
    fun `should handle multiple players independently`() {
        // Arrange
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        every { workdaysRepository.getBalance(player1) } returns 100
        every { workdaysRepository.getBalance(player2) } returns 50

        // Act
        val balance1 = workdaysRepository.getBalance(player1)
        val balance2 = workdaysRepository.getBalance(player2)

        // Assert
        assertEquals(100, balance1)
        assertEquals(50, balance2)
    }
}
