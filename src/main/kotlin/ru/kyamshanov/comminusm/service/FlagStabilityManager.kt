package ru.kyamshanov.comminusm.service

import org.bukkit.Location

/**
 * Service boundary interface for flag-stability integration.
 *
 * Consumed by [HomeTimerManager] and the upcoming OrderRespawnListener to
 * check flag state and resolve flag coordinates at the moment of use.
 *
 * Thread context: safe to call on main thread only.
 */
interface FlagStabilityManager {

    /**
     * Returns the [Location] of the active flag banner for the given order,
     * or null if no PDC entry exists, the stored position string cannot be
     * parsed, or the world is unloaded.
     *
     * When the world name stored in PDC is not resolvable a WARN-level log
     * entry must be emitted by the implementation (world name + order ID).
     *
     * Thread context: safe to call on main thread only.
     * PDC read is a synchronous in-memory operation.
     */
    fun getFlagLocation(orderId: Long): Location?

    /**
     * Returns true if the flag for [orderId] is currently in an active /
     * registered state according to the flag-stability internal activation
     * registry — not merely whether a PDC key is present.
     *
     * Thread context: safe to call on main thread only.
     */
    fun isFlagActive(orderId: Long): Boolean
}
