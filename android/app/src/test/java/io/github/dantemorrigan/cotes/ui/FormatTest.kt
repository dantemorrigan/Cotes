package io.github.dantemorrigan.cotes.ui

import androidx.compose.ui.text.SpanStyle
import io.github.dantemorrigan.cotes.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    private fun note(title: String, content: String) = Note(id = "n", title = title, content = content, created = 1)

    @Test
    fun wordCount_countsRunsOfNonSpace() {
        assertEquals(0, wordCount("  \n "))
        assertEquals(3, wordCount("Молоко\nХлеб  Кофе"))
    }

    @Test
    fun matches_isCaseInsensitive_inTitleAndText() {
        val n = note("Идеи", "Рассказ про КОФЕ")
        assertTrue(n.matches(""))
        assertTrue(n.matches("кофе"))
        assertTrue(n.matches("идеи"))
        assertFalse(n.matches("чай"))
    }

    @Test
    fun preview_skipsFirstLineWhenItIsTheTitle_andCentersOnMatch() {
        assertEquals("вторая третья", note("", "первая\nвторая\n третья").preview(""))
        assertEquals("первая вторая", note("Заголовок", "первая\nвторая").preview(""))
        val long = "x ".repeat(60) + "найди меня"
        val p = note("t", long).preview("найди")
        assertTrue(p.startsWith("…"))
        assertTrue(p.contains("найди меня"))
    }

    @Test
    fun highlight_marksEveryMatch() {
        val text = highlight("кофе и ещё Кофе", "кофе", SpanStyle())
        assertEquals("кофе и ещё Кофе", text.text)
        assertEquals(2, text.spanStyles.size)
        assertEquals(0, highlight("без совпадений", "", SpanStyle()).spanStyles.size)
    }
}
