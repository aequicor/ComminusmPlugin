package ru.kyamshanov.comminusm.listener

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent

class FlagItemProtectionListener : Listener {

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val stack = event.itemDrop.itemStack
        if (FlagItemProtectionListener.isOrderFlag(stack) || FlagItemProtectionListener.isFrontFlag(stack)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val current = event.currentItem ?: return
        val cursor = event.cursor

        if (event.clickedInventory != null && event.clickedInventory != event.whoClicked.inventory) {
            if (FlagItemProtectionListener.isOrderFlag(cursor) || FlagItemProtectionListener.isFrontFlag(cursor)) {
                event.isCancelled = true
                return
            }
        }

        if (event.isShiftClick) {
            if (FlagItemProtectionListener.isOrderFlag(current) || FlagItemProtectionListener.isFrontFlag(current)) {
                if (event.clickedInventory != event.whoClicked.inventory) {
                    event.isCancelled = true
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun isOrderFlag(item: org.bukkit.inventory.ItemStack?): Boolean {
            if (item == null || item.type != Material.WHITE_BANNER) return false
            val meta = item.itemMeta ?: return false
            return meta.displayName().toString().contains("Флаг Ордера")
        }

        @JvmStatic
        fun isFrontFlag(item: org.bukkit.inventory.ItemStack?): Boolean {
            if (item == null || item.type != Material.RED_BANNER) return false
            val meta = item.itemMeta ?: return false
            return meta.displayName().toString().contains("Флаг Трудового Фронта")
        }

        @JvmStatic
        fun hasOrderFlagInInventory(player: org.bukkit.entity.Player): Boolean {
            return player.inventory.contents.any { isOrderFlag(it) }
        }

        @JvmStatic
        fun hasFrontFlagInInventory(player: org.bukkit.entity.Player): Boolean {
            return player.inventory.contents.any { isFrontFlag(it) }
        }
    }
}
