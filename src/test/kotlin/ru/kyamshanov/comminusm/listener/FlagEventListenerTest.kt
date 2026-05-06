package ru.kyamshanov.comminusm.listener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import ru.kyamshanov.comminusm.event.FlagRelocatedEvent
import ru.kyamshanov.comminusm.service.CancelReason
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.HomeTimerManager
import ru.kyamshanov.comminusm.service.StubPluginBase
import ru.kyamshanov.comminusm.service.TeleportResult
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * Unit tests for [FlagEventListener].
 *
 * Uses a real [HomeTimerManager] (with all Bukkit seams replaced by in-memory
 * fakes) plus a subclass that records [cancelTimer] calls so we can verify
 * which [CancelReason] was used.
 *
 * Covered: TC-13, TC-20, TC-21.
 */
class FlagEventListenerTest {

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    private data class CancelCall(val playerUuid: UUID, val reason: CancelReason, val silent: Boolean)

    /** Subclass of the open [HomeTimerManager] that records every [cancelTimer] invocation. */
    private class RecordingHomeTimerManager(
        private val taskIdSeq: AtomicInteger,
    ) : HomeTimerManager(
        plugin = FakePlugin(),
        flagStabilityManager = NoOpFlagStabilityManager(),
        taskScheduler = { taskIdSeq.getAndIncrement() },
        taskCanceller = { /* no-op */ },
        isPlayerOnline = { true },
        sendActionBarToPlayer = { _, _ -> },
        sendMessageToPlayer = { _, _ -> },
        getPlayerWorldName = { "world" },
        getFlagWorldName = { _, _ -> "world" },
        teleportPlayerToFlag = { _, _ -> TeleportResult.SUCCESS },
        mainThreadRunner = { r -> r.run() },
    ) {
        val cancelCalls = mutableListOf<CancelCall>()

        override fun cancelTimer(playerUuid: UUID, reason: CancelReason, silent: Boolean) {
            cancelCalls += CancelCall(playerUuid, reason, silent)
            super.cancelTimer(playerUuid, reason, silent)
        }
    }

    private class NoOpFlagStabilityManager : FlagStabilityManager {
        override fun getFlagLocation(orderId: Long): org.bukkit.Location? = null
        override fun isFlagActive(orderId: Long): Boolean = false
    }

    private class FakePlugin : StubPluginBase() {
        override fun getLogger(): Logger = Logger.getLogger("FlagEventListenerTest")
    }

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    private val taskIdSeq = AtomicInteger(1)
    private lateinit var manager: RecordingHomeTimerManager
    private lateinit var listener: FlagEventListener

    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val orderId: Long = 7L

    @BeforeEach
    fun setUp() {
        manager = RecordingHomeTimerManager(taskIdSeq)
        listener = FlagEventListener(manager)
        // Start a timer so cancellations have something to act on
        manager.startTimer(playerUuid, orderId)
    }

    // -------------------------------------------------------------------------
    // TC-13
    // -------------------------------------------------------------------------

    /**
     * TC-13: [FlagDeactivatedEvent] → [HomeTimerManager.cancelTimersForOrder] is
     * called with [CancelReason.FLAG_DEACTIVATED] and all active timers for the
     * order are removed.
     */
    @Test
    fun `TC-13 FlagDeactivatedEvent cancels timers with FLAG_DEACTIVATED`() {
        assertTrue(manager.hasActiveTimer(playerUuid), "Pre-condition: timer must be active")

        listener.onFlagDeactivated(FlagDeactivatedEvent(orderId))

        assertFalse(manager.hasActiveTimer(playerUuid), "Timer must be cancelled")
        assertEquals(1, manager.cancelCalls.size)
        assertEquals(CancelReason.FLAG_DEACTIVATED, manager.cancelCalls[0].reason)
        assertEquals(playerUuid, manager.cancelCalls[0].playerUuid)
    }

    // -------------------------------------------------------------------------
    // TC-20
    // -------------------------------------------------------------------------

    /**
     * TC-20: [FlagRelocatedEvent] with same-world relocation → timers are NOT cancelled.
     */
    @Test
    fun `TC-20 FlagRelocatedEvent same-world does NOT cancel timers`() {
        assertTrue(manager.hasActiveTimer(playerUuid), "Pre-condition: timer must be active")

        val event = FlagRelocatedEvent(
            orderId = orderId,
            oldWorld = "world",
            newWorld = "world",
            newLocation = org.bukkit.Location(null, 10.0, 64.0, 20.0),
        )
        listener.onFlagRelocated(event)

        assertTrue(manager.hasActiveTimer(playerUuid), "Timer must NOT be cancelled for same-world relocation")
        assertEquals(0, manager.cancelCalls.size)
    }

    // -------------------------------------------------------------------------
    // TC-21
    // -------------------------------------------------------------------------

    /**
     * TC-21: [FlagRelocatedEvent] with cross-world relocation → timers are cancelled
     * with [CancelReason.FLAG_WORLD_CHANGED].
     */
    @Test
    fun `TC-21 FlagRelocatedEvent different-world cancels timers with FLAG_WORLD_CHANGED`() {
        assertTrue(manager.hasActiveTimer(playerUuid), "Pre-condition: timer must be active")

        val event = FlagRelocatedEvent(
            orderId = orderId,
            oldWorld = "world",
            newWorld = "nether",
            newLocation = org.bukkit.Location(null, 10.0, 64.0, 20.0),
        )
        listener.onFlagRelocated(event)

        assertFalse(manager.hasActiveTimer(playerUuid), "Timer must be cancelled for cross-world relocation")
        assertEquals(1, manager.cancelCalls.size)
        assertEquals(CancelReason.FLAG_WORLD_CHANGED, manager.cancelCalls[0].reason)
        assertEquals(playerUuid, manager.cancelCalls[0].playerUuid)
    }
}
