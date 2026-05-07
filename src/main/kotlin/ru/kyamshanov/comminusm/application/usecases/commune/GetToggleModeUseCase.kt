package ru.kyamshanov.comminusm.application.usecases.commune

import java.util.UUID

interface GetToggleModeUseCase {
    operator fun invoke(playerUuid: UUID): Boolean
}
