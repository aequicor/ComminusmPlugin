package ru.kyamshanov.comminusm.commune.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents a commune — an alliance of multiple orders.
 * Communes exist primarily in memory, with async persistence to storage.
 *
 * @param id Unique commune identifier
 * @param orderIds Set of order IDs that are members of this commune
 * @param version Monotonic counter incremented on each mutation (used for stale-state detection)
 * @param createdAt Timestamp when the commune was created
 * @param createdBy UUID of the player who created the commune
 */
data class Commune(
    val id: UUID,
    val orderIds: Set<Long>,
    val version: Long,
    val createdAt: LocalDateTime,
    val createdBy: UUID,
)
