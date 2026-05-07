package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID

class GetNativeOrdersOfPlayerUseCaseImpl(
    private val orderMembershipService: OrderMembershipService,
) : GetNativeOrdersOfPlayerUseCase {
    override fun invoke(playerUuid: UUID): List<Long> =
        orderMembershipService
            .getNativeOrdersOfPlayer(playerUuid)
            .toList()
}
