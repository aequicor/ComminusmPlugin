package ru.kyamshanov.comminusm.model

import org.bukkit.Location
import org.bukkit.Bukkit
import java.util.UUID

data class Order(
    val id: Long = 0,
    val ownerUuid: UUID,
    val level: Int = 1,
    val centerWorld: String? = null,
    val centerX: Int = 0,
    val centerY: Int = 0,
    val centerZ: Int = 0,
    val radius: Int = 2,
    val createdAt: String = ""
) {
    val center: Location?
        get() = centerWorld?.let { Bukkit.getWorld(it)?.let { w -> Location(w, centerX.toDouble(), centerY.toDouble(), centerZ.toDouble()) } }

    val size: Int
        get() = radius * 2 + 1

    val isActivated: Boolean
        get() = centerWorld != null
}
