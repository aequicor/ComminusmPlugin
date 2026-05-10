@file:Suppress("MaxLineLength")

package ru.kyamshanov.comminusm.config

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin

data class OrderLevelConfig(
    val level: Int,
    val radius: Int,
    val cost: Int,
)

class PluginConfig(
    private val config: FileConfiguration,
) {
    val minDistanceBetweenCenters: Int
        get() = config.getInt("private-system.order.min-distance-between-centers", DEFAULT_MIN_DISTANCE)

    val orderLevels: List<OrderLevelConfig> by lazy {
        config
            .getMapList("private-system.order.levels")
            .map { map ->
                OrderLevelConfig(
                    level = (map["level"] as? Number)?.toInt() ?: DEFAULT_ORDER_LEVEL,
                    radius = (map["radius"] as? Number)?.toInt() ?: DEFAULT_ORDER_RADIUS,
                    cost = (map["cost"] as? Number)?.toInt() ?: DEFAULT_ORDER_COST,
                )
            }.ifEmpty { defaultOrderLevels() }
    }

    val frontRadius: Int
        get() = config.getInt("private-system.front.radius", DEFAULT_FRONT_RADIUS)

    val passiveIncomeIntervalMinutes: Int
        get() = config.getInt("private-system.workdays.passive-income-interval-minutes", DEFAULT_PASSIVE_INCOME_INTERVAL)

    val passiveIncomeAmount: Int
        get() = config.getInt("private-system.workdays.passive-income-amount", DEFAULT_PASSIVE_INCOME_AMOUNT)

    val resourceRates: Map<String, Int> by lazy {
        val section = config.getConfigurationSection("private-system.workdays.resource-rates")
        if (section != null) {
            section.getKeys(false).associateWith { key -> section.getInt(key, 0) }
        } else {
            defaultResourceRates()
        }
    }

    val flagSupportBlockMaterial: Material
        get() {
            val name = config.getString("flag.supportBlockMaterial", "BEDROCK") ?: "BEDROCK"
            return runCatching { Material.valueOf(name) }
                .getOrElse {
                    ComminusmPlugin.getInstance().logger.severe(
                        "flag.supportBlockMaterial '$name' is invalid — falling back to BEDROCK",
                    )
                    Material.BEDROCK
                }
        }

    val flagMinAirAbove: Int
        get() = config.getInt("flag.minAirAbove", DEFAULT_FLAG_MIN_AIR_ABOVE).coerceAtLeast(MIN_FLAG_AIR_ABOVE)

    val flagTitleFormat: String
        get() = config.getString("flag.titleFormat", "§6{type} — §f{player}") ?: "§6{type} — §f{player}"

    val flagMaxPerChunk: Int
        get() {
            val v = config.getInt("flag.maxPerChunk", DEFAULT_FLAG_MAX_PER_CHUNK)
            if (v <= 0) {
                ComminusmPlugin.getInstance().logger.warning(
                    "flag.maxPerChunk must be ≥ 1, got $v — using default $DEFAULT_FLAG_MAX_PER_CHUNK",
                )
                return DEFAULT_FLAG_MAX_PER_CHUNK
            }
            return v
        }

    val flagAllowedWorlds: Set<String>
        get() {
            val list = config.getStringList("flag.allowedWorlds")
            if (list.isEmpty()) {
                ComminusmPlugin.getInstance().logger.warning(
                    "flag.allowedWorlds is empty — flag placement is disabled in all worlds",
                )
            }
            return list.toSet()
        }

    val flagStartupScanBatchSize: Int
        get() =
            config
                .getInt("flag.startupScanBatchSize", DEFAULT_FLAG_STARTUP_SCAN_BATCH_SIZE)
                .coerceAtLeast(MIN_FLAG_STARTUP_SCAN_BATCH_SIZE)

    companion object {
        // Flag configuration defaults
        const val DEFAULT_FLAG_MIN_AIR_ABOVE = 1
        const val MIN_FLAG_AIR_ABOVE = 0
        const val DEFAULT_FLAG_MAX_PER_CHUNK = 50
        const val DEFAULT_FLAG_STARTUP_SCAN_BATCH_SIZE = 10
        const val MIN_FLAG_STARTUP_SCAN_BATCH_SIZE = 1

        // Order level defaults
        const val DEFAULT_MIN_DISTANCE = 30
        const val DEFAULT_ORDER_LEVEL = 1
        const val DEFAULT_ORDER_RADIUS = 2
        const val DEFAULT_ORDER_COST = 0

        // Front configuration defaults
        const val DEFAULT_FRONT_RADIUS = 25

        // Passive income defaults
        const val DEFAULT_PASSIVE_INCOME_INTERVAL = 10
        const val DEFAULT_PASSIVE_INCOME_AMOUNT = 1

        // Order level constants
        const val ORDER_LEVEL_1 = 1
        const val ORDER_LEVEL_1_RADIUS = 2
        const val ORDER_LEVEL_1_COST = 0
        const val ORDER_LEVEL_2 = 2
        const val ORDER_LEVEL_2_RADIUS = 3
        const val ORDER_LEVEL_2_COST = 30
        const val ORDER_LEVEL_3 = 3
        const val ORDER_LEVEL_3_RADIUS = 4
        const val ORDER_LEVEL_3_COST = 80
        const val ORDER_LEVEL_4 = 4
        const val ORDER_LEVEL_4_RADIUS = 5
        const val ORDER_LEVEL_4_COST = 150
        const val ORDER_LEVEL_5 = 5
        const val ORDER_LEVEL_5_RADIUS = 7
        const val ORDER_LEVEL_5_COST = 300

        // Resource rate defaults
        const val RESOURCE_COBBLESTONE_RATE = 4
        const val RESOURCE_COAL_RATE = 6
        const val RESOURCE_IRON_INGOT_RATE = 12
        const val RESOURCE_GOLD_INGOT_RATE = 20
        const val RESOURCE_DIAMOND_RATE = 40
        const val RESOURCE_OAK_LOG_RATE = 4
        const val RESOURCE_DIRT_RATE = 1

        fun defaultOrderLevels(): List<OrderLevelConfig> =
            listOf(
                OrderLevelConfig(ORDER_LEVEL_1, ORDER_LEVEL_1_RADIUS, ORDER_LEVEL_1_COST),
                OrderLevelConfig(ORDER_LEVEL_2, ORDER_LEVEL_2_RADIUS, ORDER_LEVEL_2_COST),
                OrderLevelConfig(ORDER_LEVEL_3, ORDER_LEVEL_3_RADIUS, ORDER_LEVEL_3_COST),
                OrderLevelConfig(ORDER_LEVEL_4, ORDER_LEVEL_4_RADIUS, ORDER_LEVEL_4_COST),
                OrderLevelConfig(ORDER_LEVEL_5, ORDER_LEVEL_5_RADIUS, ORDER_LEVEL_5_COST),
            )

        fun defaultResourceRates(): Map<String, Int> =
            mapOf(
                "COBBLESTONE" to RESOURCE_COBBLESTONE_RATE,
                "COAL" to RESOURCE_COAL_RATE,
                "IRON_INGOT" to RESOURCE_IRON_INGOT_RATE,
                "GOLD_INGOT" to RESOURCE_GOLD_INGOT_RATE,
                "DIAMOND" to RESOURCE_DIAMOND_RATE,
                "OAK_LOG" to RESOURCE_OAK_LOG_RATE,
                "DIRT" to RESOURCE_DIRT_RATE,
            )
    }
}
