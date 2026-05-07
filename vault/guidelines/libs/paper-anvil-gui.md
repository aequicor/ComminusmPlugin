---
genre: guidelines
module: libs
title: Paper AnvilInventory API (1.21)
topic: paper-anvil-gui
---

# Paper AnvilInventory API

## Открыть виртуальный Anvil GUI

```kotlin
val anvilInv = Bukkit.createInventory(null, InventoryType.ANVIL, Component.text("Заголовок"))
// Предзаполнить поле ввода: поставить предмет в слот 0 с именем = текущее значение
val item = ItemStack(Material.PAPER).apply {
    itemMeta = itemMeta?.also { meta ->
        meta.displayName(Component.text(currentValue))
    }
}
anvilInv.setFirstItem(item)
player.openInventory(anvilInv)
```

## PrepareAnvilEvent — разрешить вывод

```kotlin
@EventHandler
fun onPrepare(event: PrepareAnvilEvent) {
    val text = event.inventory.renameText ?: return
    val result = ItemStack(Material.PAPER).apply {
        itemMeta = itemMeta?.also { meta ->
            meta.displayName(Component.text(text))
        }
    }
    event.result = result
}
```

## InventoryClickEvent — получить результат (слот 2)

```kotlin
@EventHandler
fun onClick(event: InventoryClickEvent) {
    if (event.inventory.type != InventoryType.ANVIL) return
    if (event.rawSlot != 2) return                       // только выходной слот
    val typedText = event.inventory.renameText ?: ""     // введённый текст
}
```

## InventoryCloseEvent — закрытие Anvil

```kotlin
@EventHandler
fun onClose(event: InventoryCloseEvent) {
    if (event.inventory.type != InventoryType.ANVIL) return
    // cleanup
}
```

## Примечания
- `getRenameText()` на `AnvilInventory` устарел. Использовать `.renameText` (Kotlin property)
- `setFirstItem()` — устаревший метод, но рабочий; альтернатива: `inventory.setItem(0, item)`
- Слоты Anvil: 0 = левый вход, 1 = правый вход, 2 = выход
