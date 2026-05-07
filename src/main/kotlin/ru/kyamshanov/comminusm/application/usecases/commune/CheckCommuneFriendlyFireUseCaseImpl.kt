package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID

class CheckCommuneFriendlyFireUseCaseImpl(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
) : CheckCommuneFriendlyFireUseCase {
    override fun invoke(
        damageeUuid: UUID,
        damagerUuid: UUID,
    ): Boolean {
        val damagerNativeOrders = orderMembershipService.getNativeOrdersOfPlayer(damagerUuid)
        val damageeNativeOrders = orderMembershipService.getNativeOrdersOfPlayer(damageeUuid)

        if (damagerNativeOrders.isEmpty() || damageeNativeOrders.isEmpty()) return false

        return damagerNativeOrders.any { damagerOrder ->
            val damagerCommune = communeService.getCommuneOfOrder(damagerOrder) ?: return@any false
            damageeNativeOrders.any { damagerCommune.orderIds.contains(it) }
        }
    }
}
