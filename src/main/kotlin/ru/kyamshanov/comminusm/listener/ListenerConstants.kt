package ru.kyamshanov.comminusm.listener

/**
 * Constants for listener events and configurations.
 */
object ListenerConstants {
    // Numeric constants for event handling
    const val MIN_INVENTORY_SLOT = -1
    const val FIRST_EMPTY_SLOT = -1
    const val ZERO_MINUTES = 0

    // Flag activation timing (milliseconds)
    const val FLAG_ACTIVATION_DELAY_TICKS = 20L

    // Location comparison tolerance
    const val LOCATION_BLOCK_EPSILON = 0

    // Support block Y-level offset (banner height relative to support)
    const val FLAG_SUPPORT_Y_OFFSET = 1.0

    // Dimension helpers
    const val DIMENSION_X = 0.0
    const val DIMENSION_Y = 1.0
    const val DIMENSION_Z = 0.0
}
