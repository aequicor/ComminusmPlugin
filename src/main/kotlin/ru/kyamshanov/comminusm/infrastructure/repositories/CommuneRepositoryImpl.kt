package ru.kyamshanov.comminusm.infrastructure.repositories

import ru.kyamshanov.comminusm.commune.model.Commune
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

    override fun findByName(name: String): Commune? {
        // Name is not a property of Commune in the current model,
        // so this is a no-op placeholder for future use
        return null
    }

    override fun findAllByMember(memberId: UUID): List<Commune> = communes.values.filter { it.createdBy == memberId }

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
