package ru.kyamshanov.comminusm.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.WorkdaysRepository
import java.util.UUID

class WorkdaysServiceTest {
    private lateinit var repo: WorkdaysRepository
    private lateinit var service: WorkdaysService
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkdaysRepository(db.connection)
        service = WorkdaysService(repo)
    }

    @Test
    fun `earn adds to balance`() {
        service.earn(uuid, 10)
        assertEquals(10, service.getBalance(uuid))
        service.earn(uuid, 5)
        assertEquals(15, service.getBalance(uuid))
    }

    @Test
    fun `spend returns true and decreases balance when sufficient`() {
        service.earn(uuid, 50)
        assertTrue(service.spend(uuid, 30))
        assertEquals(20, service.getBalance(uuid))
    }

    @Test
    fun `spend returns false when insufficient balance`() {
        assertFalse(service.spend(uuid, 10))
        assertEquals(0, service.getBalance(uuid))
    }

    @Test
    fun `hasEnough returns correct boolean`() {
        assertFalse(service.hasEnough(uuid, 1))
        service.earn(uuid, 100)
        assertTrue(service.hasEnough(uuid, 100))
        assertFalse(service.hasEnough(uuid, 101))
    }
}
