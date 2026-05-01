package ru.kyamshanov.comminusm.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

class WorkFrontServiceTest {
    private lateinit var repo: WorkFrontRepository
    private lateinit var service: WorkFrontService
    private val uuid = UUID.randomUUID()
    private val uuid2 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkFrontRepository(db.connection)
        service = WorkFrontService(repo, 25)
    }

    @Test
    fun `activate creates front when none exists`() {
        val success = service.activate(uuid, "world", 100, 64, 100)
        assertTrue(success)
        val front = service.getByOwner(uuid)
        assertNotNull(front)
        assertEquals(25, front!!.radius)
    }

    @Test
    fun `activate replaces old front when one exists`() {
        service.activate(uuid, "world", 0, 64, 0)
        service.activate(uuid, "nether", 50, 70, 50)
        val front = service.getByOwner(uuid)
        assertEquals("nether", front!!.centerWorld)
        assertEquals(50, front.centerX)
    }

    @Test
    fun `deactivate removes front`() {
        service.activate(uuid, "world", 10, 64, 10)
        service.deactivate(uuid)
        assertNull(service.getByOwner(uuid))
    }

    @Test
    fun `getAllInWorld returns only fronts in that world`() {
        service.activate(uuid, "world", 0, 64, 0)
        service.activate(uuid2, "world", 100, 64, 100)
        assertEquals(2, service.getAllInWorld("world").size)
        assertEquals(0, service.getAllInWorld("nether").size)
    }
}
