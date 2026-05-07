package ru.kyamshanov.comminusm.application.usecases.workfront

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.kyamshanov.comminusm.domain.entities.WorkFront as DomainWorkFront

class GetWorkFrontByOwnerUseCaseTest {
    private val workFrontRepository = mockk<WorkFrontRepository>()
    private val useCase = GetWorkFrontByOwnerUseCaseImpl(workFrontRepository)

    private lateinit var ownerUuid: UUID

    @BeforeEach
    fun setUp() {
        ownerUuid = UUID.randomUUID()
    }

    @Test
    fun `should return null when workfront not found`() {
        // Arrange
        every { workFrontRepository.findByOwner(ownerUuid) } returns null

        // Act
        val result = useCase(ownerUuid)

        // Assert
        assertNull(result)
    }

    @Test
    fun `should return workfront when found`() {
        // Arrange
        val domainWorkFront =
            DomainWorkFront(
                ownerUuid = ownerUuid,
                centerWorld = "world",
                centerX = 100,
                centerY = 64,
                centerZ = 100,
                radius = 25,
            )
        every { workFrontRepository.findByOwner(ownerUuid) } returns domainWorkFront

        // Act
        val result = useCase(ownerUuid)

        // Assert
        assertEquals(ownerUuid, result?.ownerUuid)
        assertEquals("world", result?.centerWorld)
    }

    @Test
    fun `should preserve coordinates from repository`() {
        // Arrange
        val domainWorkFront =
            DomainWorkFront(
                ownerUuid = ownerUuid,
                centerWorld = "nether",
                centerX = 200,
                centerY = 32,
                centerZ = -100,
                radius = 50,
            )
        every { workFrontRepository.findByOwner(ownerUuid) } returns domainWorkFront

        // Act
        val result = useCase(ownerUuid)

        // Assert
        assertEquals(200, result?.centerX)
        assertEquals(32, result?.centerY)
        assertEquals(-100, result?.centerZ)
        assertEquals(50, result?.radius)
    }
}
