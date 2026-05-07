package ru.kyamshanov.comminusm.application.usecases.commune

import java.util.UUID

interface CheckCommuneFriendlyFireUseCase {
    operator fun invoke(
        damageeUuid: UUID,
        damagerUuid: UUID,
    ): Boolean
}
