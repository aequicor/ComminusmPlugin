@file:Suppress("ReturnCount", "CyclomaticComplexMethod", "ComplexCondition", "MaxLineLength")

package ru.kyamshanov.comminusm.listener

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent

class FlagItemProtectionListener : Listener {
    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val stack = event.itemDrop.itemStack
        if (isOrderFlag(stack) || isFrontFlag(stack)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val whoClicked = event.whoClicked
        val view = event.view
        val current = event.currentItem
        val cursor = event.cursor

        if (event.clickedInventory != null && event.clickedInventory != whoClicked.inventory) {
            if (isOrderFlag(cursor) || isFrontFlag(cursor)) {
                event.isCancelled = true
                return
            }
        }

        if (event.isShiftClick && (isOrderFlag(current) || isFrontFlag(current))) {
            if (event.clickedInventory == whoClicked.inventory && view.topInventory != whoClicked.inventory) {
                event.isCancelled = true
                return
            }
        }

        if (event.action == InventoryAction.HOTBAR_SWAP || event.action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (view.topInventory != whoClicked.inventory) {
                val hotbarItem = (whoClicked as? Player)?.inventory?.getItem(event.hotbarButton)
                if (isOrderFlag(current) || isFrontFlag(current) || isOrderFlag(hotbarItem) || isFrontFlag(hotbarItem)) {
                    event.isCancelled = true
                    return
                }
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val view = event.view
        if (view.topInventory == event.whoClicked.inventory) return
        val topSize = view.topInventory.size
        val draggedToExternal = event.rawSlots.any { it < topSize }
        if (draggedToExternal && (isOrderFlag(event.oldCursor) || isFrontFlag(event.oldCursor))) {
            event.isCancelled = true
            return
        }
        if (event.newItems.any { (slot, item) -> slot < topSize && (isOrderFlag(item) || isFrontFlag(item)) }) {
            event.isCancelled = true
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
        fun hasOrderFlagInInventory(player: org.bukkit.entity.Player): Boolean =
            player.inventory.contents
                .any { isOrderFlag(it) }

        @JvmStatic
        fun hasFrontFlagInInventory(player: org.bukkit.entity.Player): Boolean =
            player.inventory.contents
                .any { isFrontFlag(it) }

        @JvmStatic
        fun removeAllOrderFlags(player: org.bukkit.entity.Player) {
            val inv = player.inventory
            for (i in 0 until inv.size) {
                val item = inv.getItem(i)
                if (isOrderFlag(item)) {
                    inv.clear(i)
                }
            }
        }
    }
}
