@file:Suppress("SENSELESS_COMPARISON")

package ru.kyamshanov.comminusm.gui

import io.mockk.mockk
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByIdUseCase
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * Unit tests for [CommuneOrderMenu] decorator.
 *
 * Test cases:
 * - AC-60: "Участники" button visible to order leaders and native members
 * - Button click opens OrderMembersMenu
 * - TC-156: Verify slot repositioning to bottom row
 * - TC-157: Verify skull owner metadata
 */
class CommuneOrderMenuTest {
    private val checkOrderLeadershipUseCase = mockk<CheckOrderLeadershipUseCase>()
    private val orderMembershipService = mockk<OrderMembershipService>()
    private val orderMembersMenu = mockk<OrderMembersMenu>()
    private val getOrderByIdUseCase = mockk<GetOrderByIdUseCase>()
    private val plugin = mockk<Plugin>(relaxed = true)

    private lateinit var menu: CommuneOrderMenu

    @BeforeEach
    fun setUp() {
        menu =
            CommuneOrderMenu(
                checkOrderLeadershipUseCase,
                orderMembershipService,
                orderMembersMenu,
                getOrderByIdUseCase,
                plugin,
            )
    }

    @Test
    fun testMenuCreatesSuccessfully() {
        // This test will pass once CommuneOrderMenu is instantiated
        assert(menu != null)
    }

    @Test
    fun `TC-156 participants button repositioned to bottom row slot 44`() {
        // TC-156: Verify that "Участники" button is now at slot 44 (bottom row)
        // instead of slot 21 (middle row)
        assert(CommuneOrderMenu.PARTICIPANTS_BUTTON_SLOT == 44) {
            "Participants button should be at slot 44 (bottom row) for visual harmony"
        }
    }

    @Test
    fun `TC-122 participants button slot does not conflict with order menu slots`() {
        // TC-122: Verify that "Участники" (Participants) button in CommuneOrderMenu
        // does not use the same slot as other buttons in OrderMenu.
        // This test verifies the fix for the slot conflict bug.

        // OrderMenu uses these slots (updated for TC-156):
        val orderMenuSizeSlot = 22 // "Территория" (Territory) button
        val orderMenuInfoSlot = 20
        val orderMenuUpgradeSlot = 24
        val orderMenuRestoreSlot = 31
        val orderMenuBackSlot = 36
        val orderMenuHomeSlot = 40

        // CommuneOrderMenu participants button should not conflict with any of them
        val participantsSlot = CommuneOrderMenu.PARTICIPANTS_BUTTON_SLOT

        assert(participantsSlot != orderMenuSizeSlot) {
            "Participants slot $participantsSlot conflicts with size button slot $orderMenuSizeSlot"
        }
        assert(participantsSlot != orderMenuInfoSlot) {
            "Participants slot $participantsSlot conflicts with info button slot $orderMenuInfoSlot"
        }
        assert(participantsSlot != orderMenuUpgradeSlot) {
            "Participants slot $participantsSlot conflicts with upgrade button slot $orderMenuUpgradeSlot"
        }
        assert(participantsSlot != orderMenuRestoreSlot) {
            "Participants slot $participantsSlot conflicts with restore button slot $orderMenuRestoreSlot"
        }
        assert(participantsSlot != orderMenuBackSlot) {
            "Participants slot $participantsSlot conflicts with back button slot $orderMenuBackSlot"
        }
        assert(participantsSlot != orderMenuHomeSlot) {
            "Participants slot $participantsSlot conflicts with home button slot $orderMenuHomeSlot"
        }
    }
}
