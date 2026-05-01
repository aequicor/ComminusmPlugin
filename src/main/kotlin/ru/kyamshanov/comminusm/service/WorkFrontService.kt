package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

class WorkFrontService(
    private val repository: WorkFrontRepository,
    private val frontRadius: Int
) {
    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int): Boolean {
        repository.deleteByOwner(uuid)
        val front = WorkFront(uuid, world, x, y, z, frontRadius)
        repository.upsert(front)
        return true
    }

    fun getByOwner(uuid: UUID): WorkFront? = repository.findByOwner(uuid)

    fun deactivate(uuid: UUID) {
        repository.deleteByOwner(uuid)
    }

    fun getAllInWorld(world: String): List<WorkFront> = repository.findAllInWorld(world)
}
