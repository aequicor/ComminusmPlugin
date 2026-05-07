package ru.kyamshanov.comminusm.application.usecases.order

/**
 * Use Case: Check if a new order would overlap with existing orders.
 * Returns true if there is an overlap, false otherwise.
 */
interface CheckOrderOverlapUseCase {
    operator fun invoke(
        x: Int,
        y: Int,
        z: Int,
        radius: Int,
        worldName: String,
    ): Boolean
}
