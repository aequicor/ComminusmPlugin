package ru.kyamshanov.comminusm.infrastructure.repositories

import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import java.sql.Connection
import java.util.UUID

/**
 * SQL-based implementation of WorkdaysRepository.
 * Handles persistence of workdays balance using SQLite database.
 */
@Suppress("MagicNumber")
class WorkdaysRepositoryImpl(private val conn: Connection) : WorkdaysRepository {

    override fun add(uuid: UUID, amount: Int) {
        val stmt = conn.prepareStatement(
            """
            INSERT INTO workdays (player_uuid, balance) VALUES (?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET balance = balance + ?
            """.trimIndent()
        )
        stmt.setString(1, uuid.toString())
        stmt.setInt(2, amount)
        stmt.setInt(3, amount)
        stmt.executeUpdate()
        stmt.close()
    }

    override fun spend(uuid: UUID, amount: Int): Boolean {
        val stmt = conn.prepareStatement(
            "UPDATE workdays SET balance = balance - ? WHERE player_uuid = ? AND balance >= ?"
        )
        stmt.setInt(1, amount)
        stmt.setString(2, uuid.toString())
        stmt.setInt(3, amount)
        val updated = stmt.executeUpdate()
        stmt.close()
        return updated > 0
    }

    override fun getBalance(uuid: UUID): Int {
        val stmt = conn.prepareStatement(
            "SELECT balance FROM workdays WHERE player_uuid = ?"
        )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val balance = if (rs.next()) rs.getInt("balance") else 0
        rs.close()
        stmt.close()
        return balance
    }
}
