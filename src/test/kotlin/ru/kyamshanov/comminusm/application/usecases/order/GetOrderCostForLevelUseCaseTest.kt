package ru.kyamshanov.comminusm.application.usecases.order

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import kotlin.test.assertEquals

class GetOrderCostForLevelUseCaseTest {
    private lateinit var levels: List<OrderLevelConfig>
    private lateinit var useCase: GetOrderCostForLevelUseCaseImpl

    @BeforeEach
    fun setUp() {
        levels =
            listOf(
                OrderLevelConfig(level = 1, radius = 50, cost = 100),
                OrderLevelConfig(level = 2, radius = 75, cost = 250),
                OrderLevelConfig(level = 3, radius = 100, cost = 500),
            )
        useCase = GetOrderCostForLevelUseCaseImpl(levels)
    }

    @Test
    fun `should return cost for existing level`() {
        // Act
        val cost = useCase(1)

        // Assert
        assertEquals(100, cost)
    }

    @Test
    fun `should return cost for different existing level`() {
        // Act
        val cost = useCase(2)

        // Assert
        assertEquals(250, cost)
    }

    @Test
    fun `should return zero for non-existing level`() {
        // Act
        val cost = useCase(99)

        // Assert
        assertEquals(0, cost)
    }

    @Test
    fun `should handle negative level`() {
        // Act
        val cost = useCase(-1)

        // Assert
        assertEquals(0, cost)
    }
}
