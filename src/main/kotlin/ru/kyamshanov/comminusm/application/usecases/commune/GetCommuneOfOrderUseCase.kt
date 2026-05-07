package ru.kyamshanov.comminusm.application.usecases.commune

interface GetCommuneOfOrderUseCase {
    operator fun invoke(orderId: Long): ru.kyamshanov.comminusm.commune.model.Commune?
}
