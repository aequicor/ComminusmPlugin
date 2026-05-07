package ru.kyamshanov.comminusm.application.usecases.workdays

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncrementWorkdaysUseCaseTest {
    private val workdaysRepository = mockk<WorkdaysRepository>()
    private val useCase = IncrementWorkdaysUseCaseImpl(workdaysRepository)

    private lateinit var uuid: UUID

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
    }

    @Test
    fun `should increment workdays and return new balance`() {
        // Arrange
        every { workdaysRepository.add(uuid, 100) } returns Unit
        every { workdaysRepository.getBalance(uuid) } returns 100

        // Act
        val result = useCase(uuid, 100)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(100, (result as Result.Success).data)
        verify { workdaysRepository.add(uuid, 100) }
    }

    @Test
    fun `should handle zero amount`() {
        // Arrange
        every { workdaysRepository.add(uuid, 0) } returns Unit
        every { workdaysRepository.getBalance(uuid) } returns 50

        // Act
        val result = useCase(uuid, 0)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(50, (result as Result.Success).data)
    }

    @Test
    fun `should fail with negative amount`() {
        // Act
        val result = useCase(uuid, -100)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Amount must be non-negative", (result as Result.Failure).error)
    }

    @Test
    fun `should return correct balance after increment`() {
        // Arrange
        val previousBalance = 50
        every { workdaysRepository.add(uuid, 25) } returns Unit
        every { workdaysRepository.getBalance(uuid) } returns previousBalance + 25

        // Act
        val result = useCase(uuid, 25)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(75, (result as Result.Success).data)
    }
}
