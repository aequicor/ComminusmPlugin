package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object GuiUtils {
    val BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE

    fun borderItem(): ItemStack {
        val item = ItemStack(BORDER_MATERIAL)
        val meta = item.itemMeta
        meta.displayName(Component.text(" "))
        item.itemMeta = meta
        return item
    }

    fun fillBorder(inv: org.bukkit.inventory.Inventory) {
        for (i in 0..8) inv.setItem(i, borderItem())
        for (i in 36..44) inv.setItem(i, borderItem())
        for (i in intArrayOf(9, 17, 18, 26, 27, 35)) inv.setItem(i, borderItem())
    }

    fun namedItem(name: String, material: Material, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { Component.text(it) })
        }
        item.itemMeta = meta
        return item
    }
}
