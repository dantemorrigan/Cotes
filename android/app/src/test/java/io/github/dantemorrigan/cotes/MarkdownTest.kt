package io.github.dantemorrigan.cotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTest {
    /** Ввод Enter в позиции [at] текста [before]. */
    private fun enter(before: String, at: Int = before.length) =
        Markdown.continueList(before, before.substring(0, at) + "\n" + before.substring(at), at + 1)

    @Test
    fun enter_continuesBulletNumberAndTask() {
        assertEquals("- молоко\n- " to 11, enter("- молоко"))
        assertEquals("  * a\n  * " to 10, enter("  * a"))
        assertEquals("1. раз\n2. " to 10, enter("1. раз"))
        assertEquals("9) x\n10) " to 9, enter("9) x"))
        // Новый пункт списка дел — всегда неотмеченный.
        assertEquals("- [x] хлеб\n- [ ] " to 17, enter("- [x] хлеб"))
    }

    @Test
    fun enter_onEmptyItem_endsList() {
        assertEquals("- a\n" to 4, enter("- a\n- "))
        assertEquals("- [ ] a\n" to 8, enter("- [ ] a\n- [ ] "))
    }

    @Test
    fun enter_outsideList_orNotAnEnter_isIgnored() {
        assertNull(enter("просто текст"))
        assertNull(enter("#тег"))
        assertNull(Markdown.continueList("- a", "- ab", 4))
        // Вставили сразу несколько строк — не вмешиваемся.
        assertNull(Markdown.continueList("- a", "- a\nb\nc", 8))
    }

    @Test
    fun checkbox_tapInsideBrackets_toggles() {
        val text = "Покупки\n- [ ] хлеб\n- [x] молоко"
        val first = text.indexOf("[ ]") + 1
        assertEquals(first, Markdown.checkboxAt(text, first))
        assertEquals(first, Markdown.checkboxAt(text, first + 1))
        assertNull(Markdown.checkboxAt(text, first - 1))
        assertNull(Markdown.checkboxAt(text, first + 4))
        assertEquals("Покупки\n- [x] хлеб\n- [x] молоко", Markdown.toggleCheckbox(text, first))
        val second = text.indexOf("[x]") + 1
        assertEquals("Покупки\n- [ ] хлеб\n- [ ] молоко", Markdown.toggleCheckbox(text, second))
    }

    @Test
    fun linkAt_findsUrlsAndMarkdownLinks() {
        val text = "сайт https://example.com/a и [доки](https://d.example/x) конец"
        assertEquals("https://example.com/a", Markdown.linkAt(text, text.indexOf("example")))
        assertEquals("https://d.example/x", Markdown.linkAt(text, text.indexOf("доки")))
        assertNull(Markdown.linkAt(text, text.indexOf("конец")))
    }

    @Test
    fun plain_stripsMarkupForPreviews() {
        assertEquals("Покупки", Markdown.plainLine("## Покупки"))
        assertEquals("☐ хлеб", Markdown.plainLine("- [ ] хлеб"))
        assertEquals("☑ молоко", Markdown.plainLine("- [x] молоко"))
        assertEquals("• пункт", Markdown.plainLine("- пункт"))
        assertEquals("Важно: жирный и курсив", Markdown.plainLine("> [!note] Важно: **жирный** и *курсив*"))
        assertEquals("см. Заметка и сайт", Markdown.plainLine("см. [[Заметка]] и [сайт](https://x.ru)"))
        assertEquals("псевдоним", Markdown.plainLine("[[Длинное имя|псевдоним]]"))
        assertEquals("код и метка", Markdown.plainLine("`код` и ==метка=="))
        assertEquals("слово_с_подчёркиванием", Markdown.plainLine("слово_с_подчёркиванием"))
        assertEquals("", Markdown.plainLine("---"))
        assertEquals("Заголовок\nтекст", Markdown.plain("# Заголовок\n\n```\ntext\n```".replace("text", "текст")))
    }

    @Test
    fun displayTitle_skipsHeadingMarks() {
        assertEquals("Покупки", Note(id = "1", content = "# Покупки\n- хлеб", created = 1).displayTitle)
    }

    // --- Панель форматирования ---

    private fun at(text: String, sel: String): Pair<Int, Int> = text.indexOf(sel).let { it to it + sel.length }

    @Test
    fun wrap_boldItalicStrike_andUnwrap() {
        val t = "купить хлеб"
        val (s, e) = at(t, "хлеб")
        val bold = Markdown.wrap(t, s, e, "**")
        assertEquals(Markdown.Edit("купить **хлеб**", 9, 13), bold)
        // Повторное нажатие снимает.
        assertEquals(Markdown.Edit(t, 7, 11), Markdown.wrap(bold.text, bold.start, bold.end, "**"))
        // Выделили вместе со значками — тоже снимает.
        assertEquals(Markdown.Edit(t, 7, 11), Markdown.wrap(bold.text, 7, 15, "**"))
        // Курсив внутри жирного не ломает жирный.
        assertEquals("купить ***хлеб***", Markdown.wrap(bold.text, bold.start, bold.end, "*").text)
        // Пробелы по краям выделения остаются снаружи.
        assertEquals("купить **хлеб** ", Markdown.wrap("купить хлеб ", 6, 12, "**").text)
    }

    @Test
    fun wrap_withoutSelection_putsCursorBetween() {
        assertEquals(Markdown.Edit("а ~~~~", 4, 4), Markdown.wrap("а ", 2, 2, "~~"))
    }

    @Test
    fun link_wrapsSelection_andSelectsUrl() {
        val e = Markdown.link("см. сайт", 4, 8)
        assertEquals("см. [сайт](https://)", e.text)
        assertEquals("https://", e.text.substring(e.start, e.end))
        assertEquals(Markdown.Edit("[](https://)", 1, 1), Markdown.link("", 0, 0))
    }

    @Test
    fun heading_cycles() {
        var e = Markdown.heading("Покупки", 3, 3)
        assertEquals("# Покупки", e.text)
        e = Markdown.heading(e.text, e.start, e.end)
        assertEquals("## Покупки", e.text)
        e = Markdown.heading(e.text, e.start, e.end)
        assertEquals("### Покупки", e.text)
        e = Markdown.heading(e.text, e.start, e.end)
        assertEquals("Покупки", e.text)
        // Только строка с курсором.
        assertEquals("раз\n# два\nтри", Markdown.heading("раз\nдва\nтри", 5, 5).text)
    }

    @Test
    fun block_listsTasksQuotes_toggleAndReplace() {
        val t = "молоко\nхлеб"
        val bullets = Markdown.block(t, 0, t.length, Markdown.Block.Bullet)
        assertEquals("- молоко\n- хлеб", bullets.text)
        assertEquals(t, Markdown.block(bullets.text, 0, bullets.text.length, Markdown.Block.Bullet).text)
        assertEquals("1. молоко\n2. хлеб", Markdown.block(t, 0, t.length, Markdown.Block.Numbered).text)
        assertEquals("- [ ] молоко\n- [ ] хлеб", Markdown.block(bullets.text, 0, bullets.text.length, Markdown.Block.Task).text)
        assertEquals("> молоко", Markdown.block("молоко", 2, 2, Markdown.Block.Quote).text)
        // Курсор сдвигается вместе с текстом.
        val e = Markdown.block("молоко", 3, 3, Markdown.Block.Task)
        assertEquals(9, e.start)
        // Пустые строки в многострочном выделении не трогаем.
        assertEquals("- а\n\n- б", Markdown.block("а\n\nб", 0, 4, Markdown.Block.Bullet).text)
    }
}

