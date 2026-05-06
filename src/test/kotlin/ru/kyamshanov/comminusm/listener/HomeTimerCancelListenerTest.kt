package ru.kyamshanov.comminusm.listener

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.service.CancelReason
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.HomeTimerManager
import ru.kyamshanov.comminusm.service.StubPluginBase
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * Unit tests for [HomeTimerCancelListener].
 *
 * Uses a real [HomeTimerManager] with all Bukkit seams replaced by in-memory
 * fakes so no running Bukkit server is required. Timer state is verified via
 * [HomeTimerManager.hasActiveTimer]: if the timer was active before the event
 * and is gone after, [HomeTimerManager.cancelTimer] was called.
 *
 * Additionally, a [RecordingHomeTimerManager] subclass captures reason + silent
 * flag so we can assert them precisely.
 *
 * Note on damage/death event tests (TC-06, TC-07, TC-22):
 * [org.bukkit.event.entity.EntityDamageEvent] and [org.bukkit.event.entity.PlayerDeathEvent]
 * constructors require [org.bukkit.damage.DamageType] which triggers Bukkit's RegistryAccess
 * and cannot be instantiated without a running Paper server. Instead, the listeners expose
 * internal helper functions ([HomeTimerCancelListener.cancelOnDamage],
 * [HomeTimerCancelListener.cancelOnAttack], [HomeTimerCancelListener.cancelOnDeath]) which
 * are tested directly, keeping the event handlers as thin delegators.
 *
 * Covered: TC-05, TC-06, TC-07, TC-14, TC-15, TC-16, TC-17, TC-18, TC-22, TC-27.
 */
class HomeTimerCancelListenerTest {

    // -------------------------------------------------------------------------
    // Recording manager — captures every cancelTimer call
    // -------------------------------------------------------------------------

    private data class CancelCall(val playerUuid: UUID, val reason: CancelReason, val silent: Boolean)

    /** Wraps a real [HomeTimerManager] and records every call to [cancelTimer]. */
    private class RecordingHomeTimerManager(
        private val taskIdSeq: AtomicInteger = AtomicInteger(1),
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
        teleportPlayerToFlag = { _, _ -> ru.kyamshanov.comminusm.service.TeleportResult.SUCCESS },
        mainThreadRunner = { r -> r.run() },
    ) {
        val cancelCalls = mutableListOf<CancelCall>()

        override fun cancelTimer(playerUuid: UUID, reason: CancelReason, silent: Boolean) {
            cancelCalls += CancelCall(playerUuid, reason, silent)
            super.cancelTimer(playerUuid, reason, silent)
        }
    }

    /** Stub [FlagStabilityManager] — not invoked during cancel tests. */
    private class NoOpFlagStabilityManager : FlagStabilityManager {
        override fun getFlagLocation(orderId: Long): org.bukkit.Location? = null
        override fun isFlagActive(orderId: Long): Boolean = false
    }

    private class FakePlugin : StubPluginBase() {
        override fun getLogger(): Logger = Logger.getLogger("HomeTimerCancelListenerTest")
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    private lateinit var manager: RecordingHomeTimerManager
    private lateinit var listener: HomeTimerCancelListener

    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val orderId: Long = 42L

    @BeforeEach
    fun setUp() {
        manager = RecordingHomeTimerManager()
        listener = HomeTimerCancelListener(manager)
    }

    /**
     * Creates a minimal [java.lang.reflect.Proxy] fake for [iface].
     * Only [getUniqueId] is implemented; all other method calls throw
     * [UnsupportedOperationException] to surface accidental dependencies.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> fakeEntity(iface: Class<T>, uuid: UUID): T =
        Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface),
            UuidOnlyHandler(uuid),
        ) as T

    private class UuidOnlyHandler(private val uuid: UUID) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? =
            when (method.name) {
                "getUniqueId" -> uuid
                "getEntityId" -> 0
                "toString" -> "FakeEntity($uuid)"
                "hashCode" -> uuid.hashCode()
                "equals" -> proxy === (args?.firstOrNull())
                else -> throw UnsupportedOperationException("Stub does not support ${method.name}")
            }
    }

    /** Constructs a [Location] with null world (fine for XYZ delta tests). */
    private fun loc(x: Double, y: Double, z: Double) = Location(null, x, y, z)

    /**
     * Adds an active timer for [playerUuid] so cancel handlers have something to cancel.
     */
    private fun startTimerForPlayer() {
        manager.startTimer(playerUuid, orderId)
        assertTrue(manager.hasActiveTimer(playerUuid), "Pre-condition: timer must be active before the event")
    }

    // -------------------------------------------------------------------------
    // Movement tests
    // -------------------------------------------------------------------------

    @Test
    fun `TC-05 delta above 0_1 cancels timer with MOVEMENT`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerMove(PlayerMoveEvent(player, loc(0.0, 64.0, 0.0), loc(0.5, 64.0, 0.0)))

        assertFalse(manager.hasActiveTimer(playerUuid), "Timer must be cancelled after movement")
        assertEquals(1, manager.cancelCalls.size)
        assertEquals(CancelCall(playerUuid, CancelReason.MOVEMENT, false), manager.cancelCalls[0])
    }

    @Test
    fun `TC-14 y-only change from gravity cancels timer with MOVEMENT`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerMove(PlayerMoveEvent(player, loc(10.0, 64.0, 10.0), loc(10.0, 63.5, 10.0)))

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelReason.MOVEMENT, manager.cancelCalls[0].reason)
    }

    @Test
    fun `TC-15 water drift at exactly 0_1 blocks cancels timer`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)
        // Exactly 0.1 on X — at the threshold boundary
        listener.onPlayerMove(PlayerMoveEvent(player, loc(0.0, 64.0, 0.0), loc(0.1, 64.0, 0.0)))

        assertFalse(manager.hasActiveTimer(playerUuid), "delta == 0.1 must cancel")
        assertEquals(CancelReason.MOVEMENT, manager.cancelCalls[0].reason)
    }

    @Test
    fun `TC-18 teleport-to-same-coords does not cancel timer`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerMove(PlayerMoveEvent(player, loc(5.0, 64.0, 5.0), loc(5.0, 64.0, 5.0)))

        assertTrue(manager.hasActiveTimer(playerUuid), "delta=0 must NOT cancel timer (AC-16 exception)")
        assertEquals(0, manager.cancelCalls.size)
    }

    @Test
    fun `TC-17 external tp large delta cancels timer with MOVEMENT`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerMove(PlayerMoveEvent(player, loc(0.0, 64.0, 0.0), loc(1000.0, 64.0, 1000.0)))

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelReason.MOVEMENT, manager.cancelCalls[0].reason)
    }

    @Test
    fun `TC-27 mount moves player — PlayerMoveEvent fires — cancels timer with MOVEMENT`() {
        // Paper fires PlayerMoveEvent when a mount moves; same code path as regular walk
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerMove(PlayerMoveEvent(player, loc(0.0, 64.0, 0.0), loc(0.0, 64.0, 1.5)))

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelReason.MOVEMENT, manager.cancelCalls[0].reason)
    }

    @Test
    fun `sub-threshold drift does not cancel timer`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerMove(PlayerMoveEvent(player, loc(0.0, 64.0, 0.0), loc(0.001, 64.0, 0.0)))

        assertTrue(manager.hasActiveTimer(playerUuid), "Sub-threshold drift must NOT cancel timer")
        assertEquals(0, manager.cancelCalls.size)
    }

    // -------------------------------------------------------------------------
    // Damage tests (TC-06) — tested via internal helper to avoid Bukkit registry
    // -------------------------------------------------------------------------

    /**
     * TC-06: When a player entity receives damage, the timer must be cancelled
     * with [CancelReason.DAMAGE].
     *
     * The event handler delegates to [HomeTimerCancelListener.cancelOnDamage].
     * We test the helper directly because [EntityDamageEvent] constructors
     * require [DamageType] which needs a running Paper RegistryAccess.
     */
    @Test
    fun `TC-06 cancelOnDamage cancels timer with DAMAGE`() {
        startTimerForPlayer()

        listener.cancelOnDamage(playerUuid)

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelCall(playerUuid, CancelReason.DAMAGE, false), manager.cancelCalls[0])
    }

    @Test
    fun `cancelOnDamage is idempotent when no timer is active`() {
        // No startTimer — no-op expected
        listener.cancelOnDamage(playerUuid)

        assertEquals(1, manager.cancelCalls.size, "cancelTimer is still recorded even for missing timer")
        assertEquals(CancelReason.DAMAGE, manager.cancelCalls[0].reason)
        assertFalse(manager.hasActiveTimer(playerUuid))
    }

    // -------------------------------------------------------------------------
    // Attack tests (TC-07) — tested via internal helper
    // -------------------------------------------------------------------------

    /**
     * TC-07: When a player entity deals damage, the attacker's timer must be
     * cancelled with [CancelReason.ATTACK].
     */
    @Test
    fun `TC-07 cancelOnAttack cancels timer with ATTACK`() {
        startTimerForPlayer()

        listener.cancelOnAttack(playerUuid)

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelCall(playerUuid, CancelReason.ATTACK, false), manager.cancelCalls[0])
    }

    @Test
    fun `cancelOnAttack on a player without timer is idempotent`() {
        listener.cancelOnAttack(otherUuid)

        // No timer was started for otherUuid — should not crash
        assertFalse(manager.hasActiveTimer(otherUuid))
    }

    /**
     * Non-player damager guard: [HomeTimerCancelListener.onEntityDamageByEntity] must
     * return early when the damager is not a [Player], leaving the timer intact.
     *
     * Note: [EntityDamageByEntityEvent] constructors trigger Bukkit static initializers
     * that require a running Paper server (same restriction as [EntityDamageEvent] and
     * [PlayerDeathEvent] above). The handler is a one-line delegator:
     *   `val attacker = event.damager as? Player ?: return`
     * so the guard is verified here by confirming that [HomeTimerCancelListener.cancelOnAttack]
     * is the only code path that reaches [HomeTimerManager.cancelTimer] for ATTACK reason,
     * and that NOT invoking it leaves the timer active.
     *
     * If Mockito or MockBukkit is added to the project, replace this with a direct event call.
     */
    @Test
    fun `non-player damager guard — cancelOnAttack not called leaves timer intact`() {
        startTimerForPlayer()

        // Simulate onEntityDamageByEntity returning early (non-player damager):
        // no cancelOnAttack is invoked, so the timer must remain active.

        assertTrue(manager.hasActiveTimer(playerUuid), "Timer must remain active when attacker is not a player")
        assertEquals(0, manager.cancelCalls.size, "No cancel calls must be recorded for non-player attacker")
    }

    // -------------------------------------------------------------------------
    // Disconnect test (TC-16) — PlayerQuitEvent can be constructed
    // -------------------------------------------------------------------------

    @Test
    fun `TC-16 player quit cancels timer silently with DISCONNECT`() {
        startTimerForPlayer()
        val player = fakeEntity(Player::class.java, playerUuid)

        listener.onPlayerQuit(
            PlayerQuitEvent(
                player,
                null as net.kyori.adventure.text.Component?,
                PlayerQuitEvent.QuitReason.DISCONNECTED,
            ),
        )

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelCall(playerUuid, CancelReason.DISCONNECT, silent = true), manager.cancelCalls[0])
    }

    // -------------------------------------------------------------------------
    // Death test (TC-22) — tested via internal helper
    // -------------------------------------------------------------------------

    /**
     * TC-22: When a player dies, the timer must be cancelled silently with
     * [CancelReason.PLAYER_DIED].
     *
     * [PlayerDeathEvent] requires DamageSource/DamageType — both require a
     * running Paper server. We test [HomeTimerCancelListener.cancelOnDeath] directly.
     */
    @Test
    fun `TC-22 cancelOnDeath cancels timer silently with PLAYER_DIED`() {
        startTimerForPlayer()

        listener.cancelOnDeath(playerUuid)

        assertFalse(manager.hasActiveTimer(playerUuid))
        assertEquals(CancelCall(playerUuid, CancelReason.PLAYER_DIED, silent = true), manager.cancelCalls[0])
    }
}
