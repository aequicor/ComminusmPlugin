package ru.kyamshanov.comminusm.application.usecases.order

interface GetOrderCostForLevelUseCase {
    operator fun invoke(level: Int): Int
}
