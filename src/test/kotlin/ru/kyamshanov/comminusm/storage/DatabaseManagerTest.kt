package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import kotlin.test.assertEquals

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

    @Test
    fun `migration adds name column to orders table on existing database without it`() {
        // Arrange: Create an old-schema database (without name column)
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }

        // Create the old schema (without name column, like pre-feat-order-name)
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
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

        // Verify the column does NOT exist yet
        val oldColumns = conn.metaData.getColumns(null, null, "orders", null)
        var hasNameColumn = false
        while (oldColumns.next()) {
            if (oldColumns.getString("COLUMN_NAME") == "name") {
                hasNameColumn = true
            }
        }
        oldColumns.close()
        assertTrue(!hasNameColumn, "name column should not exist before migration")

        // Act: Run the migration
        DatabaseManager.migrateOrders(conn)

        // Assert: Verify the name column now exists
        val newColumns = conn.metaData.getColumns(null, null, "orders", null)
        var nameColumnFound = false
        while (newColumns.next()) {
            if (newColumns.getString("COLUMN_NAME") == "name") {
                nameColumnFound = true
                assertEquals("TEXT", newColumns.getString("TYPE_NAME").uppercase())
            }
        }
        newColumns.close()
        assertTrue(nameColumnFound, "name column should exist after migration")

        // Assert: Verify idempotency — running migration again should not throw
        DatabaseManager.migrateOrders(conn)

        conn.close()
    }
}
