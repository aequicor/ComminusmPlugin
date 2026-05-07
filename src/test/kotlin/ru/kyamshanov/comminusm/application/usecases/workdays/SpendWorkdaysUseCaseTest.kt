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

class SpendWorkdaysUseCaseTest {
    private val workdaysRepository = mockk<WorkdaysRepository>()
    private val useCase = SpendWorkdaysUseCaseImpl(workdaysRepository)

    private lateinit var uuid: UUID

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
    }

    @Test
    fun `should spend workdays when sufficient balance`() {
        // Arrange
        every { workdaysRepository.spend(uuid, 50) } returns true

        // Act
        val result = useCase(uuid, 50)

        // Assert
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data)
        verify { workdaysRepository.spend(uuid, 50) }
    }

    @Test
    fun `should fail when insufficient balance`() {
        // Arrange
        every { workdaysRepository.spend(uuid, 200) } returns false

        // Act
        val result = useCase(uuid, 200)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Insufficient workdays", (result as Result.Failure).error)
    }

    @Test
    fun `should fail with negative amount`() {
        // Act
        val result = useCase(uuid, -50)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Amount must be non-negative", (result as Result.Failure).error)
    }

    @Test
    fun `should handle zero amount`() {
        // Arrange
        every { workdaysRepository.spend(uuid, 0) } returns true

        // Act
        val result = useCase(uuid, 0)

        // Assert
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data)
    }
}
