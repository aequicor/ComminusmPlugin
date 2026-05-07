package ru.kyamshanov.comminusm.application.usecases.commune

import java.util.UUID

interface RecalculateCrossOrderRightsUseCase {
    operator fun invoke(
        playerUuid: UUID,
        commune: ru.kyamshanov.comminusm.commune.model.Commune,
    )
}
