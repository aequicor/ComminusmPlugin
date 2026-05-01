package ru.kyamshanov.comminusm.model

import org.bukkit.Location
import org.bukkit.Bukkit
import java.util.UUID

data class WorkFront(
    val ownerUuid: UUID,
    val centerWorld: String,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Int = 25,
    val createdAt: String = ""
) {
    val center: Location?
        get() = Bukkit.getWorld(centerWorld)?.let { Location(it, centerX.toDouble(), centerY.toDouble(), centerZ.toDouble()) }

    val size: Int
        get() = radius * 2 + 1
}
