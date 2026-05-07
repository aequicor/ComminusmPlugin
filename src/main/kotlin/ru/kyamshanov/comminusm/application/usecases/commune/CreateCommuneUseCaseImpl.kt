@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of CreateCommuneUseCase.
 * Creates a new commune with the owner as the first member.
 */
class CreateCommuneUseCaseImpl(
    private val communeRepository: CommuneRepository,
) : CreateCommuneUseCase {
    override fun invoke(
        name: String,
        ownerId: UUID,
    ): Result<Commune> {
        if (name.isBlank()) {
            return Result.failure("Commune name cannot be blank")
        }

        if (name.length > MAX_NAME_LENGTH) {
            return Result.failure("Commune name too long (max $MAX_NAME_LENGTH characters)")
        }

        val existing = communeRepository.findByName(name)
        if (existing != null) {
            return Result.failure("Commune with name '$name' already exists")
        }

        val commune =
            Commune(
                name = name,
                ownerId = ownerId,
                memberIds = setOf(ownerId),
            )

        val id = communeRepository.insert(commune)
        return Result.success(commune.copy(id = id))
    }

    companion object {
        private const val MAX_NAME_LENGTH = 50
    }
}
