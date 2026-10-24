package io.github.dantemorrigan.cotes

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Крупный системный шрифт, альбомная ориентация (две панели) и длинная заметка. */
@RunWith(AndroidJUnit4::class)
class LayoutTest : CotesTest() {

    @After
    fun restore() {
        shell("settings put system font_scale 1.0")
    }

    @Test
    fun hugeFontScale_keepsMainControlsReachable() {
        shell("settings put system font_scale 2.0")
        seed("Очень длинный заголовок заметки, который не помещается" to "Текст")
        launch()
        compose.onNodeWithTag("fab").assertIsDisplayed()
        compose.onNodeWithTag("search").assertIsDisplayed()
        shot("30-font-200-list")
        compose.onNodeWithTag("row").performClick()
        compose.onNodeWithTag("delete").assertIsDisplayed()
        compose.onNodeWithTag("counts").assertIsDisplayed()
        shot("31-font-200-editor")
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("fab").assertIsDisplayed()
    }

    @Test
    fun landscape_showsListAndEditorSideBySide() {
        seed("Слева список" to "А справа — открытая заметка")
        launch()
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("note-list").assertIsDisplayed()
        compose.onNodeWithTag("content").assertIsDisplayed()
        shot("32-landscape")
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test
    fun longNote_opensAndScrolls() {
        val text = (1..3_000).joinToString("\n") { "Строка $it" }
        seed("Длинная" to text)
        launch()
        compose.onNodeWithText("Длинная").performClick()
        compose.onNodeWithTag("content").assertIsDisplayed()
        compose.onNodeWithTag("counts").assertIsDisplayed()
        shot("33-long-note")
    }
}
