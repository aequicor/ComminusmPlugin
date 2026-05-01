package ru.kyamshanov.comminusm.storage

import ru.kyamshanov.comminusm.model.Order
import java.sql.Connection
import java.util.UUID

class OrderRepository(private val conn: Connection) {

    fun insert(order: Order): Long {
        val stmt = conn.prepareStatement(
            "INSERT INTO orders (owner_uuid, level, radius) VALUES (?, ?, ?)"
        )
        stmt.setString(1, order.ownerUuid.toString())
        stmt.setInt(2, order.level)
        stmt.setInt(3, order.radius)
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        val id = if (rs.next()) rs.getLong(1) else 0L
        rs.close()
        stmt.close()
        return id
    }

    fun findByOwner(uuid: UUID): Order? {
        val stmt = conn.prepareStatement(
            "SELECT id, owner_uuid, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE owner_uuid = ?"
        )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val result = if (rs.next()) {
            Order(
                id = rs.getLong("id"),
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                level = rs.getInt("level"),
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

    fun updateLevel(uuid: UUID, newLevel: Int, newRadius: Int) {
        val stmt = conn.prepareStatement(
            "UPDATE orders SET level = ?, radius = ? WHERE owner_uuid = ?"
        )
        stmt.setInt(1, newLevel)
        stmt.setInt(2, newRadius)
        stmt.setString(3, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int) {
        val stmt = conn.prepareStatement(
            "UPDATE orders SET center_world = ?, center_x = ?, center_y = ?, center_z = ? WHERE owner_uuid = ?"
        )
        stmt.setString(1, world)
        stmt.setInt(2, x)
        stmt.setInt(3, y)
        stmt.setInt(4, z)
        stmt.setString(5, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    fun findAllInWorld(world: String): List<Order> {
        val stmt = conn.prepareStatement(
            "SELECT id, owner_uuid, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE center_world = ?"
        )
        stmt.setString(1, world)
        val rs = stmt.executeQuery()
        val result = mutableListOf<Order>()
        while (rs.next()) {
            result.add(
                Order(
                    id = rs.getLong("id"),
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    level = rs.getInt("level"),
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

    fun deleteByOwner(uuid: UUID) {
        val stmt = conn.prepareStatement("DELETE FROM orders WHERE owner_uuid = ?")
        stmt.setString(1, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }
}
