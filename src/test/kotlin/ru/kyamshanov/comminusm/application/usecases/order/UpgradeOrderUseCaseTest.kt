package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID

/**
 * Unit tests for UpgradeOrderUseCase.
 * Verifies error messages do not leak resource counts.
 */
class UpgradeOrderUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val workdaysRepository = mockk<WorkdaysRepository>()
    private val levels =
        listOf(
            OrderLevelConfig(level = 1, cost = 10, radius = 100),
            OrderLevelConfig(level = 2, cost = 20, radius = 200),
            OrderLevelConfig(level = 3, cost = 30, radius = 300),
        )
    private val useCase = UpgradeOrderUseCaseImpl(orderRepository, workdaysRepository, levels)

    @Test
    fun `should not leak resource counts in insufficient workdays error`() {
        val ownerUuid = UUID.randomUUID()
        val order =
            Order(
                id = 1,
                ownerUuid = ownerUuid,
                level = 1,
                radius = 100,
            )

        every { orderRepository.findByOwner(ownerUuid) } returns order
        every { workdaysRepository.getBalance(ownerUuid) } returns 5 // Less than cost of 20

        val result = useCase.invoke(ownerUuid)

        assertTrue(result is Result.Failure)
        val errorMessage = (result as? Result.Failure)?.error ?: ""
        // Message should be generic, NOT contain specific numbers
        assertFalse(
            errorMessage.contains("need") || errorMessage.contains("have"),
            "Error message should not leak resource counts, got: $errorMessage",
        )
        assertTrue(errorMessage.contains("Insufficient"))
    }

    @Test
    fun `should upgrade order successfully when enough workdays available`() {
        val ownerUuid = UUID.randomUUID()
        val order =
            Order(
                id = 1,
                ownerUuid = ownerUuid,
                level = 1,
                radius = 100,
            )

        every { orderRepository.findByOwner(ownerUuid) } returns order
        every { workdaysRepository.getBalance(ownerUuid) } returns 50 // More than cost of 20
        every { workdaysRepository.spend(ownerUuid, 20) } returns true
        every { orderRepository.updateLevel(ownerUuid, 2, 200) } returns Unit

        val result = useCase.invoke(ownerUuid)

        assertTrue(result is Result.Success)
    }
}
