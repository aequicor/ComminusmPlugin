package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.storage.WorkdaysRepository
import java.util.UUID

class WorkdaysService(private val repository: WorkdaysRepository) {

    fun earn(uuid: UUID, amount: Int) {
        repository.add(uuid, amount)
    }

    fun spend(uuid: UUID, amount: Int): Boolean {
        return repository.spend(uuid, amount)
    }

    fun getBalance(uuid: UUID): Int {
        return repository.getBalance(uuid)
    }

    fun hasEnough(uuid: UUID, required: Int): Boolean {
        return getBalance(uuid) >= required
    }
}
