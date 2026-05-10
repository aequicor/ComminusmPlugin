@file:Suppress("MagicNumber")

package ru.kyamshanov.comminusm.gui

/**
 * Constants for GUI inventory slots and configurations.
 */
object GuiConstants {
    // Inventory sizes
    const val SMALL_INVENTORY_SIZE = 27
    const val MEDIUM_INVENTORY_SIZE = 45
    const val LARGE_INVENTORY_SIZE = 54

    // Admin Menu slots
    const val ADMIN_DELETE_ORDERS_SLOT = 11
    const val ADMIN_DELETE_FRONTS_SLOT = 15
    const val ADMIN_STATS_SLOT = 22
    const val ADMIN_BACK_SLOT = 26

    // Common GUI slots
    const val BORDER_SLOT_TOP_LEFT = 0
    const val BORDER_SLOT_TOP_RIGHT = 8
    const val BORDER_SLOT_BOTTOM_LEFT = 18
    const val BORDER_SLOT_BOTTOM_RIGHT = 26

    // Front Menu slots
    const val FRONT_MENU_SLOT_1 = 10
    const val FRONT_MENU_SLOT_2 = 12
    const val FRONT_MENU_SLOT_3 = 14
    const val FRONT_MENU_SLOT_4 = 16
    const val FRONT_MENU_SLOT_5 = 19
    const val FRONT_MENU_BACK_SLOT = 25

    // Order Menu slots
    const val ORDER_MENU_SLOT_1 = 11
    const val ORDER_MENU_SLOT_2 = 13
    const val ORDER_MENU_SLOT_3 = 15
    const val ORDER_MENU_BACK_SLOT = 26

    // Party Menu slots
    const val PARTY_MENU_SLOT_1 = 10
    const val PARTY_MENU_SLOT_2 = 12
    const val PARTY_MENU_SLOT_3 = 14
    const val PARTY_MENU_SLOT_4 = 16
    const val PARTY_MENU_SLOT_5 = 19
    const val PARTY_MENU_BACK_SLOT = 25

    // Treasury Menu slots
    const val TREASURY_MENU_SUBMIT_SLOT = 31
    const val TREASURY_MENU_BACK_SLOT = 39
    const val TREASURY_MENU_INVENTORY_SIZE = 45

    // Party Menu slots (large inventory)
    const val PARTY_MENU_ORDER_SLOT = 20
    const val PARTY_MENU_FRONT_SLOT = 24
    const val PARTY_MENU_TREASURY_SLOT = 31
    const val PARTY_MENU_BALANCE_SLOT = 40
    const val PARTY_MENU_INVENTORY_SIZE = 45

    // Inventory slot range helpers
    const val TREASURY_MENU_INVENTORY_MAX_SLOT = 44

    // Border slot ranges for 45-slot inventories
    val TREASURY_MENU_BORDER_SLOTS =
        setOf(
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            17,
            18,
            26,
            27,
            35,
            36,
            37,
            38,
            39,
            40,
            41,
            42,
            43,
            44,
        )

    // Common numeric constants
    const val INVENTORY_SPLIT_FACTOR = 64
    const val ZERO_AMOUNT = 0

    // Color codes for text (MiniMessage format)
    const val COLOR_RED = "<red>"
    const val COLOR_YELLOW = "<yellow>"
    const val COLOR_GRAY = "<gray>"
    const val COLOR_WHITE = "<white>"
    const val COLOR_GOLD = "<gold>"

    // Common text patterns
    const val BACK_TEXT = "<red>Назад"
    const val ADMIN_PANEL_TITLE = "<red>Админ-панель"
    const val DELETE_WARNING = "<gray>Внимание: это действие необратимо!"
    const val DELETE_ORDERS_TEXT = "<red>Удалить все Ордера"
    const val DELETE_FRONTS_TEXT = "<red>Удалить все Фронты"
    const val STATS_TEXT = "<yellow>Статистика мира"
    const val ORDERS_DELETED_TEXT = "<red>☭ Все Ордера в мире удалены."
    const val FRONTS_DELETED_TEXT = "<red>☭ Все Фронты в мире удалены."
    const val STATS_PREFIX = "<yellow>☭ Статистика мира "
    const val ORDERS_PREFIX = "<gray>  Ордеров: <yellow>"
    const val FRONTS_PREFIX = "<gray>  Фронтов: <yellow>"
}
