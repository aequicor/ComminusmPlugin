package ru.kyamshanov.comminusm.commune.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.application.usecases.commune.BroadcastToCommuneUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.GetToggleModeUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase

/**
 * Listener for AsyncChatEvent that handles commune chat messages.
 *
 * Implements AC-18: "Commune chat mode - toggle and single message dispatch"
 *
 * Processes async chat events and routes messages to commune members when
 * the player has commune chat toggle mode enabled.
 */
class AsyncChatEventListener(
    private val getToggleModeUseCase: GetToggleModeUseCase,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase,
    private val broadcastToCommuneUseCase: BroadcastToCommuneUseCase,
) : Listener {
    companion object {
        // CC-08: Message length limit (max 256 chars per spec §6.15)
        private const val MESSAGE_LENGTH_LIMIT = 256
    }

    /**
     * Handles AsyncChatEvent for commune chat routing.
     *
     * Processes player chat messages and routes them through the commune chat service
     * if the player is part of a commune and has commune chat toggle mode enabled.
     * When commune chat is active, the message is sent only to commune members.
     *
     * Implements CC-08 (High): validates message length (max 256 chars) and rejects
     * blank or oversized messages per spec §6.15.
     *
     * @param event The async chat event from Paper API
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onAsyncPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val playerUUID = player.uniqueId

        // Check if commune chat toggle mode is enabled — skip if not
        if (!getToggleModeUseCase(playerUUID)) {
            return
        }

        // Extract message text from Component
        // Component API: convert to plain text for length validation
        val messageComponent = event.message()
        val plainTextSerializer =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
        val messageText = plainTextSerializer.serialize(messageComponent)

        // CC-08: Validate message length and blanks
        when {
            messageText.isBlank() -> {
                event.isCancelled = true
                player.sendMessage(Component.text("Сообщение не может быть пустым", NamedTextColor.RED))
            }

            messageText.length > MESSAGE_LENGTH_LIMIT -> {
                event.isCancelled = true
                player.sendMessage(
                    Component.text(
                        "Сообщение слишком длинное (макс 256 символов)",
                        NamedTextColor.RED,
                    ),
                )
            }

            else -> {
                // Find player's native order and check if they're in a commune
                val playerOrder = getOrderByOwnerUseCase(playerUUID)
                val commune = playerOrder?.let { getCommuneOfOrderUseCase(it.id) }

                // Route to all online commune members
                if (commune != null) {
                    broadcastToCommuneUseCase(commune.id, playerUUID, messageText)
                }
            }
        }
    }
}
