package ru.kyamshanov.comminusm.commune.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommuneChatServiceTest {
    private lateinit var communeService: CommuneService
    private lateinit var membershipService: OrderMembershipService
    private lateinit var pendingNotifications: CommunePendingNotificationService
    private lateinit var service: CommuneChatServiceImpl

    @BeforeEach
    fun setUp() {
        communeService = mockk()
        membershipService = mockk()
        pendingNotifications = CommunePendingNotificationService()
        service = CommuneChatServiceImpl(communeService, membershipService, pendingNotifications)
    }

    @Test
    fun testToggleModeOffByDefault() {
        val playerUuid = UUID.randomUUID()
        assertFalse(service.getToggleMode(playerUuid))
    }

    @Test
    fun testToggleModeSetOn() {
        val playerUuid = UUID.randomUUID()
        service.setToggleMode(playerUuid, true)
        assertTrue(service.getToggleMode(playerUuid))
    }

    @Test
    fun testToggleModeSetOff() {
        val playerUuid = UUID.randomUUID()
        service.setToggleMode(playerUuid, true)
        service.setToggleMode(playerUuid, false)
        assertFalse(service.getToggleMode(playerUuid))
    }

    @Test
    fun testResetToggleMode() {
        val playerUuid = UUID.randomUUID()
        service.setToggleMode(playerUuid, true)
        service.resetToggleMode(playerUuid)
        assertFalse(service.getToggleMode(playerUuid))
    }

    @Test
    fun testWrapPlainTextPassesLiteralText() {
        // TC-79: Text is passed through Component.text() literally, no modifications
        val text = "Hello world"
        val wrapped = service.wrapPlainText(text)
        assertEquals(text, wrapped, "Plain text should be returned unchanged")
    }

    @Test
    fun testWrapPlainTextPreservesColorCodes() {
        // TC-79: Legacy § codes and MiniMessage tags should be preserved as literal text
        val text = "§cRed text"
        val wrapped = service.wrapPlainText(text)
        assertEquals(text, wrapped, "Color codes should be preserved as literal characters")
    }

    @Test
    fun testWrapPlainTextPreservesMiniMessageTags() {
        // TC-79: MiniMessage tags are shown literally, not parsed
        val text = "<red>Colored text</red>"
        val wrapped = service.wrapPlainText(text)
        assertEquals(text, wrapped, "MiniMessage tags should be preserved as literal text")
    }

    @Test
    fun testWrapPlainTextPreservesClickCommands() {
        // CC-09: Click commands in tags are not parsed, shown literally
        val text = "<click:run_command:/admin>Click here</click>"
        val wrapped = service.wrapPlainText(text)
        assertEquals(text, wrapped, "Click commands should be preserved as literal text")
    }

    @Test
    fun testWrapPlainTextEmptyString() {
        val text = ""
        val wrapped = service.wrapPlainText(text)
        assertEquals("", wrapped, "Empty text should remain empty")
    }

    @Test
    fun testBroadcastToCommuneNullCommune() {
        // Should handle null commune gracefully
        val communeId = UUID.randomUUID()
        val sender = mockk<Player>()

        every { communeService.getCommune(communeId) } returns null

        // Should not throw
        service.broadcastToCommune(communeId, sender, "test message")
    }

    @Test
    fun testIsInCommuneReturnsTrueWhenPlayerInCommune() {
        // CRITICAL #1: Test isInCommune returns true when player is in a commune
        val playerUuid = UUID.randomUUID()
        val orderId = 42L

        every { membershipService.getNativeOrdersOfPlayer(playerUuid) } returns setOf(orderId)
        every { communeService.getCommuneOfOrder(orderId) } returns mockk() // Non-null means in commune

        assertTrue(
            service.isInCommune(playerUuid),
            "isInCommune should return true when player has an order in a commune",
        )
    }

    @Test
    fun testIsInCommuneReturnsFalseWhenPlayerNotInCommune() {
        // CRITICAL #1: Test isInCommune returns false when player is not in a commune
        val playerUuid = UUID.randomUUID()

        every { membershipService.getNativeOrdersOfPlayer(playerUuid) } returns emptySet()

        assertFalse(service.isInCommune(playerUuid), "isInCommune should return false when player has no orders")
    }

    @Test
    fun testIsInCommuneReturnsFalseWhenOrderNotInCommune() {
        // CRITICAL #1: Test isInCommune returns false when player's order is not in a commune
        val playerUuid = UUID.randomUUID()
        val orderId = 42L

        every { membershipService.getNativeOrdersOfPlayer(playerUuid) } returns setOf(orderId)
        every { communeService.getCommuneOfOrder(orderId) } returns null // Order not in any commune

        assertFalse(service.isInCommune(playerUuid), "isInCommune should return false when order is not in a commune")
    }

    @Test
    fun testBroadcastToCommuneDeliversToCorrectRecipients() {
        // CRITICAL #2: Verify broadcast delivers to correct recipients (spec §6.15)
        // Note: This test verifies the membership filtering logic by checking that
        // only players with orders in the commune are contacted for sending messages
        val communeId = UUID.randomUUID()
        val orderId = 42L
        val player1Uuid = UUID.randomUUID()
        val player2Uuid = UUID.randomUUID()
        val player3Uuid = UUID.randomUUID()

        // Setup mocks
        every { communeService.getCommune(communeId) } returns mockk() // Commune exists
        every { communeService.getCommuneOrders(communeId) } returns setOf(orderId)

        // Track which players would receive messages
        val recipientUuids = mutableListOf<UUID>()

        // Create a custom PlayerCollection for testing
        val playerList = mutableListOf<Player>()
        for (uuid in listOf(player1Uuid, player2Uuid, player3Uuid)) {
            val player =
                mockk<Player> {
                    every { uniqueId } returns uuid
                    every { sendMessage(any<Component>()) } answers {
                        recipientUuids.add(uuid)
                    }
                }
            playerList.add(player)
        }

        // Mock getOnlinePlayers through static import (less intrusive)
        io.mockk.mockkStatic(org.bukkit.Bukkit::class)
        every { org.bukkit.Bukkit.getOnlinePlayers() } returns playerList
        every { org.bukkit.Bukkit.getPlayer(any<UUID>()) } returns null // For offline checks in pending notifications

        // Setup membership: players 1,2 in commune order; player 3 not
        every { membershipService.getNativeOrdersOfPlayer(player1Uuid) } returns setOf(orderId)
        every { membershipService.getNativeOrdersOfPlayer(player2Uuid) } returns setOf(orderId)
        every { membershipService.getNativeOrdersOfPlayer(player3Uuid) } returns emptySet()
        // Mock getMembersOfOrder for offline notification queueing
        every { membershipService.getMembersOfOrder(orderId) } returns emptySet()

        val sender =
            mockk<Player> {
                every { name } returns "TestPlayer"
            }

        // Broadcast
        service.broadcastToCommune(communeId, sender, "Test message")

        // Verify correct recipients
        assertTrue(recipientUuids.contains(player1Uuid), "Player 1 should receive message")
        assertTrue(recipientUuids.contains(player2Uuid), "Player 2 should receive message")
        assertFalse(recipientUuids.contains(player3Uuid), "Player 3 should NOT receive message")

        // Clean up mocks
        io.mockk.unmockkStatic(org.bukkit.Bukkit::class)
    }

    @Test
    fun testBroadcastFormatsMessageWithComponentAPI() {
        // CRITICAL #3: Verify message format compliance (Component API requirement)
        val communeId = UUID.randomUUID()
        val orderId = 42L
        val senderName = "TestPlayer"
        val messageText = "Hello commune"
        val recipientUuid = UUID.randomUUID()

        // Setup mocks
        every { communeService.getCommune(communeId) } returns mockk()
        every { communeService.getCommuneOrders(communeId) } returns setOf(orderId)

        // Capture the sent components
        val sentComponents = mutableListOf<Component>()
        val recipientPlayer =
            mockk<Player> {
                every { uniqueId } returns recipientUuid
                every { sendMessage(any<Component>()) } answers {
                    sentComponents.add(firstArg())
                }
            }

        // Mock Bukkit.getOnlinePlayers() using static mock
        io.mockk.mockkStatic(org.bukkit.Bukkit::class)
        every { org.bukkit.Bukkit.getOnlinePlayers() } returns listOf(recipientPlayer)
        every { org.bukkit.Bukkit.getPlayer(any<UUID>()) } returns null // For offline checks in pending notifications

        every { membershipService.getNativeOrdersOfPlayer(recipientUuid) } returns setOf(orderId)
        // Mock getMembersOfOrder for offline notification queueing
        every { membershipService.getMembersOfOrder(orderId) } returns emptySet()

        val sender =
            mockk<Player> {
                every { name } returns senderName
            }

        // Broadcast
        service.broadcastToCommune(communeId, sender, messageText)

        // Verify component was sent (not a legacy string message)
        assertTrue(sentComponents.isNotEmpty(), "Component message should be sent, not legacy string")

        // Verify it's a proper Component with content
        val sentComponent = sentComponents[0]
        val componentStr = sentComponent.toString()
        assertTrue(componentStr.isNotEmpty(), "Component should have content")
        // Verify Component is not a bare string (Component API uses different structure)
        assertTrue(
            componentStr.contains("Component") || componentStr.contains("text"),
            "Should be a Component, not a plain string message",
        )

        // Clean up mocks
        io.mockk.unmockkStatic(org.bukkit.Bukkit::class)
    }
}
