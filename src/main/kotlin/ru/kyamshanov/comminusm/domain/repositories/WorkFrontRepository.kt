package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.model.WorkFront
import java.util.UUID

/**
 * Domain repository interface for WorkFront entities.
 * Defines contracts for work front persistence operations without exposing implementation details.
 */
interface WorkFrontRepository {
    fun findByOwner(uuid: UUID): WorkFront?
    fun findAllInWorld(world: String): List<WorkFront>
    fun findAllActivated(): List<WorkFront>
    fun upsert(front: WorkFront)
    fun deleteByOwner(uuid: UUID)
}
