package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Bukkit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CommuneService
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommuneStartupTaskTest {
    private lateinit var connection: Connection
    private lateinit var communeService: CommuneService
    private lateinit var orderMembersRepository: OrderMembersRepository
    private lateinit var startupTask: CommuneStartupTask

    @BeforeEach
    fun setUp() {
        // Mock Bukkit
        mockk<Bukkit>(relaxed = true)

        // Create in-memory SQLite database
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        createTables()

        // Initialize repositories and service
        val communeModels = ConcurrentHashMap<UUID, Commune>()
        val orderToCommuneId = ConcurrentHashMap<Long, UUID>()
        communeService = CommuneService(communeModels, orderToCommuneId)
        orderMembersRepository = OrderMembersRepository(ConcurrentHashMap(), connection)

        // Initialize startup task
        startupTask = CommuneStartupTask(communeService, orderMembersRepository, connection)
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    private fun createTables() {
        connection.createStatement().use { stmt ->
            // Create communes table
            stmt.executeUpdate(
                """
                CREATE TABLE communes (
                    id TEXT PRIMARY KEY,
                    created_at TEXT NOT NULL,
                    version INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create commune_orders table
            stmt.executeUpdate(
                """
                CREATE TABLE commune_orders (
                    commune_id TEXT NOT NULL,
                    order_id INTEGER NOT NULL,
                    PRIMARY KEY (commune_id, order_id)
                )
                """.trimIndent(),
            )

            // Create order_members table
            stmt.executeUpdate(
                """
                CREATE TABLE order_members (
                    order_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    granted_at TEXT NOT NULL,
                    granted_via TEXT NOT NULL,
                    PRIMARY KEY (order_id, player_uuid)
                )
                """.trimIndent(),
            )

            // Create orders table (dummy for compatibility)
            stmt.executeUpdate(
                """
                CREATE TABLE orders (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun loadCommunes_populatesCommuneService() {
        // Setup: insert commune and commune_orders
        val communeId = UUID.randomUUID()
        val orderId = 1L
        val createdAt = LocalDateTime.now()

        connection
            .prepareStatement(
                "INSERT INTO communes (id, created_at, version) VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setString(2, createdAt.toString())
                stmt.setLong(3, 0L)
                stmt.executeUpdate()
            }

        connection
            .prepareStatement(
                "INSERT INTO commune_orders (commune_id, order_id) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setLong(2, orderId)
                stmt.executeUpdate()
            }

        // Act: load communes
        startupTask.loadCommunes()

        // Assert: commune service contains the loaded commune
        val loadedCommune = communeService.getCommuneOfOrder(orderId)
        assertNotNull(loadedCommune, "Loaded commune should not be null")
        assertEquals(communeId, loadedCommune.id, "Commune ID should match")
        assertEquals(setOf(orderId), loadedCommune.orderIds, "Order IDs should match")
        assertEquals(0L, loadedCommune.version, "Version should match")
    }

    @Test
    fun loadCommunes_populatesOrderMembersCache() {
        // Setup: insert order_members
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val grantedAt = LocalDateTime.now()

        connection
            .prepareStatement(
                "INSERT INTO order_members (order_id, player_uuid, granted_at, granted_via) VALUES (?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, orderId)
                stmt.setString(2, playerUuid.toString())
                stmt.setString(3, grantedAt.toString())
                stmt.setString(4, "native")
                stmt.executeUpdate()
            }

        // Act: load communes (which includes loading order members)
        startupTask.loadCommunes()

        // Assert: member is in cache
        assertTrue(
            orderMembersRepository.isMember(orderId, playerUuid),
            "Player should be a member of the order",
        )
    }

    @Test
    fun loadCommunes_nullConnection_noSideEffects() {
        // Setup: create new task with null connection
        val taskWithoutDb = CommuneStartupTask(communeService, orderMembersRepository, null)

        // Act: load communes with null connection
        taskWithoutDb.loadCommunes()

        // Assert: commune service should be empty
        val allCommunes = communeService.getAllCommunes()
        assertEquals(0, allCommunes.size, "No communes should be loaded with null connection")
        assertFalse(
            taskWithoutDb.storageLoadFailed,
            "storageLoadFailed should be false for null connection (no-db mode)",
        )
    }

    @Test
    fun performConsistencyCheck_removesOrphanCrossOrderMember() {
        // Setup: create commune with two orders
        val communeId = UUID.randomUUID()
        val orderA = 1L
        val orderB = 2L
        val createdAt = LocalDateTime.now()
        val orphanPlayerUuid = UUID.randomUUID()

        // Insert commune
        connection
            .prepareStatement(
                "INSERT INTO communes (id, created_at, version) VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setString(2, createdAt.toString())
                stmt.setLong(3, 0L)
                stmt.executeUpdate()
            }

        // Insert commune_orders
        connection
            .prepareStatement(
                "INSERT INTO commune_orders (commune_id, order_id) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setLong(2, orderA)
                stmt.executeUpdate()
            }

        connection
            .prepareStatement(
                "INSERT INTO commune_orders (commune_id, order_id) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setLong(2, orderB)
                stmt.executeUpdate()
            }

        // Load communes to populate service
        startupTask.loadCommunes()

        // Add orphan member (commune-granted in orderB, no native anywhere)
        orderMembersRepository.addMember(orderB, orphanPlayerUuid, "commune", createdAt)

        // Assert member exists before check
        assertTrue(
            orderMembersRepository.isMember(orderB, orphanPlayerUuid),
            "Orphan member should exist before consistency check",
        )

        // Act: perform consistency check
        startupTask.performConsistencyCheck()

        // Assert: orphan member is removed
        assertFalse(
            orderMembersRepository.isMember(orderB, orphanPlayerUuid),
            "Orphan member should be removed after consistency check",
        )
    }

    @Test
    fun performConsistencyCheck_keepsValidCrossOrderMember() {
        // Setup: create commune with two orders
        val communeId = UUID.randomUUID()
        val orderA = 1L
        val orderB = 2L
        val createdAt = LocalDateTime.now()
        val playerUuid = UUID.randomUUID()

        // Insert commune
        connection
            .prepareStatement(
                "INSERT INTO communes (id, created_at, version) VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setString(2, createdAt.toString())
                stmt.setLong(3, 0L)
                stmt.executeUpdate()
            }

        // Insert commune_orders
        connection
            .prepareStatement(
                "INSERT INTO commune_orders (commune_id, order_id) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setLong(2, orderA)
                stmt.executeUpdate()
            }

        connection
            .prepareStatement(
                "INSERT INTO commune_orders (commune_id, order_id) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setString(1, communeId.toString())
                stmt.setLong(2, orderB)
                stmt.executeUpdate()
            }

        // Load communes to populate service
        startupTask.loadCommunes()

        // Add native member in orderA and commune-granted in orderB
        orderMembersRepository.addMember(orderA, playerUuid, "native", createdAt)
        orderMembersRepository.addMember(orderB, playerUuid, "commune", createdAt)

        // Assert members exist before check
        assertTrue(
            orderMembersRepository.isMember(orderA, playerUuid),
            "Native member should exist in orderA",
        )
        assertTrue(
            orderMembersRepository.isMember(orderB, playerUuid),
            "Commune member should exist in orderB",
        )

        // Act: perform consistency check
        startupTask.performConsistencyCheck()

        // Assert: commune-granted member is kept (valid cross-order membership)
        assertTrue(
            orderMembersRepository.isMember(orderB, playerUuid),
            "Valid cross-order member should be kept",
        )
    }

    @Test
    fun loadCommunes_storageFailure_setsStorageLoadFailed() {
        // Setup: create a broken connection that throws on prepareStatement
        val brokenConnection: Connection = mockk()
        every {
            brokenConnection.prepareStatement(any<String>())
        } throws
            java.sql.SQLException(
                "Connection is broken",
            )

        // Create task with broken connection
        val taskWithBrokenDb =
            CommuneStartupTask(communeService, orderMembersRepository, brokenConnection)

        // Act: try to load communes
        taskWithBrokenDb.loadCommunes()

        // Assert: storageLoadFailed is true
        assertTrue(
            taskWithBrokenDb.storageLoadFailed,
            "storageLoadFailed should be true when storage operation fails",
        )
    }
}
