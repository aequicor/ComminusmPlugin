package ru.kyamshanov.comminusm.application.usecases.workfront

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.UUID

class DeactivateWorkFrontUseCaseTest {
    private val workFrontService = mockk<WorkFrontService>()
    private val useCase = DeactivateWorkFrontUseCaseImpl(workFrontService)

    private lateinit var ownerUuid: UUID

    @BeforeEach
    fun setUp() {
        ownerUuid = UUID.randomUUID()
    }

    @Test
    fun `should call service deactivate method`() {
        // Arrange
        every { workFrontService.deactivate(ownerUuid) } returns Unit

        // Act
        useCase(ownerUuid)

        // Assert
        verify { workFrontService.deactivate(ownerUuid) }
    }

    @Test
    fun `should deactivate correct owner`() {
        // Arrange
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        every { workFrontService.deactivate(any()) } returns Unit

        // Act
        useCase(uuid1)
        useCase(uuid2)

        // Assert
        verify { workFrontService.deactivate(uuid1) }
        verify { workFrontService.deactivate(uuid2) }
    }
}
