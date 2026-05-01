package ru.kyamshanov.comminusm.config

import org.bukkit.configuration.file.FileConfiguration

data class OrderLevelConfig(val level: Int, val radius: Int, val cost: Int)

class PluginConfig(private val config: FileConfiguration) {

    val minDistanceBetweenCenters: Int
        get() = config.getInt("private-system.order.min-distance-between-centers", 30)

    val orderLevels: List<OrderLevelConfig> by lazy {
        config.getMapList("private-system.order.levels")
            .map { map ->
                OrderLevelConfig(
                    level = (map["level"] as? Number)?.toInt() ?: 1,
                    radius = (map["radius"] as? Number)?.toInt() ?: 2,
                    cost = (map["cost"] as? Number)?.toInt() ?: 0
                )
            }
            .ifEmpty { defaultOrderLevels() }
    }

    val frontRadius: Int
        get() = config.getInt("private-system.front.radius", 25)

    val passiveIncomeIntervalMinutes: Int
        get() = config.getInt("private-system.workdays.passive-income-interval-minutes", 10)

    val passiveIncomeAmount: Int
        get() = config.getInt("private-system.workdays.passive-income-amount", 1)

    val resourceRates: Map<String, Int> by lazy {
        val section = config.getConfigurationSection("private-system.workdays.resource-rates")
        if (section != null) {
            section.getKeys(false).associateWith { key -> section.getInt(key, 0) }
        } else {
            defaultResourceRates()
        }
    }

    companion object {
        fun defaultOrderLevels(): List<OrderLevelConfig> = listOf(
            OrderLevelConfig(1, 2, 0),
            OrderLevelConfig(2, 3, 30),
            OrderLevelConfig(3, 4, 80),
            OrderLevelConfig(4, 5, 150),
            OrderLevelConfig(5, 7, 300)
        )

        fun defaultResourceRates(): Map<String, Int> = mapOf(
            "COBBLESTONE" to 4,
            "COAL" to 6,
            "IRON_INGOT" to 12,
            "GOLD_INGOT" to 20,
            "DIAMOND" to 40,
            "OAK_LOG" to 4,
            "DIRT" to 1
        )
    }
}
