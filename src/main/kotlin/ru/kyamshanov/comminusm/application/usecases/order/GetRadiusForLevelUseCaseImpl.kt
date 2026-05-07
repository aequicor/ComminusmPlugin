package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig

class GetRadiusForLevelUseCaseImpl(
    private val levels: List<OrderLevelConfig>,
) : GetRadiusForLevelUseCase {
    override fun invoke(level: Int): Int {
        val found = levels.find { it.level == level }?.radius
        return found ?: levels.lastOrNull()?.radius ?: 2
    }
}
