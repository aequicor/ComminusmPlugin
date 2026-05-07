@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of RenameOrderUseCase.
 * Validates the new name and checks ownership.
 * Does NOT perform DB write — that is handled asynchronously by OrderRenameMenu (R8 mitigation).
 */
class RenameOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
) : RenameOrderUseCase {
    companion object {
        private const val MAX_NAME_LENGTH = 20
    }

    override fun invoke(
        ownerUuid: UUID,
        newName: String,
    ): Result<Unit> {
        when {
            newName.isBlank() || newName.all { it.isWhitespace() } ->
                return Result.failure("empty")
            newName.length > MAX_NAME_LENGTH ->
                return Result.failure("too_long")
            !newName.matches(Regex("[A-Za-zА-Яа-яЁё0-9\\-_]+")) ->
                return Result.failure("invalid_chars")
        }

        val order =
            orderRepository.findByOwner(ownerUuid)
                ?: return Result.failure("not_found")

        if (order.ownerUuid != ownerUuid) {
            return Result.failure("unauthorized")
        }

        // AC-19: case-sensitive, same name = no-op
        if (newName == order.name) {
            return Result.success(Unit)
        }

        return Result.success(Unit)
    }
}
