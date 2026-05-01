package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DatabaseManagerTest {
    @Test
    fun `in-memory database creates tables successfully`() {
        val manager = DatabaseManager("jdbc:sqlite::memory:")
        val conn = manager.connection
        val rs = conn.metaData.getTables(null, null, "orders", null)
        assertTrue(rs.next(), "orders table should exist")
        rs.close()

        val rs2 = conn.metaData.getTables(null, null, "work_fronts", null)
        assertTrue(rs2.next(), "work_fronts table should exist")
        rs2.close()

        val rs3 = conn.metaData.getTables(null, null, "workdays", null)
        assertTrue(rs3.next(), "workdays table should exist")
        rs3.close()

        conn.close()
    }
}
