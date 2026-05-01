# ManifestoBook Spec

> Feature: Виртуальная книга с коммунистическим манифестом при входе на сервер
> Status: Draft
> Date: 2026-05-01

## Overview

При входе игрока на сервер открывается GUI книги с адаптированным под Minecraft
текстом «Манифеста Коммунистической партии» (Маркс, Энгельс, 1848). После закрытия
книги предмет **не остаётся** в инвентаре игрока — используется метод
`player.openBook()`, который открывает GUI книги без помещения ItemStack в инвентарь.

## Architecture

```
ru/kyamshanov/comminusm/
├── book/
│   └── ManifestoBook.kt          # NEW — создание книги с манифестом
├── event/
│   └── PlayerJoinHandler.kt      # REWRITE — открытие книги на PlayerJoinEvent
└── plugin/
    └── ComminusmPlugin.kt        # регистрация (без изменений)
```

### ManifestoBook (`book/ManifestoBook.kt`)

**Ответственность:** создание `ItemStack(Material.WRITTEN_BOOK)` с заполненными
страницами манифеста. ItemStack существует только в памяти как контейнер для
контента, никогда не попадает в инвентарь.

```kotlin
object ManifestoBook {
    fun create(): ItemStack
}
```

- Автор: `Карл Маркс и Фридрих Энгельс`
- Название: `Манифест Коммунистической партии`
- Содержание: сжатый пересказ манифеста, адаптированный под Minecraft (10-15 страниц)
- Используются цветовые коды Bukkit (`§a`, `§c`, `§e`, `§l`, `§n`, `§o`, `§r`)
- Страницы — `List<String>` с переносами строк `\n`

### PlayerJoinHandler (`event/PlayerJoinHandler.kt`)

**Ответственность:** обработка `PlayerJoinEvent` — открытие книги при входе.

```kotlin
class PlayerJoinHandler : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent)
}
```

- На `PlayerJoinEvent` вызывается `player.openBook(ManifestoBook.create())`
- Join-сообщение сохраняется из текущей реализации
- Никаких ItemStack в инвентарь не кладётся
- Никаких трекеров/таймеров/методов удаления не требуется

### Что удаляется из текущей реализации

Из `PlayerJoinHandler.kt` удаляются:
- `startBookTracker()` и запущенный `BukkitRunnable`
- `hasBook()`, `removeBook()`
- `giveBookWithFallback()` — замена слотов не нужна
- `onPlayerQuit()`, `onPlayerKick()`, `onInventoryClick()` — удаление/трекинг книги больше не нужны
- `CUSTOM_MODEL_DATA`, `CHECK_INTERVAL`, `FIRST_SLOT` константы
- `lazy` поле `bookItem` (создание книги выносится в `ManifestoBook`)

## Текст манифеста

Адаптированный пересказ на русском языке, ключевые цитаты сохранены в оригинале.
Примерная структура:

| Стр. | Содержание |
|------|------------|
| 1 | Заглавная + «Призрак бродит по Европе — призрак коммунизма» |
| 2-3 | Часть I: Буржуа и пролетарии — история борьбы классов |
| 4-5 | Продолжение: кризисы капитализма, отчуждение |
| 6-7 | Часть II: Пролетарии и коммунисты — отмена частной собственности |
| 8-9 | Продолжение: трудовая повинность, централизация |
| 10-11 | Часть IV: Финальный призыв «Пролетарии всех стран, соединяйтесь!» |

Точный текст и количество страниц определяются при реализации с учётом
ограничения ~256 символов на страницу Minecraft-книги.

## Data Flow

```
PlayerJoinEvent
    │
    ▼
PlayerJoinHandler.onPlayerJoin()
    │
    ├── event.joinMessage(...)
    │
    └── player.openBook( ManifestoBook.create() )
              │
              ▼
         Paper API internal:
         └── ClientboundOpenBook packet ──► client opens book GUI
         └── NO ItemStack in player's inventory
```

ItemStack из `ManifestoBook.create()` живёт только в куче JVM в рамках вызова
метода. После закрытия книги игроком предмета в инвентаре нет.

## Error Handling

- `ManifestoBook.create()` не может упасть — все данные статические
- Если игрок не онлайн (проверка внутри метода `openBook` уже есть в Paper API),
  метод просто ничего не делает

## Testing

- Unit-тест: `ManifestoBook.create()` возвращает `ItemStack` типа `WRITTEN_BOOK`,
  мета-данные содержат правильного автора и непустые страницы
- Интеграционный тест (ручной): зайти на сервер — книга открывается,
  закрыть книгу — предмета в инвентаре нет

## Compliance

- Без `!!` — проверки через `requireNotNull` с сообщением
- Без GlobalScope/корутин — нет асинхронного кода
- Без runBlocking/Thread.sleep
- Без `TODO()` и `FIXME`
