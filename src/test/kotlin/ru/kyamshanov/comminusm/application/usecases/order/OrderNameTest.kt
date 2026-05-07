package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID

class OrderNameTest {
    @Test
    fun `sanitizeNickname with valid name returns name as-is`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("Player")
        assertEquals("Player", result)
    }

    @Test
    fun `sanitizeNickname with invalid chars replaces with underscore`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("Player#123")
        assertEquals("Player_123", result)
    }

    @Test
    fun `sanitizeNickname with all invalid chars returns fallback Order`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("###")
        assertEquals("Order", result)
    }

    @Test
    fun `sanitizeNickname with name longer than 20 chars truncates to 20`() {
        val longName = "A".repeat(25)
        val result = CreateOrderUseCaseImpl.sanitizeNickname(longName)
        assertEquals(20, result.length)
        assertEquals("A".repeat(20), result)
    }

    @Test
    fun `sanitizeNickname with name longer than 20 chars with invalid chars in second half truncates and replaces`() {
        val name = "ValidName1234567890###" // 22 chars total
        val result = CreateOrderUseCaseImpl.sanitizeNickname(name)
        assertEquals(20, result.length)
        assertTrue(result.startsWith("ValidName123456789"))
    }

    @Test
    fun `sanitizeNickname with empty string returns fallback Order`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("")
        assertEquals("Order", result)
    }

    @Test
    fun `sanitizeNickname with whitespace only returns fallback Order`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("   ")
        assertEquals("Order", result)
    }

    @Test
    fun `sanitizeNickname with Latin and Cyrillic letters accepts both`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("WarriorДружина")
        assertEquals("WarriorДружина", result)
    }

    @Test
    fun `sanitizeNickname allows digits`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("Player123")
        assertEquals("Player123", result)
    }

    @Test
    fun `sanitizeNickname allows hyphens and underscores`() {
        val result = CreateOrderUseCaseImpl.sanitizeNickname("Player-Name_1")
        assertEquals("Player-Name_1", result)
    }

    @Test
    fun `invoke creates order with sanitized player name`() {
        val playerUuid = UUID.randomUUID()
        val playerName = "TestPlayer#123"
        val expectedName = "TestPlayer_123"
        val mockRepo = mockk<OrderRepository>()
        val levels =
            listOf(
                OrderLevelConfig(level = 1, cost = 0, radius = 5),
            )

        every { mockRepo.findByOwner(playerUuid) } returns null
        every { mockRepo.insert(any()) } returns 1L

        val useCase = CreateOrderUseCaseImpl(mockRepo, levels)
        val result = useCase(playerUuid, playerName)

        assertTrue(result is Result.Success)
        val order = (result as Result.Success).data
        assertEquals(expectedName, order.name)
    }
}
