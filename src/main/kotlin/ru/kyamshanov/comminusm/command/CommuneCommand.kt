package ru.kyamshanov.comminusm.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.MuteService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID
import java.util.logging.Logger

/**
 * Command handler for `/cc [text]` — commune chat.
 *
 * Addresses: TC-10, TC-31, TC-32, TC-33, TC-34, TC-78, TC-79, TC-80, TC-124
 * AC-18, AC-18b, AC-18c, AC-19, CC-08, CC-09, CC-10, CC-21
 *
 * Usage:
 * - `/cc <text>` — send single message to commune chat
 * - `/cc` (no args or whitespace-only) — toggle "always in commune chat" mode
 *
 * Pre-conditions checked:
 * 1. Sender is a Player
 * 2. Player's native order is in a commune
 * 3. Player is not muted (CC-10)
 * 4. For single-message: text length ≤ 256 chars (CC-08)
 */
class CommuneCommand(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
    private val communeChatService: CommuneChatService,
    private val muteService: MuteService? = null, // Optional mute service
) : CommandExecutor {
    private val logger = Logger.getLogger(CommuneCommand::class.java.name)

    companion object {
        private const val MAX_MESSAGE_LENGTH = 256
    }

    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cЭта команда доступна только игрокам")
            return true
        }

        val player = sender
        val playerUuid = player.uniqueId
        val validationError = validatePreconditions(playerUuid)

        return if (validationError != null) {
            player.sendMessage(validationError)
            true
        } else {
            val playerCommune = getPlayerCommune(playerUuid)
            requireNotNull(playerCommune) // guaranteed by validatePreconditions
            when {
                args.isEmpty() || args.joinToString(" ").trim().isEmpty() ->
                    handleToggleMode(player, playerUuid)
                else -> handleSendMessage(player, args, playerCommune)
            }
        }
    }

    /**
     * Validate preconditions and return error message if any check fails.
     * Returns null if all checks pass.
     */
    private fun validatePreconditions(playerUuid: UUID): String? {
        val playerCommune = getPlayerCommune(playerUuid)
        return when {
            playerCommune == null -> "§cВы не состоите ни в одной коммуне"
            isMuted(playerUuid) -> "§cВы в муте и не можете писать в коммуне"
            else -> null
        }
    }

    /**
     * Get the commune of player's native order, or null if not in a commune.
     */
    private fun getPlayerCommune(playerUuid: UUID): ru.kyamshanov.comminusm.commune.model.Commune? {
        val nativeOrders = orderMembershipService.getNativeOrdersOfPlayer(playerUuid)
        return nativeOrders
            .asSequence()
            .mapNotNull { communeService.getCommuneOfOrder(it) }
            .firstOrNull()
    }

    /**
     * Handle toggle mode command (AC-18b, AC-18c, TC-124, CC-21).
     */
    private fun handleToggleMode(
        player: Player,
        playerUuid: UUID,
    ): Boolean {
        val currentMode = communeChatService.getToggleMode(playerUuid)
        communeChatService.setToggleMode(playerUuid, !currentMode)
        if (!currentMode) {
            player.sendMessage("§aВы в режиме чата коммуны. Введите /cc для выхода")
        } else {
            player.sendMessage("§aВы вышли из режима чата коммуны")
        }
        return true
    }

    /**
     * Handle sending a single message to commune chat (TC-10, TC-31, CC-08).
     */
    private fun handleSendMessage(
        player: Player,
        args: Array<String>,
        playerCommune: ru.kyamshanov.comminusm.commune.model.Commune,
    ): Boolean {
        val messageText = args.joinToString(" ")

        // Check 4: Message length ≤ 256 chars (CC-08)
        if (messageText.trim().length > MAX_MESSAGE_LENGTH) {
            player.sendMessage("§cСообщение слишком длинное")
            return true
        }

        // Broadcast to commune
        return try {
            communeChatService.broadcastToCommune(
                playerCommune.id,
                player,
                messageText,
            )
            true
        } catch (e: IllegalStateException) {
            logger.warning("Failed to broadcast commune message: ${e.message}")
            player.sendMessage("§cОшибка при отправке сообщения")
            true
        }
    }

    /**
     * Check if player is muted.
     * Uses MuteService if provided; otherwise returns false.
     */
    private fun isMuted(playerUuid: UUID): Boolean = muteService?.isMuted(playerUuid) ?: false
}
