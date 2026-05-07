package ru.kyamshanov.comminusm.infrastructure.repositories

import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of CommuneRepository.
 * Serves as the persistent store for commune entities.
 * Currently uses in-memory storage; may be migrated to SQL in a future stage.
 */
class CommuneRepositoryImpl(
    private val communes: MutableMap<UUID, Commune> = ConcurrentHashMap(),
) : CommuneRepository {
    override fun findById(id: UUID): Commune? = communes[id]

    override fun findByName(name: String): Commune? = communes.values.firstOrNull { it.name == name }

    override fun findAllByMember(memberId: UUID): List<Commune> =
        communes.values.filter { it.memberIds.contains(memberId) || it.ownerId == memberId }

    override fun insert(commune: Commune): UUID {
        communes[commune.id] = commune
        return commune.id
    }

    override fun update(commune: Commune) {
        communes[commune.id] = commune
    }

    override fun delete(id: UUID) {
        communes.remove(id)
    }
}
