package io.github.dantemorrigan.cotes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Подсказки первого запуска: три шага, «Пропустить», и никаких подсказок для тех, у кого есть заметки. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest : CotesTest() {
    override val onboardingDone = false

    private fun waitTip(@androidx.annotation.StringRes title: Int) {
        val text = s(title)
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun threeSteps_fromFirstNoteToSwipe() {
        launch()
        waitTip(R.string.onb_create_title)
        compose.onNodeWithText(s(R.string.onb_create_text)).assertIsDisplayed()
        shot("20-tip-create")

        compose.onNodeWithTag("fab").performClick()
        waitTip(R.string.onb_write_title)
        compose.onNodeWithTag("title").performTextInput("Первая")
        shot("21-tip-write")
        compose.onNodeWithText(s(R.string.onb_got_it)).performClick()

        compose.onNodeWithTag("back").performClick()
        waitTip(R.string.onb_swipe_title)
        shot("22-tip-swipe")
        assertFalse(settingsRepo.onboardingDone.value)

        row("Первая").performTouchInput { swipeRight(startX = left + 10f, endX = right) }
        compose.waitUntil(5_000) { settingsRepo.onboardingDone.value }
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("tip") == 0 }
    }

    @Test
    fun skip_endsOnboarding() {
        launch()
        waitTip(R.string.onb_create_title)
        compose.onNodeWithText(s(R.string.onb_skip)).performClick()
        assertTrue(settingsRepo.onboardingDone.value)
    }

    @Test
    fun existingNotes_noTips() {
        seed("Старая" to "заметка")
        launch()
        compose.waitUntil(5_000) { settingsRepo.onboardingDone.value }
        Thread.sleep(800)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTagCount("tip") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
