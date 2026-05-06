package ru.kyamshanov.comminusm.listener

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerRespawnEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for [OrderRespawnListener].
 *
 * All Bukkit collaborators are replaced with minimal Proxy-based fakes — no
 * running Paper server is required.
 *
 * PlayerRespawnEvent(Player, Location, isBedSpawn) is constructable in tests
 * because Paper bundles the event class without requiring RegistryAccess (unlike
 * DamageType-based events). The static HandlerList registration is benign outside
 * a live server context.
 *
 * Covered: TC-09, TC-10, TC-11, TC-25, TC-31, TC-36.
 * TC-37 (chunk.load(true)) is exercised via TC-09: the RecordingWorld captures
 * getChunkAt and the RecordingChunk records whether load(true) was called.
 */
class OrderRespawnListenerTest {

    // -----------------------------------------------------------------------
    // Fake infrastructure
    // -----------------------------------------------------------------------

    /** Stub [FlagStabilityManager] backed by simple maps. */
    private class StubFlagStabilityManager : FlagStabilityManager {
        val locations = mutableMapOf<Long, Location>()
        val activeFlags = mutableSetOf<Long>()

        override fun getFlagLocation(orderId: Long): Location? = locations[orderId]
        override fun isFlagActive(orderId: Long): Boolean = orderId in activeFlags
    }

    /**
     * Stub order lookup backed by a HashMap — no SQLite / OrderService involved.
     * Passed as the [findOrderByOwner] lambda to [OrderRespawnListener].
     */
    private val ordersByUuid = mutableMapOf<UUID, Order>()

    /**
     * Minimal Proxy-based fake for [Player].
     * Only [getUniqueId] is implemented; all other calls throw.
     */
    @Suppress("UNCHECKED_CAST")
    private fun fakePlayer(uuid: UUID): Player =
        Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? =
                    when (method.name) {
                        "getUniqueId" -> uuid
                        "getEntityId" -> 0
                        "toString" -> "FakePlayer($uuid)"
                        "hashCode" -> uuid.hashCode()
                        "equals" -> proxy === args?.firstOrNull()
                        else -> throw UnsupportedOperationException("Stub does not support ${method.name}")
                    }
            },
        ) as Player

    /**
     * Recording [Chunk] fake — tracks whether [load] was called.
     * Uses Proxy to avoid implementing the entire Chunk interface.
     */
    private class RecordingChunk {
        var loadCalled = false
        var loadForce = false

        @Suppress("UNCHECKED_CAST")
        val proxy: Chunk = Proxy.newProxyInstance(
            Chunk::class.java.classLoader,
            arrayOf(Chunk::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? =
                    when (method.name) {
                        "load" -> {
                            loadCalled = true
                            loadForce = args?.firstOrNull() as? Boolean ?: false
                            true
                        }
                        "toString" -> "RecordingChunk"
                        "hashCode" -> System.identityHashCode(this)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> throw UnsupportedOperationException("Chunk stub does not support ${method.name}")
                    }
            },
        ) as Chunk
    }

    /**
     * Recording [World] fake — returns [chunkProxy] from [getChunkAt].
     * Uses Proxy to avoid implementing the entire World interface.
     */
    @Suppress("UNCHECKED_CAST")
    private fun fakeWorld(worldName: String, chunkProxy: Chunk): World =
        Proxy.newProxyInstance(
            World::class.java.classLoader,
            arrayOf(World::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? =
                    when (method.name) {
                        "getName" -> worldName
                        "getChunkAt" -> chunkProxy
                        "toString" -> "FakeWorld($worldName)"
                        "hashCode" -> worldName.hashCode()
                        "equals" -> proxy === args?.firstOrNull()
                        else -> throw UnsupportedOperationException("World stub does not support ${method.name}")
                    }
            },
        ) as World

    // -----------------------------------------------------------------------
    // Test state
    // -----------------------------------------------------------------------

    private lateinit var flagManager: StubFlagStabilityManager
    private lateinit var recordingChunk: RecordingChunk
    private lateinit var testWorld: World
    private lateinit var listener: OrderRespawnListener

    private val logger: Logger = Logger.getLogger("OrderRespawnListenerTest").also { it.useParentHandlers = false }
    private val playerUuid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val orderId: Long = 99L

    @BeforeEach
    fun setUp() {
        flagManager = StubFlagStabilityManager()
        ordersByUuid.clear()
        recordingChunk = RecordingChunk()
        testWorld = fakeWorld("world", recordingChunk.proxy)
        listener = OrderRespawnListener(
            flagStabilityManager = flagManager,
            logger = logger,
            findOrderByOwner = { uuid -> ordersByUuid[uuid] },
        )
    }

    /** Constructs a [PlayerRespawnEvent] with the given [player] and initial [respawnLoc]. */
    private fun respawnEvent(player: Player, respawnLoc: Location, isBedSpawn: Boolean = false) =
        PlayerRespawnEvent(player, respawnLoc, isBedSpawn)

    /** Creates a [Location] bound to [testWorld]. */
    private fun flagLocation(x: Double = 100.0, y: Double = 64.0, z: Double = 200.0) =
        Location(testWorld, x, y, z)

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * TC-09: Player owns an order with an active flag → respawnLocation is set to flagLoc.
     * TC-37 (chunk.load(true)): chunk.load is called before the respawn point is set.
     */
    @Test
    fun `TC-09 active flag sets respawnLocation to flagLoc`() {
        val flagLoc = flagLocation()
        val order = Order(id = orderId, ownerUuid = playerUuid)
        ordersByUuid[playerUuid] = order
        flagManager.locations[orderId] = flagLoc
        flagManager.activeFlags += orderId

        val player = fakePlayer(playerUuid)
        val defaultLoc = Location(null, 0.0, 64.0, 0.0)
        val event = respawnEvent(player, defaultLoc)

        listener.onPlayerRespawn(event)

        assertEquals(flagLoc, event.respawnLocation, "respawnLocation must be set to the flag location")
        // TC-37: chunk must have been loaded before setting the respawn point
        assert(recordingChunk.loadCalled) { "chunk.load must be called for CC-04 compliance" }
        assert(recordingChunk.loadForce) { "chunk.load must be called with generate=true" }
    }

    /**
     * TC-10: getFlagLocation returns null → respawnLocation stays unchanged (Bukkit default).
     */
    @Test
    fun `TC-10 null flag location leaves respawnLocation unchanged`() {
        val order = Order(id = orderId, ownerUuid = playerUuid)
        ordersByUuid[playerUuid] = order
        // no location registered → getFlagLocation returns null

        val player = fakePlayer(playerUuid)
        val defaultLoc = Location(null, 0.0, 64.0, 0.0)
        val event = respawnEvent(player, defaultLoc)

        listener.onPlayerRespawn(event)

        assertEquals(defaultLoc, event.respawnLocation,
            "respawnLocation must stay at default when flag location is null")
    }

    /**
     * TC-11: Player has no order → respawnLocation stays unchanged.
     */
    @Test
    fun `TC-11 player with no order leaves respawnLocation unchanged`() {
        // no order registered for playerUuid

        val player = fakePlayer(playerUuid)
        val defaultLoc = Location(null, 0.0, 64.0, 0.0)
        val event = respawnEvent(player, defaultLoc)

        listener.onPlayerRespawn(event)

        assertEquals(defaultLoc, event.respawnLocation, "respawnLocation must stay at default when player has no order")
    }

    /**
     * TC-25: isFlagActive returns false (stale PDC / deactivated flag) →
     * respawnLocation stays unchanged (fallback to bed/world spawn).
     */
    @Test
    fun `TC-25 inactive flag leaves respawnLocation unchanged`() {
        val flagLoc = flagLocation()
        val order = Order(id = orderId, ownerUuid = playerUuid)
        ordersByUuid[playerUuid] = order
        flagManager.locations[orderId] = flagLoc
        // flag is NOT in activeFlags → isFlagActive returns false

        val player = fakePlayer(playerUuid)
        val defaultLoc = Location(null, 0.0, 64.0, 0.0)
        val event = respawnEvent(player, defaultLoc)

        listener.onPlayerRespawn(event)

        assertEquals(defaultLoc, event.respawnLocation, "respawnLocation must stay at default when flag is inactive")
    }

    /**
     * TC-31: Player has a bed respawn AND an active order flag →
     * flag wins (EventPriority.HIGH fires after NORMAL bed assignment).
     *
     * Simulated by creating the event with isBedSpawn=true and a bed location,
     * then verifying that after our listener fires the respawnLocation is the flag.
     */
    @Test
    fun `TC-31 active flag overrides bed respawn location`() {
        val flagLoc = flagLocation(x = 500.0, y = 70.0, z = 500.0)
        val order = Order(id = orderId, ownerUuid = playerUuid)
        ordersByUuid[playerUuid] = order
        flagManager.locations[orderId] = flagLoc
        flagManager.activeFlags += orderId

        val player = fakePlayer(playerUuid)
        val bedLoc = Location(null, 10.0, 64.0, 10.0) // bed spawn
        val event = respawnEvent(player, bedLoc, isBedSpawn = true)

        listener.onPlayerRespawn(event)

        assertEquals(flagLoc, event.respawnLocation, "Active flag must override bed respawn location (AC-27)")
    }

    /**
     * TC-36: Flag is in a different world from where the player died →
     * respawnLocation is still set to flagLoc (cross-world respawn is allowed by Paper).
     */
    @Test
    fun `TC-36 cross-world respawn sets respawnLocation to flag world`() {
        // Simulate flag in Overworld while player died in Nether
        val overworldChunk = RecordingChunk()
        val overworldWorld = fakeWorld("world", overworldChunk.proxy)
        val flagLoc = Location(overworldWorld, 0.0, 64.0, 0.0)
        val order = Order(id = orderId, ownerUuid = playerUuid)
        ordersByUuid[playerUuid] = order
        flagManager.locations[orderId] = flagLoc
        flagManager.activeFlags += orderId

        val player = fakePlayer(playerUuid)
        val netherLoc = Location(null, 5.0, 64.0, 5.0) // nether default spawn (null world)
        val event = respawnEvent(player, netherLoc)

        listener.onPlayerRespawn(event)

        assertEquals(flagLoc, event.respawnLocation, "Cross-world respawn must set location to flag world (CC-03)")
        assertEquals("world", event.respawnLocation.world?.name, "Respawn world must be the flag's world (Overworld)")
    }

    /**
     * Verifies that when an exception is thrown by a collaborator, the listener
     * catches it and leaves the event unchanged (CC-01 safety net).
     */
    @Test
    fun `exception in collaborator is swallowed and respawnLocation stays unchanged`() {
        val throwingFlagManager = object : FlagStabilityManager {
            @Suppress("TooGenericExceptionThrown")
            override fun getFlagLocation(orderId: Long): Location = throw RuntimeException("simulated failure")
            override fun isFlagActive(orderId: Long): Boolean = true
        }
        val order = Order(id = orderId, ownerUuid = playerUuid)
        ordersByUuid[playerUuid] = order

        val safeListener = OrderRespawnListener(
            flagStabilityManager = throwingFlagManager,
            logger = logger,
            findOrderByOwner = { uuid -> ordersByUuid[uuid] },
        )
        val player = fakePlayer(playerUuid)
        val defaultLoc = Location(null, 0.0, 64.0, 0.0)
        val event = respawnEvent(player, defaultLoc)

        safeListener.onPlayerRespawn(event)

        assertEquals(defaultLoc, event.respawnLocation,
            "Exception must be caught; respawnLocation must stay at default")
    }
}
