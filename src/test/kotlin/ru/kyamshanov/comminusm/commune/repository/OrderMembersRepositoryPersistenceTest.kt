package ru.kyamshanov.comminusm.commune.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderMembersRepositoryPersistenceTest {
    private lateinit var connection: Connection
    private lateinit var repository: OrderMembersRepository

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        createTables()
        repository = OrderMembersRepository(mutableMapOf(), connection)
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    private fun createTables() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    name TEXT NOT NULL DEFAULT '',
                    level INTEGER NOT NULL DEFAULT 1,
                    center_world TEXT,
                    center_x INTEGER,
                    center_y INTEGER,
                    center_z INTEGER,
                    radius INTEGER NOT NULL DEFAULT 2,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """.trimIndent(),
            )
        }

        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE order_members (
                    order_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    granted_at TEXT NOT NULL,
                    granted_via TEXT NOT NULL,
                    PRIMARY KEY (order_id, player_uuid),
                    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                    CHECK (granted_via IN ('native', 'commune'))
                )
                """.trimIndent(),
            )
        }
    }

    private fun insertOrder(orderId: Long = 1): Long {
        val stmt =
            connection.prepareStatement(
                "INSERT INTO orders (id, owner_uuid, level, radius) VALUES (?, ?, ?, ?)",
            )
        stmt.setLong(1, orderId)
        stmt.setString(2, UUID.randomUUID().toString())
        stmt.setInt(3, 1)
        stmt.setInt(4, 2)
        stmt.executeUpdate()
        stmt.close()
        return orderId
    }

    @Test
    fun `roundTripAdd_addMember_thenLoadAll_returnsMemberWithCorrectFields`() {
        val orderId = insertOrder(1)
        val playerUuid = UUID.randomUUID()
        val grantedAt = LocalDateTime.now()
        val grantedVia = "native"

        repository.addMember(orderId, playerUuid, grantedVia, grantedAt)

        val loaded = repository.loadAll()

        assertEquals(1, loaded.size)
        val member = loaded[0]
        assertEquals(playerUuid, member.playerUuid)
        assertEquals(orderId, member.orderId)
        assertEquals(grantedVia, member.grantedVia)
        // Compare without nanoseconds since DB stores as TEXT
        assertEquals(grantedAt.withNano(0), member.grantedAt.withNano(0))
    }

    @Test
    fun `removePersisted_addThenRemove_thenLoadAll_memberIsGone`() {
        val orderId = insertOrder(1)
        val playerUuid = UUID.randomUUID()
        val grantedAt = LocalDateTime.now()

        repository.addMember(orderId, playerUuid, "native", grantedAt)
        repository.removeMember(orderId, playerUuid)

        val loaded = repository.loadAll()

        assertEquals(0, loaded.size)
    }

    @Test
    fun `nullConnectionMode_addMember_withNullConnection_onlyUpdatesCache`() {
        val cachelessRepo = OrderMembersRepository(mutableMapOf(), null)
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val grantedAt = LocalDateTime.now()

        cachelessRepo.addMember(orderId, playerUuid, "commune", grantedAt)

        val loaded = cachelessRepo.loadAll()
        assertEquals(0, loaded.size)

        // But cache should be updated
        val cached = cachelessRepo.getMembersOfOrder(orderId)
        assertEquals(1, cached.size)
    }

    @Test
    fun `duplicateAddIsIdempotent_addMemberTwice_withSameOrderAndPlayer_hasOnlyOneRow`() {
        val orderId = insertOrder(1)
        val playerUuid = UUID.randomUUID()
        val grantedAt = LocalDateTime.now()

        repository.addMember(orderId, playerUuid, "native", grantedAt)
        repository.addMember(orderId, playerUuid, "native", grantedAt)

        val loaded = repository.loadAll()

        assertEquals(1, loaded.size)
    }

    @Test
    fun `loadAllPopulatesCache_afterLoadAll_getMembersOfOrderReturnLoaded`() {
        val orderId = insertOrder(1)
        val playerUuid1 = UUID.randomUUID()
        val playerUuid2 = UUID.randomUUID()
        val grantedAt = LocalDateTime.now()

        // Insert directly into DB (bypassing cache)
        val stmt =
            connection.prepareStatement(
                "INSERT INTO order_members (order_id, player_uuid, granted_at, granted_via) VALUES (?, ?, ?, ?)",
            )
        stmt.setLong(1, orderId)
        stmt.setString(2, playerUuid1.toString())
        stmt.setString(3, grantedAt.toString())
        stmt.setString(4, "native")
        stmt.executeUpdate()
        stmt.close()

        val stmt2 =
            connection.prepareStatement(
                "INSERT INTO order_members (order_id, player_uuid, granted_at, granted_via) VALUES (?, ?, ?, ?)",
            )
        stmt2.setLong(1, orderId)
        stmt2.setString(2, playerUuid2.toString())
        stmt2.setString(3, grantedAt.toString())
        stmt2.setString(4, "commune")
        stmt2.executeUpdate()
        stmt2.close()

        val loaded = repository.loadAll()

        assertEquals(2, loaded.size)
        assertTrue(repository.isMember(orderId, playerUuid1))
        assertTrue(repository.isMember(orderId, playerUuid2))
        assertEquals(2, repository.getMembersOfOrder(orderId).size)
    }
}
