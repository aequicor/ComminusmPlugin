package ru.kyamshanov.comminusm.application.usecases.order

interface GetRadiusForLevelUseCase {
    operator fun invoke(level: Int): Int
}
