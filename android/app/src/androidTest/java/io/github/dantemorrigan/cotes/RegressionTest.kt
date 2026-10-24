package io.github.dantemorrigan.cotes

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Мини-регресс основных сценариев. Скриншоты кладутся в /sdcard/cotes-shots/
 * (светлая или тёмная тема — по текущему режиму эмулятора).
 */
@RunWith(AndroidJUnit4::class)
class RegressionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repo get() = NotesRepository.get(context)
    private val settingsRepo get() = SettingsRepository.get(context)
    private val night: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    @Before
    fun setUp() {
        repo.resetForTests()
        settingsRepo.reset()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun seed(vararg notes: Pair<String, String>) {
        notes.forEach { (title, content) ->
            val note = repo.create()
            repo.edit(note.id, title, content)
            Thread.sleep(5)
        }
        repo.flush()
    }

    private fun shell(cmd: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val dir = "/sdcard/cotes-shots"
        shell("mkdir -p $dir")
        shell("screencap -p $dir/${if (night) "dark" else "light"}-$name.png")
    }

    private fun waitSaved() {
        compose.waitUntil(5_000) { !repo.saving.value }
    }

    private fun systemBack() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun row(title: String) =
        compose.onAllNodesWithTag("row").filter(hasAnyDescendant(hasText(title))).onFirst()

    @Test
    fun createNote_autosaves_andShowsInList() {
        launch()
        compose.onNodeWithText("Пока пусто").assertIsDisplayed()
        shot("01-empty")

        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("title").performTextInput("Список покупок")
        compose.onNodeWithTag("content").performTextInput("Молоко\nХлеб\nКофе")
        compose.onNodeWithTag("counts").assert(hasText("3 слова · 16 символов"))
        shot("02-editor")

        waitSaved()
        val stored = File(context.filesDir, "notes.json").readText()
        assertTrue("заметка записана в файл", stored.contains("Список покупок"))

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("Список покупок").assertIsDisplayed()
        compose.onNodeWithText("1 заметка", substring = true).assertIsDisplayed()
        shot("03-list-one")
    }

    @Test
    fun blankNote_isDiscarded() {
        launch()
        compose.onNodeWithTag("fab").performClick()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("Пока пусто").assertIsDisplayed()
        assertTrue(repo.notes.value.isEmpty())
    }

    @Test
    fun search_filters_andOpensTheRightNote() {
        seed("Список покупок" to "Молоко и хлеб", "Идеи" to "Рассказ про кофе", "Планы" to "Выставка в субботу")
        launch()
        shot("04-list")

        compose.onNodeWithTag("search").performTextInput("кофе")
        compose.onNodeWithText("Найдено: 1").assertIsDisplayed()
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
        compose.waitForIdle()
        assertTrue(repo.notes.value.first { it.title == "Первая" }.pinned)
        compose.onNodeWithContentDescription("Закреплена").assertIsDisplayed()
        shot("07-pinned")

        row("Вторая").performTouchInput { swipeLeft(startX = right - 10f, endX = left) }
        compose.onNodeWithText("Заметка удалена").assertIsDisplayed()
        assertEquals(1, repo.notes.value.size)
        shot("08-deleted-snackbar")

        compose.onNodeWithText("Отменить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вторая").assertIsDisplayed()
        assertEquals(2, repo.notes.value.size)
        shot("08b-undo")
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
        compose.onNodeWithText("Заметка удалена").assertIsDisplayed()
        compose.onNodeWithText("Пока пусто").assertIsDisplayed()
    }

    @Test
    fun settings_themeAccentAndTextApply_andPersist() {
        seed("Одна" to "заметка")
        launch()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Настройки").assertIsDisplayed()
        compose.onNodeWithTag("version").assert(hasText("Версия", substring = true))
        shot("10-settings")

        compose.onNodeWithText("Тёмная").performClick()
        compose.onNodeWithContentDescription("Цвет Мята").performClick()
        compose.onNodeWithText("XL").performScrollTo().performClick()
        val s = settingsRepo.settings.value
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(Accent.MINT, s.accent)
        assertEquals(TextSize.XL, s.textSize)
        shot("11-settings-changed")

        compose.onNodeWithText("Компактный список").performScrollTo().performClick()
        assertTrue(settingsRepo.settings.value.compactList)
        compose.onNodeWithText("Резервная копия всех заметок").performScrollTo().assertIsDisplayed()

        // «Назад» закрывает настройки, а не приложение; новый вид уже применён.
        systemBack()
        compose.onNodeWithText("Одна").assertIsDisplayed()
        shot("12-list-mint-dark-compact")
        compose.onNodeWithText("Одна").performClick()
        shot("13-editor-xl")

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Сбросить настройки").performScrollTo().performClick()
        assertEquals(AppSettings(), settingsRepo.settings.value)
    }

    @Test
    fun backup_roundTrip_isCompatibleWithWebVersion() {
        // Старый формат веб-версии: массив без id и дат.
        val web = """[{"title":"Из браузера","content":"Привет"},{"title":"","content":"Без заголовка"}]"""
        assertEquals(2, repo.importJson(web))
        assertEquals(0, repo.importJson(repo.exportJson()))
        assertNull(repo.importJson("не json"))
        val exported = NotesJson.decode(repo.exportJson())!!
        assertEquals(setOf("Из браузера", ""), exported.map { it.title }.toSet())
        assertEquals("Без заголовка", exported.first { it.title.isEmpty() }.displayTitle)
    }
}
