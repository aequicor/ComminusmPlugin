# ManifestoBook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current book-on-join mechanism with a virtual book that opens via `player.openBook()` without placing any item in the player's inventory.

**Architecture:** New `ManifestoBook` object in `book/` package creates an in-memory `ItemStack` with adapted Communist Manifesto text. `PlayerJoinHandler` is simplified to call `player.openBook(ManifestoBook.create())` — no inventory placement, no trackers, no cleanup handlers.

**Tech Stack:** Kotlin, Paper API 1.21.11, Kyori Adventure Components, JUnit 5, MockK

**Spec:** `docs/plugin/spec/manifesto-book-spec.md`

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Create | `src/main/kotlin/ru/kyamshanov/comminusm/book/ManifestoBook.kt` | Book content factory |
| Rewrite | `src/main/kotlin/ru/kyamshanov/comminusm/event/PlayerJoinHandler.kt` | Join event handler |
| Create | `src/test/kotlin/ru/kyamshanov/comminusm/book/ManifestoBookTest.kt` | Tests for ManifestoBook |

---

### Task 1: Create ManifestoBook

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/book/ManifestoBook.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/book/ManifestoBookTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/ru/kyamshanov/comminusm/book/ManifestoBookTest.kt`:

```kotlin
package ru.kyamshanov.comminusm.book

import org.bukkit.Material
import org.bukkit.inventory.meta.BookMeta
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ManifestoBookTest {

    @Test
    fun `create returns ItemStack of type WRITTEN_BOOK`() {
        val book = ManifestoBook.create()

        assertEquals(Material.WRITTEN_BOOK, book.type)
    }

    @Test
    fun `create returns book with correct author`() {
        val book = ManifestoBook.create()
        val meta = book.itemMeta as BookMeta

        assertTrue(meta.author()?.contains("Маркс") == true)
        assertTrue(meta.author()?.contains("Энгельс") == true)
    }

    @Test
    fun `create returns book with correct title`() {
        val book = ManifestoBook.create()
        val meta = book.itemMeta as BookMeta

        assertTrue(meta.title()?.contains("Манифест") == true)
    }

    @Test
    fun `create returns book with non-empty pages`() {
        val book = ManifestoBook.create()
        val meta = book.itemMeta as BookMeta

        val pages = meta.pages()
        assertNotNull(pages)
        assertTrue(pages.size >= 1)
        pages.forEach { page ->
            assertNotNull(page)
            assertTrue(page.length > 0, "Each page must have content")
        }
    }

    @Test
    fun `create returns book with page count between 10 and 15`() {
        val book = ManifestoBook.create()
        val meta = book.itemMeta as BookMeta

        val pages = meta.pages()
        assertTrue(pages.size in 10..15, "Expected 10-15 pages but got ${pages.size}")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.book.ManifestoBookTest"`
Expected: FAIL — compilation error "Unresolved reference: ManifestoBook"

- [ ] **Step 3: Write minimal implementation — ManifestoBook skeleton**

Create `src/main/kotlin/ru/kyamshanov/comminusm/book/ManifestoBook.kt`:

```kotlin
package ru.kyamshanov.comminusm.book

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta

object ManifestoBook {

    fun create(): ItemStack {
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta: BookMeta = book.itemMeta as BookMeta

        meta.author(Component.text("Карл Маркс и Фридрих Энгельс"))
        meta.title(Component.text("Манифест Коммунистической партии"))

        meta.setPages(pages)

        book.itemMeta = meta
        return book
    }

    private val pages: List<String> = listOf(
        "§l§nМанифест Коммунистической\nпартии§r\n\n" +
            "§oКарл Маркс и Фридрих Энгельс\n1848 г.§r\n\n" +
            "Призрак бродит по Европе —\nпризрак коммунизма.\n" +
            "Все силы старой Европы\nобъединились для священной\nтравли этого призрака...",

        "§lI. Буржуа и пролетарии§r\n\n" +
            "История всех существовавших\nобществ была историей\nборьбы классов.\n\n" +
            "Свободный и раб, патриций и\nплебей, помещик и крепостной —\nвсегда вели непрерывную борьбу.",

        "Буржуазное общество не\nуничтожило классовых\nпротиворечий. Оно только\nпоставило новые классы на\nместо старых.\n\n" +
            "Общество раскалывается на\nдва лагеря: §cбуржуазию§r и\n§aпролетариат§r.",

        "Буржуазия сыграла в истории\nреволюционную роль.\n\n" +
            "Она разрушила все феодальные\nи патриархальные отношения.\n" +
            "Не оставила между людьми\nникакой связи, кроме §cголого\nинтереса§r и бессердечного\n§c«чистогана»§r.",

        "Буржуазия не может\nсуществовать без постоянных\nпереворотов в производстве.\n\n" +
            "Все сословное и застойное\nисчезает. Люди вынуждены\nтрезво взглянуть на свои\nжизненные условия.",

        "Потребность в сбыте гонит\nбуржуазию по всему миру.\nОна сделала производство и\nпотребление всех стран\n§eкосмополитическим§r.\n\n" +
            "Дешёвые цены — её тяжёлая\nартиллерия, разрушающая все\nкитайские стены.",

        "Оружие, которым буржуазия\nниспровергла феодализм,\nнаправляется теперь против\nнеё самой.\n\n" +
            "Она породила людей, которые\nнаправят это оружие —\n§aсовременных рабочих,\nпролетариев§r.",

        "Рабочий становится простым\nпридатком машины.\nЧем больше растёт\nнепривлекательность труда,\nтем меньше заработная плата.\n\n" +
            "Мужской труд вытесняется\nженским и детским.\nРазличия пола и возраста\nтеряют значение.",

        "§lII. Пролетарии и коммунисты§r\n\n" +
            "Коммунисты не являются\nособой партией,\nпротивостоящей другим\nрабочим партиям.\n\n" +
            "У них нет интересов,\nотдельных от интересов\nпролетариата в целом.",

        "Отличительная черта\nкоммунизма: отмена\nчастной собственности.\n\n" +
            "Капитал — это коллективный\nпродукт и может быть приведён\nв движение лишь совместной\nдеятельностью всех членов\nобщества.",

        "В буржуазном обществе труд\nлишь увеличивает капитал.\nВ коммунистическом — служит\nобогащению самого рабочего.\n\n" +
            "На смену старому обществу с\nего классами придёт\n§eассоциация, где свободное\nразвитие каждого — условие\nсвободного развития всех§r.",

        "§lIV. Отношение коммунистов к\nпрочим оппозиционным партиям§r\n\n" +
            "Коммунисты борются во имя\nближайших целей и интересов\nрабочего класса.\n\n" +
            "Они поддерживают всякое\nреволюционное движение\nпротив существующего строя.",

        "Коммунисты повсюду\nдобиваются объединения\nдемократических партий\nвсех стран.\n\n" +
            "Они открыто заявляют, что\nих цели могут быть достигнуты\nлишь насильственным\nниспровержением всего\nсуществующего строя.",

        "Пусть господствующие классы\nсодрогаются перед\nКоммунистической Революцией.\n\n" +
            "Пролетариям нечего терять,\nкроме своих цепей.\nПриобретут же они весь мир.\n\n" +
            "§c§lПРОЛЕТАРИИ ВСЕХ СТРАН,\nСОЕДИНЯЙТЕСЬ!§r"
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.book.ManifestoBookTest"`
Expected: all 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/book/ManifestoBook.kt src/test/kotlin/ru/kyamshanov/comminusm/book/ManifestoBookTest.kt
git commit -m "feat: add ManifestoBook with adapted Communist Manifesto text"
```

---

### Task 2: Rewrite PlayerJoinHandler

**Files:**
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/event/PlayerJoinHandler.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt` (import update only)

- [ ] **Step 1: Rewrite PlayerJoinHandler**

Replace the entire content of `src/main/kotlin/ru/kyamshanov/comminusm/event/PlayerJoinHandler.kt`:

```kotlin
package ru.kyamshanov.comminusm.event

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.kyamshanov.comminusm.book.ManifestoBook

class PlayerJoinHandler : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        player.openBook(ManifestoBook.create())

        event.joinMessage(
            Component.text("§e[ВХОД] §a${event.player.name}§e явился на собрание трудового коллектива!")
        )
    }
}
```

- [ ] **Step 2: Verify ComminusmPlugin imports are correct**

Read `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt`. The existing import `import ru.kyamshanov.comminusm.event.PlayerJoinHandler` is still valid — no change needed.

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `./gradlew :plugin:test`
Expected: all tests PASS

- [ ] **Step 5: Run detekt**

Run: `./gradlew detekt`
Expected: no violations (or existing violations unchanged)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/event/PlayerJoinHandler.kt
git commit -m "refactor: simplify PlayerJoinHandler to use virtual book via openBook()"
```

---

## Definition of Done Checklist

- [ ] `./gradlew compileKotlin` — BUILD SUCCESSFUL
- [ ] `./gradlew :plugin:test` — all tests pass
- [ ] `./gradlew detekt` — no new violations
- [ ] No `TODO` or `FIXME` in code
- [ ] Book opens on join, no item remains in inventory
- [ ] All old tracker/cleanup code removed from PlayerJoinHandler
