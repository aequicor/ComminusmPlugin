package ru.kyamshanov.comminusm.service

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for [HomeTimerManager].
 *
 * All Bukkit-dependent seams (scheduler, player lookup, action bar, teleport,
 * world-name resolution) are replaced with in-memory fakes.
 * No running Bukkit server is required.
 *
 * Covered test cases: TC-03, TC-12, TC-08, TC-24, TC-35, TC-39.
 */
class HomeTimerManagerTest {

    // -------------------------------------------------------------------------
    // In-memory fake infrastructure
    // -------------------------------------------------------------------------

    private var nextTaskId = 1
    private val scheduledActions = mutableMapOf<Int, () -> Unit>()
    private val cancelledTasks = mutableSetOf<Int>()
    private val mainThreadQueue = mutableListOf<Runnable>()

    /** ActionBar messages (raw MiniMessage strings) sent per player UUID. */
    private val actionBarMessages = mutableMapOf<UUID, MutableList<String>>()

    /** Chat messages (serialised via PlainTextComponentSerializer) sent per player UUID. */
    private val sentMessages = mutableMapOf<UUID, MutableList<String>>()

    /** Controls which players are "online". Maps UUID → world name. */
    private val onlinePlayers = mutableMapOf<UUID, String>()

    /** Teleport calls recorded as playerUuid → list of orderId. */
    private val teleportCalls = mutableMapOf<UUID, MutableList<Long>>()

    /** Whether the next teleport attempt succeeds. */
    private var teleportSucceeds = true

    private lateinit var fakeFsm: FakeFlagStabilityManager
    private lateinit var manager: HomeTimerManager

    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val orderId: Long = 42L

    // -------------------------------------------------------------------------
    // Fake FlagStabilityManager
    // -------------------------------------------------------------------------

    private inner class FakeFlagStabilityManager : FlagStabilityManager {
        private val worldNames = mutableMapOf<Long, String?>()
        private val activeFlags = mutableMapOf<Long, Boolean>()

        /** How many times getFlagLocation was called per orderId (CC-06 verification). */
        val getFlagLocationCallCount = mutableMapOf<Long, Int>()

        /**
         * Convenience setter: sets both flag location (world name) and active state.
         * [worldName] = null simulates an absent/unresolvable flag location.
         */
        fun setFlag(id: Long, worldName: String?, active: Boolean) {
            worldNames[id] = worldName
            activeFlags[id] = active
        }

        override fun getFlagLocation(orderId: Long): org.bukkit.Location? {
            getFlagLocationCallCount[orderId] = (getFlagLocationCallCount[orderId] ?: 0) + 1
            // Return null when world name is absent (simulates missing PDC entry)
            return worldNames[orderId]?.let { org.bukkit.Location(null, 0.0, 64.0, 0.0) }
        }

        override fun isFlagActive(orderId: Long): Boolean = activeFlags[orderId] ?: false

        /** Returns the stored world name for the given orderId (used by getFlagWorldName seam). */
        fun getWorldName(orderId: Long): String? = worldNames[orderId]
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeEach
    fun setUp() {
        nextTaskId = 1
        scheduledActions.clear()
        cancelledTasks.clear()
        mainThreadQueue.clear()
        actionBarMessages.clear()
        sentMessages.clear()
        onlinePlayers.clear()
        teleportCalls.clear()
        teleportSucceeds = true

        fakeFsm = FakeFlagStabilityManager()

        manager = HomeTimerManager(
            plugin = FakePlugin(),
            flagStabilityManager = fakeFsm,
            taskScheduler = { action ->
                val id = nextTaskId++
                scheduledActions[id] = action
                id
            },
            taskCanceller = { id -> cancelledTasks.add(id) },
            isPlayerOnline = { uuid -> onlinePlayers.containsKey(uuid) },
            sendActionBarToPlayer = { uuid, message ->
                actionBarMessages.getOrPut(uuid) { mutableListOf() }.add(message)
            },
            sendMessageToPlayer = { uuid, component ->
                val plain = PlainTextComponentSerializer.plainText().serialize(component)
                sentMessages.getOrPut(uuid) { mutableListOf() }.add(plain)
            },
            getPlayerWorldName = { uuid -> onlinePlayers[uuid] },
            getFlagWorldName = { oid, _ -> fakeFsm.getWorldName(oid) },
            teleportPlayerToFlag = { uuid, oid ->
                teleportCalls.getOrPut(uuid) { mutableListOf() }.add(oid)
                if (teleportSucceeds) TeleportResult.SUCCESS else TeleportResult.FAILED
            },
            mainThreadRunner = { runnable -> mainThreadQueue.add(runnable) },
        )
    }

    // -------------------------------------------------------------------------
    // TC-03: startTimer creates task, returns true, state in map
    // -------------------------------------------------------------------------

    @Test
    fun `TC-03 startTimer creates BukkitTask, returns true, and stores state`() {
        onlinePlayers[playerUuid] = "world"

        val result = manager.startTimer(playerUuid, orderId)

        assertTrue(result, "startTimer must return true on first call")
        assertTrue(manager.hasActiveTimer(playerUuid), "state must be stored in the timers map")
        assertEquals(1, scheduledActions.size, "exactly one BukkitTask must have been submitted")
    }

    @Test
    fun `TC-03 startTimer sends initial ActionBar message containing 30 seconds`() {
        onlinePlayers[playerUuid] = "world"
        manager.startTimer(playerUuid, orderId)

        val messages = actionBarMessages[playerUuid]
        assertNotNull(messages, "player must receive an ActionBar message on timer start")
        assertTrue(
            messages!!.any { it.contains("30") },
            "initial ActionBar must contain '30' seconds",
        )
    }

    // -------------------------------------------------------------------------
    // TC-12: startTimer twice returns false on second call
    // -------------------------------------------------------------------------

    @Test
    fun `TC-12 startTimer twice returns false on second call`() {
        onlinePlayers[playerUuid] = "world"

        val first = manager.startTimer(playerUuid, orderId)
        val second = manager.startTimer(playerUuid, orderId)

        assertTrue(first, "first startTimer must return true")
        assertFalse(second, "second startTimer must return false (AC-12: no double timer)")
    }

    @Test
    fun `TC-12 second startTimer does not create additional BukkitTask`() {
        onlinePlayers[playerUuid] = "world"

        manager.startTimer(playerUuid, orderId)
        val taskCountAfterFirst = scheduledActions.size

        manager.startTimer(playerUuid, orderId)

        assertEquals(
            taskCountAfterFirst,
            scheduledActions.size,
            "no additional task must be created for a duplicate startTimer",
        )
    }

    // -------------------------------------------------------------------------
    // TC-08: tick at remainingSeconds=1 calls executeHomeTP path
    // -------------------------------------------------------------------------

    @Test
    fun `TC-08 tick at remainingSeconds=1 triggers teleport and removes state from map`() {
        onlinePlayers[playerUuid] = "world"
        fakeFsm.setFlag(orderId, worldName = "world", active = true)

        manager.startTimer(playerUuid, orderId)

        val state = manager.timers[playerUuid]
        assertNotNull(state, "timer state must exist after startTimer")
        state!!.remainingSeconds = 1

        val taskId = state.taskId
        scheduledActions[taskId]!!.invoke()

        assertFalse(manager.hasActiveTimer(playerUuid), "state must be removed after teleport execution")
        assertTrue(cancelledTasks.contains(taskId), "BukkitTask must be cancelled after teleport execution")
        val calls = teleportCalls[playerUuid]
        assertNotNull(calls, "teleport must be attempted")
        assertTrue(calls!!.contains(orderId), "teleport must be attempted for the correct orderId")
    }

    @Test
    fun `TC-08 tick decrements remainingSeconds and sends ActionBar update`() {
        onlinePlayers[playerUuid] = "world"
        manager.startTimer(playerUuid, orderId)

        val state = manager.timers[playerUuid]!!
        state.remainingSeconds = 10

        scheduledActions[state.taskId]!!.invoke()

        assertEquals(9, state.remainingSeconds, "remainingSeconds must be decremented by 1 per tick")
        val messages = actionBarMessages[playerUuid]
        assertNotNull(messages)
        assertTrue(
            messages!!.any { it.contains("9") },
            "ActionBar must be updated with the new remaining seconds",
        )
    }

    // -------------------------------------------------------------------------
    // TC-24: executeHomeTP when getFlagLocation=null → no teleport, error message
    // -------------------------------------------------------------------------

    @Test
    fun `TC-24 executeHomeTP when flagLocation is null sends error message and skips teleport`() {
        onlinePlayers[playerUuid] = "world"

        // null worldName → getFlagLocation returns null
        fakeFsm.setFlag(orderId, worldName = null, active = true)

        manager.startTimer(playerUuid, orderId)
        val state = manager.timers[playerUuid]!!
        state.remainingSeconds = 1

        scheduledActions[state.taskId]!!.invoke()

        assertFalse(manager.hasActiveTimer(playerUuid), "state must be removed even when teleport is skipped")
        assertTrue(teleportCalls[playerUuid].isNullOrEmpty(), "teleport must NOT be called when flag location is null")
        val messages = sentMessages[playerUuid]
        assertNotNull(messages, "player must receive a chat message when flag location is null")
        assertTrue(
            messages!!.any { it.contains("недоступен") || it.contains("отменена") },
            "error message must mention недоступен or отменена",
        )
    }

    // -------------------------------------------------------------------------
    // TC-35: executeHomeTP when isFlagActive=false → no teleport (CC-02)
    // -------------------------------------------------------------------------

    @Test
    fun `TC-35 executeHomeTP when isFlagActive is false cancels teleport (CC-02)`() {
        onlinePlayers[playerUuid] = "world"

        fakeFsm.setFlag(orderId, worldName = "world", active = false)   // flag inactive

        manager.startTimer(playerUuid, orderId)
        val state = manager.timers[playerUuid]!!
        state.remainingSeconds = 1

        scheduledActions[state.taskId]!!.invoke()

        assertFalse(manager.hasActiveTimer(playerUuid), "state must be removed after cancelled teleport")
        assertTrue(teleportCalls[playerUuid].isNullOrEmpty(), "teleport must NOT be called when flag is inactive")
        val messages = sentMessages[playerUuid]
        assertNotNull(messages, "player must receive a chat message when flag is inactive")
        assertTrue(
            messages!!.any { it.contains("недоступен") || it.contains("отменена") || it.contains("деактивирован") },
            "error message must mention недоступен, отменена, or деактивирован",
        )
    }

    // -------------------------------------------------------------------------
    // TC-39: executeHomeTP reads fresh flag location, not cached (CC-06)
    // -------------------------------------------------------------------------

    @Test
    fun `TC-39 executeHomeTP calls getFlagLocation at execution time, not from cached state (CC-06)`() {
        onlinePlayers[playerUuid] = "world"

        fakeFsm.setFlag(orderId, worldName = "world", active = true)

        manager.startTimer(playerUuid, orderId)
        val state = manager.timers[playerUuid]!!

        // Relocate flag within same world after timer started
        fakeFsm.setFlag(orderId, worldName = "world", active = true)

        val callsBefore = fakeFsm.getFlagLocationCallCount[orderId] ?: 0

        state.remainingSeconds = 1
        scheduledActions[state.taskId]!!.invoke()

        val callsAfter = fakeFsm.getFlagLocationCallCount[orderId] ?: 0
        assertTrue(
            callsAfter > callsBefore,
            "getFlagLocation must be called during executeHomeTP (CC-06 — fresh read, not cached from startTimer)",
        )
    }

    // -------------------------------------------------------------------------
    // Additional: cancelTimer idempotency, cancelTimersForOrder, onDisable
    // -------------------------------------------------------------------------

    @Test
    fun `cancelTimer is idempotent when called twice`() {
        onlinePlayers[playerUuid] = "world"
        manager.startTimer(playerUuid, orderId)

        assertDoesNotThrow {
            manager.cancelTimer(playerUuid, CancelReason.MOVEMENT)
            manager.cancelTimer(playerUuid, CancelReason.MOVEMENT)
        }
        assertFalse(manager.hasActiveTimer(playerUuid))
    }

    @Test
    fun `cancelTimersForOrder cancels all timers for target order and leaves others`() {
        val uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val uuid3 = UUID.fromString("00000000-0000-0000-0000-000000000003")
        onlinePlayers[playerUuid] = "world"
        onlinePlayers[uuid2] = "world"
        onlinePlayers[uuid3] = "world"

        manager.startTimer(playerUuid, orderId)
        manager.startTimer(uuid2, orderId)
        manager.startTimer(uuid3, orderId + 1)

        manager.cancelTimersForOrder(orderId, CancelReason.FLAG_DEACTIVATED)

        assertFalse(manager.hasActiveTimer(playerUuid), "timer for player1 (orderId) must be cancelled")
        assertFalse(manager.hasActiveTimer(uuid2), "timer for player2 (orderId) must be cancelled")
        assertTrue(manager.hasActiveTimer(uuid3), "timer for player3 (different order) must NOT be cancelled")
    }

    @Test
    fun `onDisable cancels all BukkitTasks and clears the map`() {
        val uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
        onlinePlayers[playerUuid] = "world"
        onlinePlayers[uuid2] = "world"

        manager.startTimer(playerUuid, orderId)
        manager.startTimer(uuid2, orderId + 1)

        manager.onDisable()

        assertFalse(manager.hasActiveTimer(playerUuid), "all timers must be cleared by onDisable")
        assertFalse(manager.hasActiveTimer(uuid2), "all timers must be cleared by onDisable")
        assertEquals(2, cancelledTasks.size, "onDisable must cancel both BukkitTasks")
    }

    // -------------------------------------------------------------------------
    // Fake Plugin
    // -------------------------------------------------------------------------

    private class FakePlugin : StubPluginBase() {
        override fun getLogger(): Logger = Logger.getLogger("HomeTimerManagerTest")
    }
}
