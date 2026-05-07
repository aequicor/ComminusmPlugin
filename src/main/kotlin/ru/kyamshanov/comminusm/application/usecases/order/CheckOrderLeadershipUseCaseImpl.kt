package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID

class CheckOrderLeadershipUseCaseImpl(
    private val orderRepository: OrderRepository,
) : CheckOrderLeadershipUseCase {
    override fun invoke(playerUuid: UUID): Boolean = orderRepository.findByOwner(playerUuid) != null
}
