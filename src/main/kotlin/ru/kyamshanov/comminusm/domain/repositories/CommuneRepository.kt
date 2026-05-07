package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.entities.Commune
import java.util.UUID

/**
 * Domain repository interface for Commune entities.
 * Defines contracts for commune persistence operations without exposing implementation details.
 */
interface CommuneRepository {
    fun findById(id: UUID): Commune?

    fun findByName(name: String): Commune?

    fun findAllByMember(memberId: UUID): List<Commune>

    fun insert(commune: Commune): UUID

    fun update(commune: Commune)

    fun delete(id: UUID)
}
