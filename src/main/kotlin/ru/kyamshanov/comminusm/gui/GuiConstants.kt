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

    // Color codes for text (deprecated, prefer Component API)
    const val COLOR_RED = "§c"
    const val COLOR_YELLOW = "§e"
    const val COLOR_GRAY = "§7"
    const val COLOR_WHITE = "§f"
    const val COLOR_GOLD = "§6"

    // Common text patterns
    const val BACK_TEXT = "§cНазад"
    const val ADMIN_PANEL_TITLE = "§cАдмин-панель"
    const val DELETE_WARNING = "§7Внимание: это действие необратимо!"
    const val DELETE_ORDERS_TEXT = "§cУдалить все Ордера"
    const val DELETE_FRONTS_TEXT = "§cУдалить все Фронты"
    const val STATS_TEXT = "§eСтатистика мира"
    const val ORDERS_DELETED_TEXT = "§c☭ Все Ордера в мире удалены."
    const val FRONTS_DELETED_TEXT = "§c☭ Все Фронты в мире удалены."
    const val STATS_PREFIX = "§e☭ Статистика мира "
    const val ORDERS_PREFIX = "§7  Ордеров: §e"
    const val FRONTS_PREFIX = "§7  Фронтов: §e"
}
