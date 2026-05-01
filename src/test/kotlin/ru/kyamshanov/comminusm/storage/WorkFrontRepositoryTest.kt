package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.WorkFront
import java.util.UUID

class WorkFrontRepositoryTest {
    private lateinit var db: DatabaseManager
    private lateinit var repo: WorkFrontRepository
    private lateinit var wdRepo: WorkdaysRepository
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkFrontRepository(db.connection)
        wdRepo = WorkdaysRepository(db.connection)
    }

    @Test
    fun `upsert creates and updates work front`() {
        val front = WorkFront(uuid, "world", 10, 64, 10)
        repo.upsert(front)
        val found = repo.findByOwner(uuid)
        assertNotNull(found)
        assertEquals(10, found!!.centerX)

        repo.upsert(WorkFront(uuid, "world_nether", 20, 100, 20))
        val updated = repo.findByOwner(uuid)
        assertEquals("world_nether", updated!!.centerWorld)
        assertEquals(100, updated.centerY)
    }

    @Test
    fun `delete removes work front`() {
        repo.upsert(WorkFront(uuid, "world", 0, 64, 0))
        assertNotNull(repo.findByOwner(uuid))
        repo.deleteByOwner(uuid)
        assertNull(repo.findByOwner(uuid))
    }

    @Test
    fun `workdays add and get balance`() {
        wdRepo.add(uuid, 50)
        assertEquals(50, wdRepo.getBalance(uuid))
        wdRepo.add(uuid, 30)
        assertEquals(80, wdRepo.getBalance(uuid))
    }
}
