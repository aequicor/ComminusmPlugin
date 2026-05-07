package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import java.util.UUID

class WorkdaysService(
    private val repository: WorkdaysRepository,
) {
    fun earn(
        uuid: UUID,
        amount: Int,
    ) {
        repository.add(uuid, amount)
    }

    fun spend(
        uuid: UUID,
        amount: Int,
    ): Boolean = repository.spend(uuid, amount)

    fun getBalance(uuid: UUID): Int = repository.getBalance(uuid)

    fun hasEnough(
        uuid: UUID,
        required: Int,
    ): Boolean = getBalance(uuid) >= required
}
