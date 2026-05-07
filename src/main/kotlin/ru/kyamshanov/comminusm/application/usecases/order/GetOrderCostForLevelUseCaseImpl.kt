package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig

class GetOrderCostForLevelUseCaseImpl(
    private val levels: List<OrderLevelConfig>,
) : GetOrderCostForLevelUseCase {
    override fun invoke(level: Int): Int = levels.find { it.level == level }?.cost ?: 0
}
