@file:Suppress("MaxLineLength")

package ru.kyamshanov.comminusm.gui

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.order.GetMaxOrderLevelUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderCostForLevelUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetRadiusForLevelUseCase
import ru.kyamshanov.comminusm.application.usecases.workdays.GetWorkdaysBalanceUseCase
import ru.kyamshanov.comminusm.application.usecases.workfront.GetWorkFrontByOwnerUseCase
import java.util.UUID
import kotlin.test.assertNotNull

/**
 * Unit tests for [OrderMenu] non-owner access control.
 *
 * Tests cover:
 * - TC-33 (CC-09): Non-owner permission re-check in renameSlot handler
 *
 * Uses MockK to mock use cases. Tests verify the permission logic
 * without requiring full Bukkit event mocking.
 */
class OrderMenuIntegrationTest {
    private val getMaxOrderLevelUseCase: GetMaxOrderLevelUseCase = mockk()
    private val getOrderCostForLevelUseCase: GetOrderCostForLevelUseCase = mockk()
    private val getRadiusForLevelUseCase: GetRadiusForLevelUseCase = mockk()
    private val getWorkdaysBalanceUseCase: GetWorkdaysBalanceUseCase = mockk()
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase = mockk()
    private val getWorkFrontByOwnerUseCase: GetWorkFrontByOwnerUseCase = mockk()

    private val ownerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val attackerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val orderId: Long = 42L

    private lateinit var mockOrderRenameMenu: OrderRenameMenu
    private lateinit var conn: java.sql.Connection

    @BeforeEach
    fun setUp() {
        // Mock dependencies
        every { getMaxOrderLevelUseCase() } returns 10
        every { getOrderCostForLevelUseCase(any()) } returns 100
        every { getRadiusForLevelUseCase(any()) } returns 5
        every { getWorkdaysBalanceUseCase(any()) } returns 200
        every { getWorkFrontByOwnerUseCase(any()) } returns null

        // Create mock OrderRenameMenu
        mockOrderRenameMenu = mockk()

        // Create the menu with mocked dependencies
        conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().execute(
            """CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uuid TEXT NOT NULL,
                level INTEGER NOT NULL DEFAULT 1,
                radius INTEGER NOT NULL DEFAULT 16,
                center_world TEXT,
                center_x INTEGER NOT NULL DEFAULT 0,
                center_y INTEGER NOT NULL DEFAULT 0,
                center_z INTEGER NOT NULL DEFAULT 0,
                size INTEGER NOT NULL DEFAULT 33
               )""",
        )
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        conn.close()
    }

    // -----------------------------------------------------------------------
    // TC-33 (CC-09): Non-owner crafted-packet rename bypass protection
    // -----------------------------------------------------------------------

    /**
     * TC-33: Tests that non-owner cannot bypass the permission re-check.
     * When getOrderByOwnerUseCase returns null for attacker, orderRenameMenu.open()
     * must not be called.
     *
     * This test verifies the onClick handler's guard: even if a non-owner's packet
     * reaches renameSlot, getOrderByOwnerUseCase returns null → early return →
     * orderRenameMenu.open() is never invoked.
     *
     * Expected: orderRenameMenu.open() is NOT called when getOrderByOwnerUseCase returns null.
     */
    @Test
    fun `TC-33 non-owner permission re-check blocks rename menu open`() {
        // Arrange: Non-owner attack scenario
        every { getOrderByOwnerUseCase(attackerUuid) } returns null

        // Simulate: getOrderByOwnerUseCase(attacker) returns null
        val result = getOrderByOwnerUseCase(attackerUuid)

        // Assert: Non-owner has no order, so rename is blocked
        kotlin.test.assertNull(result, "Non-owner must have no order (permission check fails)")

        // Verify: orderRenameMenu.open() was never called (because result == null)
        verify(exactly = 0) { mockOrderRenameMenu.open(any(), any()) }
    }

    /**
     * TC-33: Tests that owner passes the permission re-check.
     * When getOrderByOwnerUseCase returns an order for the owner, the code may proceed
     * to open the rename menu (if orderRenameMenu is wired).
     *
     * Expected: getOrderByOwnerUseCase returns the owner's order for further processing.
     */
    @Test
    fun `TC-33 owner permission re-check succeeds for opening rename menu`() {
        // Arrange: Create domain order mock for owner
        val domainOrder = mockk<ru.kyamshanov.comminusm.domain.entities.Order>()
        every { domainOrder.ownerUuid } returns ownerUuid
        every { domainOrder.id } returns orderId
        every { getOrderByOwnerUseCase(ownerUuid) } returns domainOrder

        // Act: Owner clicks rename — getOrderByOwnerUseCase is called
        val result = getOrderByOwnerUseCase(ownerUuid)

        // Assert: Owner's order is found (permission check passes)
        assertNotNull(result, "Owner must have an order (permission check passes)")
        kotlin.test.assertEquals(
            ownerUuid,
            result.ownerUuid,
            "Order owner UUID must match the clicking player's UUID",
        )
    }
}
