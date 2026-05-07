package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import java.util.UUID

class RemoveOrderFromCommuneWithCascadeUseCaseImpl(
    private val communeService: CommuneService,
    private val crossOrderMembershipService: CrossOrderMembershipService,
) : RemoveOrderFromCommuneWithCascadeUseCase {
    override fun invoke(orderId: Long) {
        val commune = communeService.getCommuneOfOrder(orderId) ?: return

        try {
            crossOrderMembershipService.setCascadeMode(true)

            commune.orderIds.forEach { otherOrderId ->
                if (otherOrderId != orderId) {
                    crossOrderMembershipService.revokeCommuneMember(Pair(orderId, otherOrderId), UUID.randomUUID())
                    crossOrderMembershipService.revokeCommuneMember(Pair(otherOrderId, orderId), UUID.randomUUID())
                }
            }

            communeService.removeOrderFromCommune(commune.id, orderId)

            val updatedCommune = communeService.getCommune(commune.id)
            if (updatedCommune != null && updatedCommune.orderIds.isEmpty()) {
                communeService.dissolveCommune(commune.id)
            }
        } finally {
            crossOrderMembershipService.clearCascadeMode()
        }
    }
}
