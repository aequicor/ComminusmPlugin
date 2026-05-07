package ru.kyamshanov.comminusm.commune.service

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing commune chat operations.
 * Handles message broadcasting and toggle mode state.
 *
 * Addresses: TC-10, TC-31, TC-32, TC-33, TC-79, CC-09
 */
interface CommuneChatService {
    /**
     * Toggle mode (persistent per session, reset on leave commune)
     * Addresses AC-18b, AC-18c, CC-12
     */
    fun setToggleMode(
        playerUUID: UUID,
        enabled: Boolean,
    )

    fun getToggleMode(playerUUID: UUID): Boolean

    /**
     * Broadcast message to all online commune members
     * Addresses TC-10, AC-31, CC-09
     */
    fun broadcastToCommune(
        communeId: UUID,
        sender: Player,
        text: String,
    )

    /**
     * Plain-text wrapping (CC-09) — no MiniMessage parsing
     * MiniMessage tags and legacy color codes treated as literal text
     */
    fun wrapPlainText(text: String): String

    /**
     * Reset toggles when player's commune dissolved (§6.7, CC-15)
     */
    fun resetToggleMode(playerUUID: UUID)

    /**
     * Check if player is in a commune (convenience method)
     */
    fun isInCommune(playerUUID: UUID): Boolean
}

/**
 * Default implementation of CommuneChatService.
 * Uses in-memory toggle state (reset on reload per CC-12).
 */
class CommuneChatServiceImpl(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
    private val pendingNotifications: CommunePendingNotificationService,
) : CommuneChatService {
    // In-memory toggle state: Set<UUID> of playerUUIDs with toggle mode active
    // Addresses CC-12: reset on reload
    private val toggleModes = ConcurrentHashMap<UUID, Boolean>()

    override fun setToggleMode(
        playerUUID: UUID,
        enabled: Boolean,
    ) {
        toggleModes[playerUUID] = enabled
    }

    override fun getToggleMode(playerUUID: UUID): Boolean = toggleModes.getOrDefault(playerUUID, false)

    override fun resetToggleMode(playerUUID: UUID) {
        toggleModes.remove(playerUUID)
    }

    override fun isInCommune(playerUUID: UUID): Boolean {
        val orders = orderMembershipService.getNativeOrdersOfPlayer(playerUUID)
        return orders.any { communeService.getCommuneOfOrder(it) != null }
    }

    override fun wrapPlainText(text: String): String {
        // CC-09: Plain-text wrapping — no parsing of MiniMessage tags or legacy codes
        // Input text is treated as literal; output is the same text
        return text
    }

    override fun broadcastToCommune(
        communeId: UUID,
        sender: Player,
        text: String,
    ) {
        // Load commune by ID — explicit null check (CRITICAL #2)
        val commune = communeService.getCommune(communeId)
        if (commune == null) {
            return
        }

        // Load online players who are members of this commune
        val communeOrderIds = communeService.getCommuneOrders(communeId)

        val communeMembers =
            Bukkit
                .getOnlinePlayers()
                .filter { player ->
                    val playerNativeOrders = orderMembershipService.getNativeOrdersOfPlayer(player.uniqueId)
                    playerNativeOrders.any { it in communeOrderIds }
                }

        // Format message using Component API (not hardcoded ChatColor)
        val wrappedText = wrapPlainText(text)

        // Build message with Component API (spec §6.15 lines 700-701)
        // Format: [Commune] sender_name: message with colors
        val component =
            Component
                .text()
                .append(Component.text("[").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Коммуна").color(NamedTextColor.GREEN))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(sender.name).color(NamedTextColor.YELLOW))
                .append(Component.text(": ").color(NamedTextColor.GRAY))
                .append(Component.text(wrappedText).color(NamedTextColor.WHITE))
                .build()

        communeMembers.forEach { it.sendMessage(component) }

        // CC-14: Queue plain-text message for offline native commune members
        val offlineText = "[Коммуна] ${sender.name}: $wrappedText"
        val allNativeMemberUuids = mutableSetOf<UUID>()
        for (orderId in communeOrderIds) {
            val members = orderMembershipService.getMembersOfOrder(orderId)
            allNativeMemberUuids.addAll(
                members.filter { it.grantedVia == "native" }.map { it.playerUuid },
            )
        }
        allNativeMemberUuids
            .filter { uuid -> Bukkit.getPlayer(uuid) == null }
            .forEach { uuid -> pendingNotifications.enqueue(uuid, offlineText) }
    }
}
