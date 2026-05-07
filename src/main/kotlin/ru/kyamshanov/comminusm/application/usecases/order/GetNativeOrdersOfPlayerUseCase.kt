package ru.kyamshanov.comminusm.application.usecases.order

import java.util.UUID

interface GetNativeOrdersOfPlayerUseCase {
    operator fun invoke(playerUuid: UUID): List<Long>
}
