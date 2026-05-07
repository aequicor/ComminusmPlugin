package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import java.util.UUID

class RevokeCommuneMemberUseCaseImpl(
    private val crossOrderMembershipService: CrossOrderMembershipService,
) : RevokeCommuneMemberUseCase {
    override fun invoke(
        nativeOrderId: Long,
        hostOrderId: Long,
        operationId: UUID,
    ) {
        crossOrderMembershipService.revokeCommuneMember(Pair(nativeOrderId, hostOrderId), operationId)
    }
}
