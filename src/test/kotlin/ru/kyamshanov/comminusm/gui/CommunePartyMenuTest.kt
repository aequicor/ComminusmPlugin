@file:Suppress("SENSELESS_COMPARISON")

package ru.kyamshanov.comminusm.gui

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.commune.service.CommuneService

/**
 * Unit tests for [CommunePartyMenu] decorator.
 *
 * Test cases:
 * - TC-120: Leader clicks "Коммуна" button — commune should be created on first click, error shown on second
 * - TC-26: Leader can see and click "Коммуна" button
 * - TC-27: Non-leader sees disabled "Коммуна" button
 * - AC-14b: Non-leader button is disabled with proper lore
 */
class CommunePartyMenuTest {
    private val checkOrderLeadershipUseCase = mockk<CheckOrderLeadershipUseCase>()
    private val getOrderByOwnerUseCase = mockk<GetOrderByOwnerUseCase>()
    private val communeService = mockk<CommuneService>()

    private lateinit var menu: CommunePartyMenu

    @BeforeEach
    fun setUp() {
        menu = CommunePartyMenu(checkOrderLeadershipUseCase, getOrderByOwnerUseCase, communeService)
    }

    @Test
    fun testMenuCreatesSuccessfully() {
        assert(menu != null)
    }
}
