package ru.kyamshanov.comminusm.commune.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class CommunePendingNotificationServiceTest {
    private lateinit var service: CommunePendingNotificationService

    @BeforeEach
    fun setup() {
        service = CommunePendingNotificationService()
    }

    @Test
    fun enqueueAndDrain_returnsSingleMessage() {
        val playerUuid = UUID.randomUUID()
        val message = "Test message"

        service.enqueue(playerUuid, message)
        val result = service.drain(playerUuid)

        assertEquals(listOf(message), result)
    }

    @Test
    fun drain_clearsQueue() {
        val playerUuid = UUID.randomUUID()

        service.enqueue(playerUuid, "Message 1")
        service.enqueue(playerUuid, "Message 2")

        val firstDrain = service.drain(playerUuid)
        val secondDrain = service.drain(playerUuid)

        assertEquals(listOf("Message 1", "Message 2"), firstDrain)
        assertEquals(emptyList(), secondDrain)
    }

    @Test
    fun drain_emptyQueueReturnsEmptyList() {
        val playerUuid = UUID.randomUUID()

        val result = service.drain(playerUuid)

        assertEquals(emptyList(), result)
    }

    @Test
    fun enqueue_differentPlayers_isolated() {
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()

        service.enqueue(player1, "Player 1 Message")
        service.enqueue(player2, "Player 2 Message")

        val player1Result = service.drain(player1)
        val player2Result = service.drain(player2)

        assertEquals(listOf("Player 1 Message"), player1Result)
        assertEquals(listOf("Player 2 Message"), player2Result)
    }

    @Test
    fun multipleMessages_allDeliveredInOrder() {
        val playerUuid = UUID.randomUUID()

        service.enqueue(playerUuid, "First")
        service.enqueue(playerUuid, "Second")
        service.enqueue(playerUuid, "Third")

        val result = service.drain(playerUuid)

        assertEquals(listOf("First", "Second", "Third"), result)
    }
}
