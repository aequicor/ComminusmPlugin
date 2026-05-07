package ru.kyamshanov.comminusm.application.usecases.workdays

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import java.util.UUID
import kotlin.test.assertEquals

class GetWorkdaysBalanceUseCaseTest {
    private val workdaysRepository = mockk<WorkdaysRepository>()
    private val useCase = GetWorkdaysBalanceUseCaseImpl(workdaysRepository)

    private lateinit var uuid: UUID

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
    }

    @Test
    fun `should return zero for player with no balance`() {
        // Arrange
        every { workdaysRepository.getBalance(uuid) } returns 0

        // Act
        val result = useCase(uuid)

        // Assert
        assertEquals(0, result)
    }

    @Test
    fun `should return current balance`() {
        // Arrange
        every { workdaysRepository.getBalance(uuid) } returns 100

        // Act
        val result = useCase(uuid)

        // Assert
        assertEquals(100, result)
    }

    @Test
    fun `should return correct balance for multiple players`() {
        // Arrange
        val uuid2 = UUID.randomUUID()
        every { workdaysRepository.getBalance(uuid) } returns 50
        every { workdaysRepository.getBalance(uuid2) } returns 150

        // Act
        val balance1 = useCase(uuid)
        val balance2 = useCase(uuid2)

        // Assert
        assertEquals(50, balance1)
        assertEquals(150, balance2)
    }
}
