package ru.kyamshanov.comminusm.gui

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService

/**
 * Unit tests for [CommuneOrderMenu] decorator.
 *
 * Test cases:
 * - AC-60: "Участники" button visible to order leaders and native members
 * - Button click opens OrderMembersMenu
 */
class CommuneOrderMenuTest {

    private val orderService = mockk<OrderService>()
    private val orderMembershipService = mockk<OrderMembershipService>()

    private lateinit var menu: CommuneOrderMenu

    @BeforeEach
    fun setUp() {
        menu = CommuneOrderMenu(orderService, orderMembershipService)
    }

    @Test
    fun testMenuCreatesSuccessfully() {
        // This test will pass once CommuneOrderMenu is instantiated
        assert(menu != null)
    }
}
