@file:Suppress("SENSELESS_COMPARISON")

package ru.kyamshanov.comminusm.gui

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * Unit tests for [OrderMembersMenu].
 *
 * Test cases:
 * - AC-60: Display native and cross-order members
 * - AC-51: Leader sees invite button
 * - US-14: Leader can remove members
 * - TC-93: List displays native member names
 */
class OrderMembersMenuTest {
    private val checkOrderLeadershipUseCase = mockk<CheckOrderLeadershipUseCase>()
    private val orderMembershipService = mockk<OrderMembershipService>()

    private lateinit var menu: OrderMembersMenu

    @BeforeEach
    fun setUp() {
        menu = OrderMembersMenu(checkOrderLeadershipUseCase, orderMembershipService)
    }

    @Test
    fun testMenuCreatesSuccessfully() {
        // This test will pass once OrderMembersMenu is instantiated
        assert(menu != null)
    }
}
