package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Create a new Commune.
 * Fails if the name is blank, too long, or already exists.
 */
interface CreateCommuneUseCase {
    operator fun invoke(
        name: String,
        ownerId: UUID,
    ): Result<Commune>
}
