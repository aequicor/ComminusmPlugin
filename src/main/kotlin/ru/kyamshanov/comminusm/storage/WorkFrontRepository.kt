package ru.kyamshanov.comminusm.storage

import ru.kyamshanov.comminusm.model.WorkFront
import java.sql.Connection
import java.util.UUID

class WorkFrontRepository(private val conn: Connection) {

    fun upsert(front: WorkFront) {
        val stmt = conn.prepareStatement(
            """
            INSERT INTO work_fronts (owner_uuid, center_world, center_x, center_y, center_z, radius)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(owner_uuid) DO UPDATE SET
                center_world = excluded.center_world,
                center_x = excluded.center_x,
                center_y = excluded.center_y,
                center_z = excluded.center_z,
                radius = excluded.radius,
                created_at = datetime('now')
            """.trimIndent()
        )
        stmt.setString(1, front.ownerUuid.toString())
        stmt.setString(2, front.centerWorld)
        stmt.setInt(3, front.centerX)
        stmt.setInt(4, front.centerY)
        stmt.setInt(5, front.centerZ)
        stmt.setInt(6, front.radius)
        stmt.executeUpdate()
        stmt.close()
    }

    fun findByOwner(uuid: UUID): WorkFront? {
        val stmt = conn.prepareStatement(
            "SELECT owner_uuid, center_world, center_x, center_y, center_z, radius, created_at FROM work_fronts WHERE owner_uuid = ?"
        )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val result = if (rs.next()) {
            WorkFront(
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                centerWorld = rs.getString("center_world"),
                centerX = rs.getInt("center_x"),
                centerY = rs.getInt("center_y"),
                centerZ = rs.getInt("center_z"),
                radius = rs.getInt("radius"),
                createdAt = rs.getString("created_at")
            )
        } else null
        rs.close()
        stmt.close()
        return result
    }

    fun deleteByOwner(uuid: UUID) {
        val stmt = conn.prepareStatement("DELETE FROM work_fronts WHERE owner_uuid = ?")
        stmt.setString(1, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    fun findAllInWorld(world: String): List<WorkFront> {
        val stmt = conn.prepareStatement(
            "SELECT owner_uuid, center_world, center_x, center_y, center_z, radius, created_at FROM work_fronts WHERE center_world = ?"
        )
        stmt.setString(1, world)
        val rs = stmt.executeQuery()
        val result = mutableListOf<WorkFront>()
        while (rs.next()) {
            result.add(
                WorkFront(
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at")
                )
            )
        }
        rs.close()
        stmt.close()
        return result
    }
}
