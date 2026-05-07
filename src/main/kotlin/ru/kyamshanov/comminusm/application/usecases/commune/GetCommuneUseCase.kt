package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.entities.Commune
import java.util.UUID

/**
 * Use Case: Retrieve a commune by its ID.
 * Returns null if the commune doesn't exist.
 */
interface GetCommuneUseCase {
    operator fun invoke(communeId: UUID): Commune?
}
