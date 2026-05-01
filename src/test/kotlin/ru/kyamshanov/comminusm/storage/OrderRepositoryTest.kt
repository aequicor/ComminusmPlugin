package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.Order
import java.util.UUID

class OrderRepositoryTest {
    private lateinit var db: DatabaseManager
    private lateinit var repo: OrderRepository
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        db = DatabaseManager("jdbc:sqlite::memory:")
        repo = OrderRepository(db.connection)
    }

    @Test
    fun `insert and find by uuid returns order`() {
        val order = Order(ownerUuid = uuid)
        val id = repo.insert(order)
        assertTrue(id > 0, "insert should return positive id")

        val found = repo.findByOwner(uuid)
        assertNotNull(found, "should find order by owner uuid")
        assertEquals(uuid, found!!.ownerUuid)
    }

    @Test
    fun `update level changes the order level and radius`() {
        repo.insert(Order(ownerUuid = uuid))
        repo.updateLevel(uuid, 3, 4)

        val found = repo.findByOwner(uuid)
        assertNotNull(found)
        assertEquals(3, found!!.level)
        assertEquals(4, found.radius)
    }

    @Test
    fun `activate sets center coordinates`() {
        repo.insert(Order(ownerUuid = uuid))
        repo.activate(uuid, "world", 100, 64, 200)

        val found = repo.findByOwner(uuid)
        assertNotNull(found)
        assertEquals("world", found!!.centerWorld)
        assertEquals(100, found.centerX)
        assertEquals(64, found.centerY)
        assertEquals(200, found.centerZ)
    }
}
