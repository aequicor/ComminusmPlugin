package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.Order
import java.util.UUID

class OrderRepositoryFlagTest {
    private lateinit var db: DatabaseManager
    private lateinit var repo: OrderRepository

    @BeforeEach
    fun setUp() {
        db = DatabaseManager("jdbc:sqlite::memory:")
        repo = OrderRepository(db.connection)
    }

    @Test
    fun `findAllActivated returns only orders with center_world set`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()

        repo.insert(Order(ownerUuid = uuid1, level = 1, radius = 2))
        repo.insert(Order(ownerUuid = uuid2, level = 1, radius = 2))

        repo.activate(uuid1, "world", 100, 64, 100)

        val activated = repo.findAllActivated()
        assertEquals(1, activated.size)
        assertEquals(uuid1, activated[0].ownerUuid)
        assertEquals("world", activated[0].centerWorld)
    }

    @Test
    fun `findAllActivated returns empty list when no orders activated`() {
        val uuid = UUID.randomUUID()
        repo.insert(Order(ownerUuid = uuid, level = 1, radius = 2))

        val activated = repo.findAllActivated()
        assertTrue(activated.isEmpty())
    }

    @Test
    fun `findAllActivated returns empty list when no orders exist`() {
        assertTrue(repo.findAllActivated().isEmpty())
    }

    @Test
    fun `findAllActivated returns all activated orders`() {
        val uuids = (1..3).map { UUID.randomUUID() }
        uuids.forEach { repo.insert(Order(ownerUuid = it, level = 1, radius = 2)) }
        uuids.forEach { repo.activate(it, "world", 0, 64, 0) }

        val activated = repo.findAllActivated()
        assertEquals(3, activated.size)
    }

    @Test
    fun `findAllActivated excludes orders without center_world after partial activation`() {
        val activatedUuid = UUID.randomUUID()
        val pendingUuid = UUID.randomUUID()

        repo.insert(Order(ownerUuid = activatedUuid, level = 1, radius = 2))
        repo.insert(Order(ownerUuid = pendingUuid, level = 1, radius = 2))
        repo.activate(activatedUuid, "world", 0, 64, 0)

        val activated = repo.findAllActivated()
        assertEquals(1, activated.size)
        assertEquals(activatedUuid, activated[0].ownerUuid)
    }
}
