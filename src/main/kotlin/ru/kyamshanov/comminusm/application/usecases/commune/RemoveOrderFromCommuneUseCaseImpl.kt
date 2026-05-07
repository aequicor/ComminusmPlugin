package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.service.CommuneService
import java.util.UUID

class RemoveOrderFromCommuneUseCaseImpl(
    private val communeService: CommuneService,
) : RemoveOrderFromCommuneUseCase {
    override fun invoke(
        communeId: UUID,
        orderId: Long,
    ) {
        communeService.removeOrderFromCommune(communeId, orderId)
    }
}
