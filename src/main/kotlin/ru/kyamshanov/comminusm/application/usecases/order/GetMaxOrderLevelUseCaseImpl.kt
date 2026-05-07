package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig

private const val DEFAULT_MAX_LEVEL = 5

class GetMaxOrderLevelUseCaseImpl(
    private val levels: List<OrderLevelConfig>,
) : GetMaxOrderLevelUseCase {
    override fun invoke(): Int = levels.maxOfOrNull { it.level } ?: DEFAULT_MAX_LEVEL
}
