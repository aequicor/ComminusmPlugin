@file:Suppress("PackageName")

package ru.kyamshanov.comminusm.domain.value_objects

import java.util.UUID

/**
 * Domain value object representing a player's workdays balance.
 *
 * @param uuid UUID of the player
 * @param amount Number of workdays available
 */
data class WorkdaysBalance(
    val uuid: UUID,
    val amount: Int = 0,
)
