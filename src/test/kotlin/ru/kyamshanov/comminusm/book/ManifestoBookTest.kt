package ru.kyamshanov.comminusm.book

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestoBookTest {

    @Test
    fun `pages list is not empty`() {
        val pages = ManifestoBook.pages

        assertNotNull(pages)
        assertTrue(pages.isNotEmpty())
    }

    @Test
    fun `page count is between 10 and 15`() {
        val pages = ManifestoBook.pages

        assertTrue(pages.size in 10..15, "Expected 10-15 pages but got ${pages.size}")
    }

    @Test
    fun `first page contains opening line`() {
        val pages = ManifestoBook.pages

        assertTrue(pages[0].contains("Призрак бродит по Европе"))
    }

    @Test
    fun `last page contains final slogan`() {
        val pages = ManifestoBook.pages

        assertTrue(pages.last().contains("ПРОЛЕТАРИИ ВСЕХ СТРАН"))
        assertTrue(pages.last().contains("СОЕДИНЯЙТЕСЬ"))
    }

    @Test
    fun `all pages have content`() {
        val pages = ManifestoBook.pages

        pages.forEachIndexed { index, page ->
            assertTrue(page.isNotEmpty(), "Page $index is empty")
        }
    }

    @Test
    fun `pages contain section headers`() {
        val pages = ManifestoBook.pages
        val allText = pages.joinToString("\n")

        assertTrue(allText.contains("Буржуа и пролетарии"))
        assertTrue(allText.contains("Пролетарии и коммунисты"))
        assertTrue(allText.contains("Отношение коммунистов"))
    }

    @Test
    fun `pages contain Marx and Engels reference`() {
        val pages = ManifestoBook.pages

        assertTrue(pages[0].contains("Маркс"))
        assertTrue(pages[0].contains("Энгельс"))
    }
}
