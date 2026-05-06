package ru.kyamshanov.comminusm.gui

import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.HomeTimerManager
import ru.kyamshanov.comminusm.service.TeleportResult
import ru.kyamshanov.comminusm.service.StubPluginBase
import java.util.UUID

/**
 * Unit tests for [OrderMenu] home-button logic.
 *
 * Tests use pure-logic seams extracted from the GUI class:
 * - [OrderMenu.resolveHomeButtonState] — no Bukkit server required
 * - [OrderMenu.handleHomeClick]        — no Bukkit server required
 *
 * The World stub problem is avoided by injecting a [getFlagWorldName] lambda
 * that returns a pre-set String without touching [Location.world].
 *
 * Covered test cases: TC-01, TC-02, TC-03, TC-26, TC-32.
 */
@Suppress("TooManyFunctions")
class OrderMenuTest {

    // -------------------------------------------------------------------------
    // Fake FlagStabilityManager
    // -------------------------------------------------------------------------

    /**
     * In-memory fake. Stores (worldName, isActive) per orderId.
     * [getFlagLocation] returns a Location(null, ...) — the world name is
     * retrieved separately via the [worldNameOf] helper, used in lambda seams.
     */
    private inner class FakeFlagStabilityManager : FlagStabilityManager {
        private val entries = mutableMapOf<Long, Pair<String?, Boolean>>()

        fun setFlag(orderId: Long, worldName: String?, active: Boolean) {
            entries[orderId] = Pair(worldName, active)
        }

        /** Used by tests to feed the getFlagWorldName lambda. */
        fun worldNameOf(orderId: Long): String? = entries[orderId]?.first

        override fun getFlagLocation(orderId: Long): Location? {
            // Return null when no entry or worldName is null (flag absent).
            // Location with null world — actual world name injected via getFlagWorldName lambda.
            entries[orderId]?.first ?: return null
            return Location(null, 0.0, 64.0, 0.0)
        }

        override fun isFlagActive(orderId: Long): Boolean =
            entries[orderId]?.second ?: false
    }

    // -------------------------------------------------------------------------
    // Fake HomeTimerManager — records startTimer calls without Bukkit tasks
    // -------------------------------------------------------------------------

    private class FakeHomeTimerManager : HomeTimerManager(
        plugin = object : StubPluginBase() {},
        flagStabilityManager = object : FlagStabilityManager {
            override fun getFlagLocation(orderId: Long): Location? = null
            override fun isFlagActive(orderId: Long): Boolean = false
        },
        taskScheduler = { 0 },
        taskCanceller = {},
        isPlayerOnline = { false },
        sendActionBarToPlayer = { _, _ -> },
        sendMessageToPlayer = { _, _ -> },
        getPlayerWorldName = { null },
        getFlagWorldName = { _, _ -> null },
        teleportPlayerToFlag = { _, _ -> TeleportResult.FAILED },
        mainThreadRunner = {},
    ) {
        var cancelTimerCallCount: Int = 0

        /** cancelTimer is open — override to count cancel calls (TC-26 verification). */
        override fun cancelTimer(
            playerUuid: UUID,
            reason: ru.kyamshanov.comminusm.service.CancelReason,
            silent: Boolean,
        ) {
            cancelTimerCallCount++
            super.cancelTimer(playerUuid, reason, silent)
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val orderId: Long = 42L
    private val playerWorld = "world"
    private val differentWorld = "nether"

    private lateinit var fakeFsm: FakeFlagStabilityManager
    private lateinit var fakeHtm: FakeHomeTimerManager
    private lateinit var menu: OrderMenu

    @BeforeEach
    fun setUp() {
        fakeFsm = FakeFlagStabilityManager()
        fakeHtm = FakeHomeTimerManager()
        menu = buildMenu()
    }

    private fun buildMenu(): OrderMenu {
        val conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")
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
        val orderService = ru.kyamshanov.comminusm.service.OrderService(
            orderRepository = ru.kyamshanov.comminusm.storage.OrderRepository(conn),
            levels = emptyList(),
            workdaysService = null,
            minDistanceBetweenCenters = 100,
        )
        return OrderMenu(
            orderService = orderService,
            workdaysService = null,
            config = PluginConfig(org.bukkit.configuration.file.YamlConfiguration()),
            workFrontService = null,
            homeTimerManager = fakeHtm,
            flagStabilityManager = fakeFsm,
            plugin = null,
        )
    }

    // -------------------------------------------------------------------------
    // TC-01: active flag in same world → ACTIVE button state
    // -------------------------------------------------------------------------

    @Test
    fun `TC-01 active flag in same world yields ACTIVE button state`() {
        fakeFsm.setFlag(orderId, playerWorld, active = true)

        val state = menu.resolveHomeButtonState(
            fsm = fakeFsm,
            orderId = orderId,
            playerWorld = playerWorld,
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertEquals(
            OrderMenu.HomeButtonState.ACTIVE,
            state,
            "Owner with active flag in same world must see ACTIVE home button",
        )
    }

    // -------------------------------------------------------------------------
    // TC-02: no flag or inactive flag → HIDDEN button state
    // -------------------------------------------------------------------------

    @Test
    fun `TC-02 inactive flag yields HIDDEN button state`() {
        fakeFsm.setFlag(orderId, playerWorld, active = false)

        val state = menu.resolveHomeButtonState(
            fsm = fakeFsm,
            orderId = orderId,
            playerWorld = playerWorld,
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertEquals(
            OrderMenu.HomeButtonState.HIDDEN,
            state,
            "Inactive flag must yield HIDDEN home button",
        )
    }

    @Test
    fun `TC-02 absent flag location yields HIDDEN button state`() {
        // No entry in fakeFsm — getFlagLocation returns null (non-owner case)

        val state = menu.resolveHomeButtonState(
            fsm = fakeFsm,
            orderId = orderId,
            playerWorld = playerWorld,
            getFlagWorldName = { null },
        )

        assertEquals(
            OrderMenu.HomeButtonState.HIDDEN,
            state,
            "Absent flag location must yield HIDDEN home button (non-owner / deactivated flag)",
        )
    }

    // -------------------------------------------------------------------------
    // TC-32: active flag in different world → DISABLED_DIFFERENT_WORLD
    // -------------------------------------------------------------------------

    @Test
    fun `TC-32 active flag in different world yields DISABLED_DIFFERENT_WORLD button state`() {
        fakeFsm.setFlag(orderId, differentWorld, active = true)

        val state = menu.resolveHomeButtonState(
            fsm = fakeFsm,
            orderId = orderId,
            playerWorld = playerWorld,
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertEquals(
            OrderMenu.HomeButtonState.DISABLED_DIFFERENT_WORLD,
            state,
            "Active flag in different world must yield DISABLED_DIFFERENT_WORLD home button",
        )
    }

    // -------------------------------------------------------------------------
    // TC-03: handleHomeClick with valid state → startTimer + closeInventory
    // -------------------------------------------------------------------------

    @Test
    fun `TC-03 handleHomeClick with active same-world flag starts timer and closes inventory`() {
        fakeFsm.setFlag(orderId, playerWorld, active = true)
        var inventoryClosed = false

        // Use a recording HomeTimerManager (taskScheduler records the fact that startTimer was called)
        val scheduledIds = mutableListOf<Int>()
        val recordingHtm = HomeTimerManager(
            plugin = object : StubPluginBase() {},
            flagStabilityManager = fakeFsm,
            taskScheduler = { action ->
                val id = scheduledIds.size + 1
                scheduledIds.add(id)
                id
            },
            taskCanceller = {},
            isPlayerOnline = { false },
            sendActionBarToPlayer = { _, _ -> },
            sendMessageToPlayer = { _, _ -> },
            getPlayerWorldName = { null },
            getFlagWorldName = { _, _ -> null },
            teleportPlayerToFlag = { _, _ -> TeleportResult.FAILED },
            mainThreadRunner = {},
        )

        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = recordingHtm,
            sendActionBar = {},
            closeInventory = { inventoryClosed = true },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertTrue(
            recordingHtm.hasActiveTimer(playerUuid),
            "startTimer must have been called — timer must be active for the player",
        )
        assertTrue(inventoryClosed, "inventory must be closed after timer start (AC-23)")
    }

    @Test
    fun `TC-03 handleHomeClick with null flag location sends correct error and does not start timer`() {
        // No entry in fakeFsm — getFlagLocation returns null
        var inventoryClosed = false
        val actionBarMessages = mutableListOf<String>()

        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = fakeHtm,
            sendActionBar = { actionBarMessages.add(it) },
            closeInventory = { inventoryClosed = true },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertFalse(
            fakeHtm.hasActiveTimer(playerUuid),
            "startTimer must NOT be called when flag location is null",
        )
        assertFalse(inventoryClosed, "inventory must NOT be closed when flag location is null")
        assertEquals(
            listOf("<red>Флаг недоступен.</red>"),
            actionBarMessages,
            "correct action bar message must be sent when flag location is null",
        )
    }

    @Test
    fun `TC-03 handleHomeClick with inactive flag sends correct error and does not start timer`() {
        fakeFsm.setFlag(orderId, playerWorld, active = false)
        var inventoryClosed = false
        val actionBarMessages = mutableListOf<String>()

        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = fakeHtm,
            sendActionBar = { actionBarMessages.add(it) },
            closeInventory = { inventoryClosed = true },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertFalse(
            fakeHtm.hasActiveTimer(playerUuid),
            "startTimer must NOT be called when flag is inactive",
        )
        assertFalse(inventoryClosed, "inventory must NOT be closed when timer is not started")
        assertEquals(
            listOf("<red>Флаг ордера недоступен.</red>"),
            actionBarMessages,
            "correct action bar message must be sent when flag is inactive",
        )
    }

    @Test
    fun `TC-32 handleHomeClick with flag in different world sends correct error and does not start timer`() {
        fakeFsm.setFlag(orderId, differentWorld, active = true)
        var inventoryClosed = false
        val actionBarMessages = mutableListOf<String>()

        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = fakeHtm,
            sendActionBar = { actionBarMessages.add(it) },
            closeInventory = { inventoryClosed = true },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertFalse(
            fakeHtm.hasActiveTimer(playerUuid),
            "startTimer must NOT be called when flag is in different world",
        )
        assertFalse(inventoryClosed, "inventory must NOT be closed when timer is not started")
        assertEquals(
            listOf("<red>Возврат домой недоступен — флаг находится в другом мире.</red>"),
            actionBarMessages,
            "correct action bar message must be sent for cross-world click (AC-28)",
        )
    }

    // -------------------------------------------------------------------------
    // AC-12: hasActiveTimer guard — silent return when timer already active
    // -------------------------------------------------------------------------

    @Test
    fun `AC-12 handleHomeClick silently returns without closing inventory when timer is already active`() {
        fakeFsm.setFlag(orderId, playerWorld, active = true)
        var inventoryClosed = false
        val actionBarMessages = mutableListOf<String>()

        val alreadyActiveHtm = HomeTimerManager(
            plugin = object : StubPluginBase() {},
            flagStabilityManager = fakeFsm,
            taskScheduler = { _ -> 1 },
            taskCanceller = {},
            isPlayerOnline = { false },
            sendActionBarToPlayer = { _, _ -> },
            sendMessageToPlayer = { _, _ -> },
            getPlayerWorldName = { null },
            getFlagWorldName = { _, _ -> null },
            teleportPlayerToFlag = { _, _ -> TeleportResult.FAILED },
            mainThreadRunner = {},
        )
        // Pre-seed an active timer for playerUuid
        alreadyActiveHtm.startTimer(playerUuid, orderId)

        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = alreadyActiveHtm,
            sendActionBar = { actionBarMessages.add(it) },
            closeInventory = { inventoryClosed = true },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        assertFalse(inventoryClosed, "inventory must NOT be closed when timer is already active (AC-12)")
        assertTrue(actionBarMessages.isEmpty(), "no action bar message must be sent on silent guard (AC-12)")
    }

    // -------------------------------------------------------------------------
    // Fix 2: non-owner rejected before any flag check
    // -------------------------------------------------------------------------

    @Test
    fun `handleHomeClick with non-owner rejects request and does not start timer`() {
        fakeFsm.setFlag(orderId, playerWorld, active = true)
        var inventoryClosed = false
        val messages = mutableListOf<String>()
        val actionBarMessages = mutableListOf<String>()

        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = fakeHtm,
            sendActionBar = { actionBarMessages.add(it) },
            sendMessage = { messages.add(it) },
            closeInventory = { inventoryClosed = true },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
            checkOwner = { false }, // non-owner
        )

        assertFalse(fakeHtm.hasActiveTimer(playerUuid), "startTimer must NOT be called for non-owner")
        assertFalse(inventoryClosed, "inventory must NOT be closed for non-owner")
        assertTrue(messages.isNotEmpty(), "error message must be sent to non-owner")
        assertTrue(actionBarMessages.isEmpty(), "action bar must NOT be sent when rejected at ownership check")
    }

    // -------------------------------------------------------------------------
    // TC-26: closing inventory does NOT cancel the active timer
    // -------------------------------------------------------------------------

    @Test
    fun `TC-26 timer remains active after handleHomeClick closes inventory`() {
        fakeFsm.setFlag(orderId, playerWorld, active = true)

        val recordingHtm = HomeTimerManager(
            plugin = object : StubPluginBase() {},
            flagStabilityManager = fakeFsm,
            taskScheduler = { _ -> 1 },
            taskCanceller = {},
            isPlayerOnline = { false },
            sendActionBarToPlayer = { _, _ -> },
            sendMessageToPlayer = { _, _ -> },
            getPlayerWorldName = { null },
            getFlagWorldName = { _, _ -> null },
            teleportPlayerToFlag = { _, _ -> TeleportResult.FAILED },
            mainThreadRunner = {},
        )

        // Start the timer via handleHomeClick
        menu.handleHomeClick(
            playerUuid = playerUuid,
            playerWorldName = playerWorld,
            orderId = orderId,
            fsm = fakeFsm,
            htm = recordingHtm,
            sendActionBar = {},
            closeInventory = { /* simulate closing inventory */ },
            getFlagWorldName = { fakeFsm.worldNameOf(orderId) },
        )

        // Timer must still be active — closeInventory() did NOT cancel it
        // TC-26: closing inventory must not cancel the active timer
        assertTrue(
            recordingHtm.hasActiveTimer(playerUuid),
            "Timer must remain active after menu is closed (TC-26, AC-23)",
        )
    }
}
