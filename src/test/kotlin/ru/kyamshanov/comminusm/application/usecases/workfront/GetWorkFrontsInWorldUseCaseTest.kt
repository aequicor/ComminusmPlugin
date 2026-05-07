package ru.kyamshanov.comminusm.application.usecases.workfront

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.kyamshanov.comminusm.domain.entities.WorkFront as DomainWorkFront

class GetWorkFrontsInWorldUseCaseTest {
    private val workFrontRepository = mockk<WorkFrontRepository>()
    private val useCase = GetWorkFrontsInWorldUseCaseImpl(workFrontRepository)

    @Test
    fun `should return empty list when no workfronts in world`() {
        // Arrange
        every { workFrontRepository.findAllInWorld("world") } returns emptyList()

        // Act
        val result = useCase("world")

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return list of workfronts in world`() {
        // Arrange
        val wf1 =
            DomainWorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 100,
                centerY = 64,
                centerZ = 100,
                radius = 25,
            )
        val wf2 =
            DomainWorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 200,
                centerY = 64,
                centerZ = 200,
                radius = 30,
            )
        every { workFrontRepository.findAllInWorld("world") } returns listOf(wf1, wf2)

        // Act
        val result = useCase("world")

        // Assert
        assertEquals(2, result.size)
    }

    @Test
    fun `should return workfronts only from specified world`() {
        // Arrange
        val wf1 =
            DomainWorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "nether",
                centerX = 0,
                centerY = 32,
                centerZ = 0,
                radius = 25,
            )
        every { workFrontRepository.findAllInWorld("nether") } returns listOf(wf1)
        every { workFrontRepository.findAllInWorld("end") } returns emptyList()

        // Act
        val netherResult = useCase("nether")
        val endResult = useCase("end")

        // Assert
        assertEquals(1, netherResult.size)
        assertTrue(endResult.isEmpty())
    }
}
