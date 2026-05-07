@file:Suppress("MaxLineLength")

package ru.kyamshanov.comminusm.application.usecases.commune

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Unit tests for DisbandCommuneUseCase.
 * Verifies ownership enforcement at the use case level.
 */
class DisbandCommuneUseCaseTest {
    private var deletedCommuneId: UUID? = null

    private val communeRepository =
        object : CommuneRepository {
            private val communes = mutableMapOf<UUID, Commune>()

            override fun findById(id: UUID): Commune? = communes[id]

            override fun findByName(name: String): Commune? = communes.values.find { it.name == name }

            override fun findAllByMember(memberId: UUID): List<Commune> = communes.values.filter { it.memberIds.contains(memberId) }

            override fun insert(commune: Commune): UUID {
                communes[commune.id] = commune
                return commune.id
            }

            override fun update(commune: Commune) {
                communes[commune.id] = commune
            }

            override fun delete(id: UUID) {
                deletedCommuneId = id
                communes.remove(id)
            }
        }

    private val useCase = DisbandCommuneUseCaseImpl(communeRepository)

    @Test
    fun `should disband commune when requester is owner`() {
        val communeId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = setOf(UUID.randomUUID(), UUID.randomUUID()),
            )
        communeRepository.update(commune)

        val result = useCase.invoke(communeId, ownerId)

        assertTrue(result is Result.Success)
        assertTrue(deletedCommuneId == communeId)
    }

    @Test
    fun `should fail when requester is not owner`() {
        val communeId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val requesterId = UUID.randomUUID() // Different from ownerId

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = setOf(UUID.randomUUID()),
            )
        communeRepository.update(commune)

        val result = useCase.invoke(communeId, requesterId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("owner"),
            "Expected 'owner' in error message, got: $failureMsg",
        )
    }

    @Test
    fun `should fail when commune not found`() {
        val communeId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val result = useCase.invoke(communeId, ownerId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("not found"),
            "Expected 'not found' in error message, got: $failureMsg",
        )
    }
}
