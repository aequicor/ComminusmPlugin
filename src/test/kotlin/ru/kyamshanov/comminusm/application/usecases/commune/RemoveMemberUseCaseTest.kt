@file:Suppress("MaxLineLength")

package ru.kyamshanov.comminusm.application.usecases.commune

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Unit tests for RemoveMemberUseCase.
 * Verifies authorization checks and member removal logic.
 */
class RemoveMemberUseCaseTest {
    private var updatedCommune: Commune? = null

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
                updatedCommune = commune
                communes[commune.id] = commune
            }

            override fun delete(id: UUID) {
                communes.remove(id)
            }
        }

    private val useCase = RemoveMemberUseCaseImpl(communeRepository)

    @Test
    fun `should remove member when requester is owner`() {
        val communeId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = setOf(memberId),
            )
        communeRepository.update(commune)

        val result = useCase.invoke(communeId, memberId, ownerId)

        assertTrue(result is Result.Success)
        assertTrue(updatedCommune?.memberIds?.isEmpty() == true)
    }

    @Test
    fun `should fail when requester is not owner`() {
        val communeId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val requesterId = UUID.randomUUID() // Different from ownerId

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = setOf(memberId),
            )
        communeRepository.update(commune)

        val result = useCase.invoke(communeId, memberId, requesterId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("owner"),
            "Expected 'owner' in error message, got: $failureMsg",
        )
    }

    @Test
    fun `should fail when member not in commune`() {
        val communeId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = emptySet(),
            )
        communeRepository.update(commune)

        val result = useCase.invoke(communeId, memberId, ownerId)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `should fail when commune not found`() {
        val communeId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val result = useCase.invoke(communeId, memberId, ownerId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("not found"),
            "Expected 'not found' in error message, got: $failureMsg",
        )
    }
}
