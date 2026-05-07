package ru.kyamshanov.comminusm.commune.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory queue for commune notifications that could not be delivered because
 * the target player was offline at the time of broadcast.
 * Drained on PlayerJoinEvent (CC-14).
 */
class CommunePendingNotificationService {
    private val queue = ConcurrentHashMap<UUID, MutableList<String>>()

    /** Queue a notification for a player who is currently offline. */
    fun enqueue(
        playerUuid: UUID,
        message: String,
    ) {
        queue.getOrPut(playerUuid) { mutableListOf() }.add(message)
    }

    /** Remove and return all queued notifications for a player. Returns empty list if none. */
    fun drain(playerUuid: UUID): List<String> = queue.remove(playerUuid) ?: emptyList()
}
