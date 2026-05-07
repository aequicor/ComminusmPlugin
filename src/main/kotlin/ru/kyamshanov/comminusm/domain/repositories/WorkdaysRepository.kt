package ru.kyamshanov.comminusm.domain.repositories

import java.util.UUID

/**
 * Domain repository interface for Workdays balance entities.
 * Defines contracts for workdays persistence operations without exposing implementation details.
 */
interface WorkdaysRepository {
    fun add(
        uuid: UUID,
        amount: Int,
    )

    fun spend(
        uuid: UUID,
        amount: Int,
    ): Boolean

    fun getBalance(uuid: UUID): Int
}
