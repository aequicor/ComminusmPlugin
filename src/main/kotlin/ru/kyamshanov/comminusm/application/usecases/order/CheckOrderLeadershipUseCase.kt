package ru.kyamshanov.comminusm.application.usecases.order

import java.util.UUID

interface CheckOrderLeadershipUseCase {
    operator fun invoke(playerUuid: UUID): Boolean
}
