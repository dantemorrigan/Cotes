package io.github.dantemorrigan.cotes

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Markdown в редакторе: продолжение списков, чекбоксы по нажатию, подсветка не меняет текст. */
@RunWith(AndroidJUnit4::class)
class MarkdownUiTest : CotesTest() {

    @Test
    fun enter_continuesList_andCheckboxToggles() {
        seed("Покупки" to "- [ ] хлеб")
        launch()
        row("Покупки").performClick()
        val field = compose.onNodeWithTag("content")
        field.performTextInputSelection(TextRange(10))
        field.performTextInput("\n")
        waitSaved()
        assertEquals("- [ ] хлеб\n- [ ] ", repo.notes.value.single().content)
        field.performTextInput("молоко")
        waitSaved()

        // Нажатие в «[ ]» первого пункта отмечает его.
        field.performTextInputSelection(TextRange(3))
        compose.waitUntil(5_000) { repo.notes.value.single().content.startsWith("- [x] хлеб") }
        assertEquals("- [x] хлеб\n- [ ] молоко", repo.notes.value.single().content)
    }

    @Test
    fun richNote_looksRight_andStaysUnchanged() {
        val text = "# Планы на неделю\n\n**Важно:** позвонить *маме*\n\n- [x] купить ==билеты==\n- [ ] собрать вещи\n" +
            "1. Москва\n2. Казань\n\n> [!note] Взять зарядку\n\nСм. [[Маршрут]] и https://example.com\n\n`код` ~~отменено~~"
        seed("" to text)
        launch()
        shot("42-markdown-list")
        row("Планы на неделю").performClick()
        compose.onNodeWithTag("content").assertExists()
        shot("43-markdown-editor")
        assertEquals(text, repo.notes.value.single().content)
    }
}
