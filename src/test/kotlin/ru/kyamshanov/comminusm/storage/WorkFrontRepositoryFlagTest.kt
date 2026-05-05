package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.WorkFront
import java.util.UUID

class WorkFrontRepositoryFlagTest {
    private lateinit var db: DatabaseManager
    private lateinit var repo: WorkFrontRepository

    @BeforeEach
    fun setUp() {
        db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkFrontRepository(db.connection)
    }

    @Test
    fun `findAllActivated returns empty when no fronts exist`() {
        val all = repo.findAllActivated()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `findAllActivated returns all work fronts`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        repo.upsert(WorkFront(uuid1, "world", 10, 64, 10))
        repo.upsert(WorkFront(uuid2, "world", 20, 64, 20))

        val all = repo.findAllActivated()
        assertEquals(2, all.size)
    }

    @Test
    fun `findAllActivated returns updated front after upsert`() {
        val uuid = UUID.randomUUID()
        repo.upsert(WorkFront(uuid, "world", 10, 64, 10))
        repo.upsert(WorkFront(uuid, "world", 50, 64, 50))

        val all = repo.findAllActivated()
        assertEquals(1, all.size)
        assertEquals(50, all[0].centerX)
    }

    @Test
    fun `findAllActivated returns fronts across multiple worlds`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        repo.upsert(WorkFront(uuid1, "world", 0, 64, 0))
        repo.upsert(WorkFront(uuid2, "nether", 100, 64, 100))

        val all = repo.findAllActivated()
        assertEquals(2, all.size)
    }

    @Test
    fun `findAllActivated does not include deleted front`() {
        val uuid = UUID.randomUUID()
        repo.upsert(WorkFront(uuid, "world", 10, 64, 10))
        repo.deleteByOwner(uuid)

        val all = repo.findAllActivated()
        assertTrue(all.isEmpty())
    }
}
