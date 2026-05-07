package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.entities.Commune
import java.util.UUID

/**
 * Use Case: List all communes a member belongs to.
 * Returns empty list if the member is not in any communes.
 */
interface ListCommunesForMemberUseCase {
    operator fun invoke(memberId: UUID): List<Commune>
}
