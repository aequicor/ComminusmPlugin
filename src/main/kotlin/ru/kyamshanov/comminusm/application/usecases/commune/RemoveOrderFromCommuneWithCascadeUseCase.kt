package ru.kyamshanov.comminusm.application.usecases.commune

interface RemoveOrderFromCommuneWithCascadeUseCase {
    operator fun invoke(orderId: Long)
}
