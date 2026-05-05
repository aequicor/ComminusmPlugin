package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlockListenerTest {

    /**
     * Demonstrates the bug: Component.contains(Component) is style-sensitive.
     * A gold-colored "Флаг Ордера" does NOT "contain" a plain "Флаг Ордера"
     * because the styles differ (gold vs default/white).
     */
    @Test
    fun `component contains is style sensitive — plain text check is NOT`() {
        // The banner's custom name as stored by the plugin (with color formatting)
        val styledName = Component.text("Флаг Ордера", NamedTextColor.GOLD)

        // The check currently in the code (plain, no color)
        val plainCheck = Component.text("Флаг Ордера")

        // BUG: style-sensitive contains returns false for styled vs plain
        assertFalse(
            styledName.contains(plainCheck),
            "BUG: Component.contains(Component) is style-sensitive — " +
                "gold 'Флаг Ордера'.contains(white 'Флаг Ордера') = false"
        )

        // FIX: PlainTextComponentSerializer strips formatting, comparison works
        val plainText = PlainTextComponentSerializer.plainText().serialize(styledName)
        assertTrue(
            plainText.contains("Флаг Ордера"),
            "FIX: plain-text serialization strips color, 'Флаг Ордера'.contains('Флаг Ордера') = true"
        )
    }

    @Test
    fun `plain text comparison works for both order and front flag names`() {
        val goldOrderFlag = Component.text("Флаг Ордера", NamedTextColor.GOLD)
        val redFrontFlag = Component.text("Флаг Трудового Фронта", NamedTextColor.RED)
        val goldFrontFlag = Component.text("Флаг Трудового Фронта", NamedTextColor.GOLD)

        val serializer = PlainTextComponentSerializer.plainText()

        // Order flag — any color
        assertEquals("Флаг Ордера", serializer.serialize(goldOrderFlag))
        assertTrue(serializer.serialize(goldOrderFlag).contains("Флаг Ордера"))
        assertTrue(serializer.serialize(goldOrderFlag).contains("Ордера"))

        // Front flag — red
        assertEquals("Флаг Трудового Фронта", serializer.serialize(redFrontFlag))
        assertTrue(serializer.serialize(redFrontFlag).contains("Флаг Трудового Фронта"))
        assertTrue(serializer.serialize(redFrontFlag).contains("Трудового Фронта"))

        // Front flag — gold
        assertEquals("Флаг Трудового Фронта", serializer.serialize(goldFrontFlag))
        assertTrue(serializer.serialize(goldFrontFlag).contains("Флаг Трудового Фронта"))
    }

    @Test
    fun `plain text comparison works with section sign formatted text`() {
        // Some code paths may use legacy § formatting
        val legacyStyled = Component.text("§6Флаг Ордера")
        val plainText = PlainTextComponentSerializer.plainText().serialize(legacyStyled)

        // §6 is a color code, not stripped by serializer in component text (it's literal)
        // but the key point: contains still finds the text
        assertTrue(
            plainText.contains("Флаг Ордера"),
            "Even with §6 prefix, 'Флаг Ордера' substring exists in the serialized text"
        )
    }
}
