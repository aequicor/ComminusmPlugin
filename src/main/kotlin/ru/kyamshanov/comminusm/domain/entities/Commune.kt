package ru.kyamshanov.comminusm.domain.entities

import java.util.UUID

/**
 * Domain entity for Commune.
 * Represents a collective alliance of members without Bukkit dependencies.
 *
 * @param id Unique commune identifier
 * @param name Name of the commune
 * @param ownerId UUID of the player who owns the commune
 * @param memberIds Set of member UUIDs in the commune
 * @param createdAt Timestamp of creation (milliseconds since epoch)
 */
data class Commune(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val ownerId: UUID,
    val memberIds: Set<UUID> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
)
