package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.commune.service.CommuneChatService

/**
 * Listener for AsyncChatEvent that handles commune chat messages.
 * Integrates with CommuneChatService to route messages to the correct channel.
 *
 * Implements AC-18: "Commune chat mode - toggle and single message dispatch"
 *
 * Stub implementation: `communeChatService` is retained for future AC-18 handler implementation.
 * Once Paper AsyncChatEvent handler is added, this service will be used to route messages.
 */
class AsyncChatEventListener(
    @Suppress("UnusedPrivateProperty")
    private val communeChatService: CommuneChatService,
) : Listener {
    // Note: AsyncChatEvent implementation would go here
    // Handles /cc message routing in async chat mode
    // Addresses AC-18: "Commune chat mode - toggle and single message dispatch"
    // This is a stub implementation; full implementation requires Paper AsyncChatEvent
}
