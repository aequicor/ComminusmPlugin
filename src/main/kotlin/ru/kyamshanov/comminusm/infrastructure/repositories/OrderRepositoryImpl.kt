package ru.kyamshanov.comminusm.infrastructure.repositories

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.model.Order
import java.sql.Connection
import java.util.UUID

/**
 * SQL-based implementation of OrderRepository.
 * Handles persistence of Order entities using SQLite database.
 */
@Suppress("MagicNumber", "MaxLineLength")
class OrderRepositoryImpl(
    private val conn: Connection,
) : OrderRepository {
    override fun insert(order: Order): Long {
        val stmt =
            conn.prepareStatement(
                "INSERT INTO orders (owner_uuid, name, level, radius) VALUES (?, ?, ?, ?)",
            )
        stmt.setString(1, order.ownerUuid.toString())
        stmt.setString(2, order.name)
        stmt.setInt(3, order.level)
        stmt.setInt(4, order.radius)
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        val id = if (rs.next()) rs.getLong(1) else 0L
        rs.close()
        stmt.close()
        return id
    }

    override fun findByOwner(uuid: UUID): Order? {
        val stmt =
            conn.prepareStatement(
                "SELECT id, owner_uuid, name, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE owner_uuid = ?",
            )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val result =
            if (rs.next()) {
                Order(
                    id = rs.getLong("id"),
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    name = rs.getString("name") ?: "",
                    level = rs.getInt("level"),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at"),
                )
            } else {
                null
            }
        rs.close()
        stmt.close()
        return result
    }

    override fun updateLevel(
        uuid: UUID,
        level: Int,
        radius: Int,
    ) {
        val stmt =
            conn.prepareStatement(
                "UPDATE orders SET level = ?, radius = ? WHERE owner_uuid = ?",
            )
        stmt.setInt(1, level)
        stmt.setInt(2, radius)
        stmt.setString(3, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    override fun activate(
        uuid: UUID,
        world: String,
        x: Int,
        y: Int,
        z: Int,
    ) {
        val stmt =
            conn.prepareStatement(
                "UPDATE orders SET center_world = ?, center_x = ?, center_y = ?, center_z = ? WHERE owner_uuid = ?",
            )
        stmt.setString(1, world)
        stmt.setInt(2, x)
        stmt.setInt(3, y)
        stmt.setInt(4, z)
        stmt.setString(5, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    override fun findAllInWorld(world: String): List<Order> {
        val stmt =
            conn.prepareStatement(
                "SELECT id, owner_uuid, name, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE center_world = ?",
            )
        stmt.setString(1, world)
        val rs = stmt.executeQuery()
        val result = mutableListOf<Order>()
        while (rs.next()) {
            result.add(
                Order(
                    id = rs.getLong("id"),
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    name = rs.getString("name") ?: "",
                    level = rs.getInt("level"),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at"),
                ),
            )
        }
        rs.close()
        stmt.close()
        return result
    }

    override fun findAllActivated(): List<Order> {
        val stmt =
            conn.prepareStatement(
                "SELECT id, owner_uuid, name, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE center_world IS NOT NULL",
            )
        val rs = stmt.executeQuery()
        val result = mutableListOf<Order>()
        while (rs.next()) {
            result.add(
                Order(
                    id = rs.getLong("id"),
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    name = rs.getString("name") ?: "",
                    level = rs.getInt("level"),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at"),
                ),
            )
        }
        rs.close()
        stmt.close()
        return result
    }

    override fun findById(id: Long): Order? {
        val sql =
            """
            SELECT id, owner_uuid, name, level, center_world, center_x,
                   center_y, center_z, radius, created_at
            FROM orders WHERE id = ?
            """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return Order(
                    id = rs.getLong("id"),
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    name = rs.getString("name") ?: "",
                    level = rs.getInt("level"),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at"),
                )
            }
        }
        return null
    }

    override fun deleteByOwner(uuid: UUID) {
        val stmt = conn.prepareStatement("DELETE FROM orders WHERE owner_uuid = ?")
        stmt.setString(1, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    override fun update(order: Order) {
        // Not currently used in the codebase, but provided for interface completeness
        val stmt =
            conn.prepareStatement(
                "UPDATE orders SET name = ?, level = ?, radius = ?, center_world = ?, center_x = ?, center_y = ?, center_z = ? WHERE id = ?",
            )
        stmt.setString(1, order.name)
        stmt.setInt(2, order.level)
        stmt.setInt(3, order.radius)
        stmt.setString(4, order.centerWorld)
        stmt.setInt(5, order.centerX)
        stmt.setInt(6, order.centerY)
        stmt.setInt(7, order.centerZ)
        stmt.setLong(8, order.id)
        stmt.executeUpdate()
        stmt.close()
    }
}
