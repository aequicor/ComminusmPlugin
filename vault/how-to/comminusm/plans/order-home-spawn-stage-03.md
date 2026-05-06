---
genre: how-to
module: comminusm
title: "Stage 03 — OrderMenu: кнопка «Вернуться домой»"
topic: order-home-spawn
stage: 3
status: DONE
date: 06.05.2026
related:
  - vault/concepts/comminusm/plans/order-home-spawn-plan.md
  - vault/reference/comminusm/spec/order-home-spawn.md
---

# Stage 03 — OrderMenu: кнопка «Вернуться домой»

## Goal

Добавить кнопку «Вернуться домой» в меню ордера. Кнопка видна только владельцу ордера в том же мире, что и флаг. По клику запускается таймер через `HomeTimerManager`.

## Files to Modify

- `src/main/kotlin/ru/kyamshanov/comminusm/gui/OrderMenu.kt`

## Context

Текущая структура `OrderMenu.kt`:
```
45 слотов (6 рядов):
slot 20 — infoSlot (информация об ордере)
slot 22 — sizeSlot (размер территории)
slot 24 — upgradeSlot (улучшение)
slot 31 — restoreSlot (восстановить флаг)
slot 39 — backSlot (назад)
```

Свободные слоты для кнопки: **slot 4** (верхний центр) или **slot 13** (центр, 2й ряд).
Рекомендуется **slot 4** — хорошо заметен, не конфликтует с существующими.

## Implementation

### 1. Добавить константу слота

```kotlin
private val homeSlot = 4  // «Вернуться домой» — верхний центр
```

### 2. Метод buildHomeButton()

```kotlin
private fun buildHomeButton(
    flagStabilityManager: FlagStabilityManager,
    orderId: Long,
    playerWorld: String,
): ItemStack? {
    val flagLoc = flagStabilityManager.getFlagLocation(orderId) ?: return null
    val flagActive = flagStabilityManager.isFlagActive(orderId)
    if (!flagActive) return null

    return if (flagLoc.world?.name == playerWorld) {
        // AC-01: показать активную кнопку
        GuiUtils.namedItem(
            "<green>Вернуться домой</green>",
            Material.COMPASS,
            "<gray>Нажмите, чтобы начать телепортацию</gray>",
            "<gray>Стойте неподвижно 30 сек.</gray>",
        )
    } else {
        // AC-28: показать кнопку, но неактивную (другой мир)
        GuiUtils.namedItem(
            "<gray>Вернуться домой</gray>",
            Material.COMPASS,
            "<red>Флаг в другом мире — телепорт недоступен</red>",
        ).also { it.amount = 1 }  // disabled state
    }
}
```

### 3. В методе открытия меню (buildInventory / open)

После заполнения существующих слотов:

```kotlin
// Показывать только владельцу (AC-01, AC-02, AC-29)
val order = orderService.findByOwner(player.uniqueId)
if (order != null) {
    val homeButton = buildHomeButton(flagStabilityManager, order.id, player.world.name)
    if (homeButton != null) {
        inv.setItem(homeSlot, homeButton)
        // Сохранить orderId в PDC кнопки для клика без DB-запроса (Q4 из spec)
        val meta = homeButton.itemMeta
        meta?.persistentDataContainer?.set(
            NamespacedKey(plugin, "home_order_id"),
            PersistentDataType.LONG,
            order.id,
        )
        homeButton.itemMeta = meta
        inv.setItem(homeSlot, homeButton)
    }
}
```

### 4. В onClick handler

```kotlin
if (event.rawSlot == homeSlot) {
    event.isCancelled = true
    val player = event.whoClicked as? Player ?: return

    // Валидация: правильный материал кнопки (Q10 из spec — slot identity check)
    val clickedItem = event.currentItem ?: return
    if (clickedItem.type != Material.COMPASS) return

    // Читать orderId из PDC кнопки (не из DB — Q4)
    val orderId = clickedItem.itemMeta
        ?.persistentDataContainer
        ?.get(NamespacedKey(plugin, "home_order_id"), PersistentDataType.LONG)
        ?: return

    // Проверить активность флага и мир ПЕРЕД запуском таймера (AC-28)
    val flagLoc = flagStabilityManager.getFlagLocation(orderId)
    if (flagLoc == null || !flagStabilityManager.isFlagActive(orderId)) {
        player.sendActionBar(mm.deserialize("<red>Флаг недоступен.</red>"))
        return
    }
    if (flagLoc.world?.name != player.world.name) {
        player.sendActionBar(mm.deserialize("<red>Телепортация в другой мир недоступна.</red>"))
        return
    }

    // Запустить таймер (AC-03)
    homeTimerManager.startTimer(player.uniqueId, orderId)  // AC-12: startTimer возвращает false если уже запущен
    player.closeInventory()
}
```

## Important Notes

- **AC-02:** Кнопка не рендерится для не-владельцев (проверка через `orderService.findByOwner`).
- **AC-12:** `homeTimerManager.startTimer()` внутри проверяет наличие активного таймера — повторный клик игнорируется.
- **AC-28:** Если флаг в другом мире — кнопка показывается серой (disabled-вид), при клике — сообщение.
- **PDC хранение orderId в кнопке** — ключевое решение для избегания DB-запроса при click (Q4 из spec).
- **CC-07:** Закрытие inventory (`player.closeInventory()`) выполняется ПОСЛЕ запуска таймера — таймер продолжает работать в фоне (AC-23).

## Tests to Write

| TC | Scenario | Expected |
|----|----------|---------|
| TC-01 | Владелец открывает меню, флаг активен, тот же мир | кнопка HOME видна с Material.COMPASS |
| TC-02 | Не-владелец открывает меню ордера | кнопка HOME отсутствует |
| TC-03 | Владелец нажимает кнопку | startTimer вызван, меню закрыто |
| TC-32 | Флаг в другом мире — кнопка отображена как disabled | серая кнопка, при клике сообщение об ошибке |
| TC-26 | Меню закрыто после нажатия | таймер продолжает работать (не отменяется) |

## Acceptance Criteria Covered

AC-01, AC-02, AC-03, AC-04 (через HomeTimerManager), AC-12, AC-23, AC-28

## Definition of Done

- [ ] `OrderMenu.kt` модифицирован, компилируется
- [ ] Unit-тесты TC-01, TC-02, TC-03, TC-26, TC-32 написаны и проходят
- [ ] `./gradlew compileKotlin` — PASS
- [ ] `./gradlew detekt ktlintCheck` — PASS
