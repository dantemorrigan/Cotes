package io.github.dantemorrigan.cotes

import android.content.Intent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Мини-регресс основных сценариев на текущем языке и теме эмулятора. */
@RunWith(AndroidJUnit4::class)
class RegressionTest : CotesTest() {

    @Test
    fun createNote_autosaves_andShowsInList() {
        launch()
        compose.onNodeWithText(s(R.string.empty_title)).assertIsDisplayed()
        shot("01-empty")

        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("title").performTextInput("Список покупок")
        compose.onNodeWithTag("content").performTextInput("Молоко\nХлеб\nКофе")
        compose.onNodeWithTag("counts").assert(hasText(s(R.string.counts, q(R.plurals.words, 3), q(R.plurals.chars, 16))))
        shot("02-editor")

        waitSaved()
        val stored = File(context.filesDir, "notes.json").readText()
        assertTrue("note is written to the file", stored.contains("Список покупок"))

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("Список покупок").assertIsDisplayed()
        compose.onNodeWithText(q(R.plurals.notes_count, 1), substring = true).assertIsDisplayed()
        shot("03-list-one")
    }

    @Test
    fun blankNote_isDiscarded() {
        launch()
        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText(s(R.string.empty_title)).assertIsDisplayed()
        assertTrue(repo.notes.value.isEmpty())
    }

    @Test
    fun search_filters_andOpensTheRightNote() {
        seed("Список покупок" to "Молоко и хлеб", "Идеи" to "Рассказ про кофе", "Планы" to "Выставка в субботу")
        launch()
        shot("04-list")

        compose.onNodeWithTag("search").performTextInput("кофе")
        compose.onNodeWithText(s(R.string.found, 1)).assertIsDisplayed()
        compose.onAllNodesWithTag("row").assertCountEquals(1)
        shot("05-search")

        compose.onNodeWithText("Идеи").performClick()
        compose.onNodeWithTag("title").assert(hasText("Идеи"))
        shot("06-editor-existing")

        systemBack()
        compose.onNodeWithTag("search").assert(hasText("кофе"))
    }

    @Test
    fun swipeRight_pins_swipeLeft_deletes_withUndo() {
        seed("Первая" to "один", "Вторая" to "два")
        launch()

        row("Первая").performTouchInput { swipeRight(startX = left + 10f, endX = right) }
        compose.waitUntil(5_000) { repo.notes.value.first { it.title == "Первая" }.pinned }
        shot("07a-after-swipe")
        // Строка возвращается на место и значок появляется с анимацией.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithContentDescription(s(R.string.pinned)).assertIsDisplayed() }.isSuccess
        }
        shot("07-pinned")

        row("Вторая").performTouchInput { swipeLeft(startX = right - 10f, endX = left) }
        // Сначала — подтверждение; заметка ещё на месте.
        compose.onNodeWithText(s(R.string.delete_one_title)).assertIsDisplayed()
        assertEquals(2, repo.notes.value.size)
        shot("08a-confirm-delete")
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.onNodeWithText(s(R.string.note_deleted)).assertIsDisplayed()
        assertEquals(1, repo.notes.value.size)
        shot("08-deleted-snackbar")

        compose.onNodeWithText(s(R.string.undo)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вторая").assertIsDisplayed()
        assertEquals(2, repo.notes.value.size)
    }

    @Test
    fun shortSwipe_doesNothing() {
        seed("Заметка" to "текст")
        launch()
        row("Заметка").performTouchInput { swipeRight(startX = left + 10f, endX = left + width * 0.35f) }
        compose.waitForIdle()
        assertFalse(repo.notes.value.single().pinned)
        row("Заметка").performTouchInput { swipeLeft(startX = right - 10f, endX = right - width * 0.35f) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText(s(R.string.delete_one_title)).fetchSemanticsNodes().isEmpty())
        assertEquals(1, repo.notes.value.size)
    }

    @Test
    fun selection_deletesSeveral_withUndo() {
        seed("Один" to "1", "Два" to "2", "Три" to "3")
        launch()
        row("Два").performTouchInput { longClick() }
        compose.onNodeWithTag("selected-count").assert(hasText(q(R.plurals.selected_count, 1)))
        row("Три").performClick()
        compose.onNodeWithTag("selected-count").assert(hasText(q(R.plurals.selected_count, 2)))
        shot("15-selection")
        compose.onNodeWithContentDescription(s(R.string.delete_selected)).performClick()
        compose.onNodeWithText(q(R.plurals.delete_many_title, 2)).assertIsDisplayed()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.onNodeWithText(q(R.plurals.notes_deleted, 2)).assertIsDisplayed()
        assertEquals(listOf("Один"), repo.notes.value.map { it.title })
        compose.onNodeWithText(s(R.string.undo)).performClick()
        compose.waitUntil(5_000) { repo.notes.value.size == 3 }

        // «Выбрать» в шапке, «Выбрать все» и выход по «назад».
        compose.onNodeWithContentDescription(s(R.string.select)).performClick()
        compose.onNodeWithContentDescription(s(R.string.select_all)).performClick()
        compose.onNodeWithTag("selected-count").assert(hasText(q(R.plurals.selected_count, 3)))
        systemBack()
        compose.onNodeWithTag("fab").assertIsDisplayed()
        assertEquals(3, repo.notes.value.size)
    }

    @Test
    fun fab_collapsesWhileScrollingDown_andExpandsAtRest() {
        seed(*(1..30).map { "Заметка $it" to "текст $it" }.toTypedArray())
        launch()
        shot("16a-fab-start")
        val expanded = {
            runCatching { compose.onNodeWithText(s(R.string.fab_new)).assertIsDisplayed() }.isSuccess
        }
        compose.waitUntil(5_000) { expanded() }
        compose.onNodeWithTag("note-list").performTouchInput { swipeUp(startY = bottom - 50f, endY = top + 50f, durationMillis = 600) }
        compose.waitUntil(3_000) { compose.onAllNodesWithText(s(R.string.fab_new)).fetchSemanticsNodes().isEmpty() }
        shot("16-fab-collapsed")
        compose.waitUntil(5_000) { expanded() }
    }

    @Test
    fun editorButtons_pinAndDelete() {
        seed("Заметка" to "Текст заметки")
        launch()
        compose.onNodeWithText("Заметка").performClick()
        compose.onNodeWithTag("pin").performClick()
        assertTrue(repo.notes.value.single().pinned)
        shot("09-editor-pinned")

        compose.onNodeWithTag("delete").performClick()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        assertEquals(1, repo.notes.value.size)
        compose.onNodeWithTag("delete").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.onNodeWithText(s(R.string.note_deleted)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.empty_title)).assertIsDisplayed()
    }

    @Test
    fun settings_themeAccentAndTextApply_andPersist() {
        seed("Одна" to "заметка")
        launch()
        compose.onNodeWithContentDescription(s(R.string.settings)).performClick()
        compose.onNodeWithText(s(R.string.settings)).assertIsDisplayed()
        compose.onNodeWithTag("version").assert(hasText(s(R.string.version, "", 0).substringBefore(" "), substring = true))
        shot("10-settings")

        compose.onNodeWithText(s(R.string.theme_dark)).performClick()
        compose.onNodeWithContentDescription(s(R.string.accent_cd, s(R.string.accent_mint))).performClick()
        compose.onNodeWithText("XL").performScrollTo().performClick()
        val st = settingsRepo.settings.value
        assertEquals(ThemeMode.DARK, st.theme)
        assertEquals(Accent.MINT, st.accent)
        assertEquals(TextSize.XL, st.textSize)
        shot("11-settings-changed")

        compose.onNodeWithText(s(R.string.compact_list)).performScrollTo().performClick()
        assertTrue(settingsRepo.settings.value.compactList)
        compose.onNodeWithText(s(R.string.backup)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(s(R.string.privacy)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.privacy_text)).assertIsDisplayed()
        shot("11b-privacy")
        compose.onNodeWithText(s(R.string.ok)).performClick()

        // «Назад» закрывает настройки, а не приложение; новый вид уже применён.
        systemBack()
        compose.onNodeWithText("Одна").assertIsDisplayed()
        shot("12-list-mint-dark-compact")
        compose.onNodeWithText("Одна").performClick()
        shot("13-editor-xl")

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithContentDescription(s(R.string.settings)).performClick()
        compose.onNodeWithText(s(R.string.reset_settings)).performScrollTo().performClick()
        assertEquals(AppSettings(), settingsRepo.settings.value)
    }

    @Test
    fun language_switchesInSettings_andSticks() {
        launch()
        compose.onNodeWithContentDescription(s(R.string.settings)).performClick()
        val other = if (lang == "ru") "en" else "ru"
        val label = s(if (other == "ru") R.string.language_ru else R.string.language_en)
        compose.onNodeWithText(label).performScrollTo().performClick()
        // Activity пересоздаётся на новом языке, экран настроек остаётся открытым.
        compose.waitUntil(10_000) { context.resources.configuration.locales[0].language == other }
        compose.waitForIdle()
        assertEquals(other, AppLanguage.current().tag)
        compose.onNodeWithText(s(R.string.section_language)).performScrollTo().assertIsDisplayed()
        shot("14-language-switched")
        applyLocale(lang)
    }

    @Test
    fun appShortcut_opensNewNote() {
        val intent = Intent(MainActivity.ACTION_NEW_NOTE)
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        launch(intent)
        compose.onNodeWithTag("title").assertIsDisplayed()
        assertEquals(1, repo.notes.value.size)
        assertTrue(repo.notes.value.single().isBlank)
    }

    @Test
    fun backup_roundTrip_readsOldFormat() {
        // Старый формат копий: массив без id и дат.
        val old = """[{"title":"Из браузера","content":"Привет"},{"title":"","content":"Без заголовка"}]"""
        assertEquals(2, repo.importJson(old))
        assertEquals(0, repo.importJson(repo.exportJson()))
        assertNull(repo.importJson("не json"))
        val exported = NotesJson.decode(repo.exportJson())!!
        assertEquals(setOf("Из браузера", ""), exported.map { it.title }.toSet())
        assertEquals("Без заголовка", exported.first { it.title.isEmpty() }.displayTitle)
    }
}
