package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID

class RecalculateCrossOrderRightsUseCaseImpl(
    private val orderMembershipService: OrderMembershipService,
) : RecalculateCrossOrderRightsUseCase {
    override fun invoke(
        playerUuid: UUID,
        commune: Commune,
    ) {
        val nativeOrdersInCommune =
            orderMembershipService
                .getNativeOrdersOfPlayer(playerUuid)
                .filter { it in commune.orderIds }

        if (nativeOrdersInCommune.isEmpty()) {
            commune.orderIds.forEach { hostOrderId ->
                orderMembershipService.removeMemberSilently(hostOrderId, playerUuid)
            }
        }
    }
}
