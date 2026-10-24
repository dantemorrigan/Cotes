package io.github.dantemorrigan.cotes

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Синхронизация и открытый редактор: правка с другого устройства не теряется. */
@RunWith(AndroidJUnit4::class)
class SyncUiTest : CotesTest() {

    @Test
    fun openNote_showsRemoteChange_andKeepsItOnNextEdit() {
        seed("Покупки" to "Молоко")
        launch()
        row("Покупки").performClick()
        compose.onNodeWithTag("content").assertExists()

        // Пока заметка открыта, синхронизация приносит её новую версию с другого устройства.
        val note = repo.notes.value.single()
        val remote = note.copy(content = "Молоко\nХлеб", changed = note.changed + 1_000)
        repo.applySync(listOf(remote), emptyMap(), emptyList(), mapOf(note.id to note.changed))
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("content") and hasText("Молоко\nХлеб")).fetchSemanticsNodes().isNotEmpty()
        }

        // Следующая правка здесь продолжает новую версию, а не затирает её старой.
        compose.onNodeWithTag("content").performTextInput("!")
        waitSaved()
        val saved = repo.notes.value.single().content
        assertEquals(true, saved.contains("Хлеб") && saved.contains("!"))
        shot("40-remote-change-in-editor")
    }
}
