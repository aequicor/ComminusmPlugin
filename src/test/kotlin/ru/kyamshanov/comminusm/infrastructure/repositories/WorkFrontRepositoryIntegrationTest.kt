package ru.kyamshanov.comminusm.infrastructure.repositories

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.WorkFront
import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkFrontRepositoryIntegrationTest {
    private val workFrontRepository = mockk<WorkFrontRepository>()

    private lateinit var ownerUuid: UUID

    @BeforeEach
    fun setUp() {
        ownerUuid = UUID.randomUUID()
    }

    @Test
    fun `should find workfront by owner when exists`() {
        // Arrange
        val workFront =
            WorkFront(
                ownerUuid = ownerUuid,
                centerWorld = "world",
                centerX = 100,
                centerY = 64,
                centerZ = 100,
                radius = 25,
            )
        every { workFrontRepository.findByOwner(ownerUuid) } returns workFront

        // Act
        val found = workFrontRepository.findByOwner(ownerUuid)

        // Assert
        assertNotNull(found)
        assertEquals(ownerUuid, found?.ownerUuid)
    }

    @Test
    fun `should return null for non-existent workfront`() {
        // Arrange
        every { workFrontRepository.findByOwner(any()) } returns null

        // Act
        val found = workFrontRepository.findByOwner(UUID.randomUUID())

        // Assert
        assertNull(found)
    }

    @Test
    fun `should find workfronts in world`() {
        // Arrange
        val wf1 =
            WorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 0,
                centerY = 64,
                centerZ = 0,
                radius = 25,
            )
        val wf2 =
            WorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "nether",
                centerX = 0,
                centerY = 32,
                centerZ = 0,
                radius = 25,
            )
        every { workFrontRepository.findAllInWorld("world") } returns listOf(wf1)
        every { workFrontRepository.findAllInWorld("nether") } returns listOf(wf2)

        // Act
        val worldFronts = workFrontRepository.findAllInWorld("world")
        val netherFronts = workFrontRepository.findAllInWorld("nether")

        // Assert
        assertEquals(1, worldFronts.size)
        assertEquals(1, netherFronts.size)
    }

    @Test
    fun `should find all activated workfronts`() {
        // Arrange
        val wf1 =
            WorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "world",
                centerX = 0,
                centerY = 64,
                centerZ = 0,
                radius = 25,
            )
        val wf2 =
            WorkFront(
                ownerUuid = UUID.randomUUID(),
                centerWorld = "nether",
                centerX = 0,
                centerY = 32,
                centerZ = 0,
                radius = 25,
            )
        every { workFrontRepository.findAllActivated() } returns listOf(wf1, wf2)

        // Act
        val activated = workFrontRepository.findAllActivated()

        // Assert
        assertEquals(2, activated.size)
    }
}
