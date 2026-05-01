package ru.kyamshanov.comminusm.storage

import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class DatabaseManager(jdbcUrl: String) {

    val connection: Connection by lazy {
        val conn = DriverManager.getConnection(jdbcUrl)
        conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        createTables(conn)
        conn
    }

    constructor(plugin: Plugin) : this(
        "jdbc:sqlite:${plugin.dataFolder.absolutePath}${File.separator}data.db"
    )

    private fun createTables(conn: Connection) {
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
                """.trimIndent()
            )
        }

        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS work_fronts (
                    owner_uuid TEXT PRIMARY KEY,
                    center_world TEXT NOT NULL,
                    center_x INTEGER NOT NULL,
                    center_y INTEGER NOT NULL,
                    center_z INTEGER NOT NULL,
                    radius INTEGER NOT NULL DEFAULT 25,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """.trimIndent()
            )
        }

        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS workdays (
                    player_uuid TEXT PRIMARY KEY,
                    balance INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    fun integrityCheck(): Boolean {
        return connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA integrity_check").use { rs ->
                rs.getString(1) == "ok"
            }
        }
    }

    fun close() {
        try {
            connection.close()
        } catch (_: Exception) {
            // already closed
        }
    }
}
