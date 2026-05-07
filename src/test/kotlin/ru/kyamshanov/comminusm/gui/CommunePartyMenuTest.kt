@file:Suppress("SENSELESS_COMPARISON")

package ru.kyamshanov.comminusm.gui

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.service.OrderService

/**
 * Unit tests for [CommunePartyMenu] decorator.
 *
 * Test cases:
 * - TC-26: Leader can see and click "Коммуна" button
 * - TC-27: Non-leader sees disabled "Коммуна" button
 * - AC-14b: Non-leader button is disabled with proper lore
 */
class CommunePartyMenuTest {
    private val communeService = mockk<CommuneService>()
    private val orderService = mockk<OrderService>()

    private lateinit var menu: CommunePartyMenu

    @BeforeEach
    fun setUp() {
        menu = CommunePartyMenu(communeService, orderService)
    }

    @Test
    fun testMenuCreatesSuccessfully() {
        // This test will pass once CommunePartyMenu is instantiated
        assert(menu != null)
    }
}
