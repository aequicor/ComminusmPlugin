package ru.kyamshanov.comminusm.application.usecases.workdays

import java.util.UUID

/**
 * Use Case: Get a player's current workdays balance.
 * Returns 0 if the player has no balance record.
 */
interface GetWorkdaysBalanceUseCase {
    operator fun invoke(uuid: UUID): Int
}
