package ru.kyamshanov.comminusm.commune.service

import java.util.UUID

/**
 * Service for managing muted players.
 * Determines if a player is currently muted from commune chat.
 *
 * Addresses: CC-10 (player mute check)
 */
interface MuteService {
    /**
     * Check if a player is muted.
     *
     * @param playerUuid The UUID of the player to check
     * @return true if the player is muted, false otherwise
     */
    fun isMuted(playerUuid: UUID): Boolean
}
