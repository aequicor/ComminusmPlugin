package ru.kyamshanov.comminusm.config

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin

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

    val flagSupportBlockMaterial: Material
        get() {
            val name = config.getString("flag.supportBlockMaterial", "BEDROCK") ?: "BEDROCK"
            return runCatching { Material.valueOf(name) }
                .getOrElse {
                    ComminusmPlugin.getInstance().logger.severe(
                        "flag.supportBlockMaterial '$name' is invalid — falling back to BEDROCK"
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
                    "flag.maxPerChunk must be ≥ 1, got $v — using default $DEFAULT_FLAG_MAX_PER_CHUNK"
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
                    "flag.allowedWorlds is empty — flag placement is disabled in all worlds"
                )
            }
            return list.toSet()
        }

    val flagStartupScanBatchSize: Int
        get() = config.getInt("flag.startupScanBatchSize", DEFAULT_FLAG_STARTUP_SCAN_BATCH_SIZE)
            .coerceAtLeast(MIN_FLAG_STARTUP_SCAN_BATCH_SIZE)

    companion object {
        const val DEFAULT_FLAG_MIN_AIR_ABOVE = 2
        const val MIN_FLAG_AIR_ABOVE = 1
        const val DEFAULT_FLAG_MAX_PER_CHUNK = 50
        const val DEFAULT_FLAG_STARTUP_SCAN_BATCH_SIZE = 10
        const val MIN_FLAG_STARTUP_SCAN_BATCH_SIZE = 1

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
