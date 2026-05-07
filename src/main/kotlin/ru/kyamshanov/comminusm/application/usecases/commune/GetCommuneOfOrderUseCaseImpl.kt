package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.CommuneService

class GetCommuneOfOrderUseCaseImpl(
    private val communeService: CommuneService,
) : GetCommuneOfOrderUseCase {
    override fun invoke(orderId: Long): Commune? = communeService.getCommuneOfOrder(orderId)
}
