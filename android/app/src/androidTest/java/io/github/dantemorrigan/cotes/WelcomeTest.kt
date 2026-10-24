package io.github.dantemorrigan.cotes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Первый запуск: выбор «Google или только на устройстве»; локальный профиль в настройках. */
@RunWith(AndroidJUnit4::class)
class WelcomeTest : CotesTest() {
    override val onboardingDone = false
    override val accountChoiceDone = false

    @Test
    fun firstLaunch_choosesLocal_thenOnboarding() {
        launch()
        compose.onNodeWithTag("welcome").assertIsDisplayed()
        compose.onNodeWithText(s(R.string.welcome_title)).assertIsDisplayed()
        shot("30-welcome")

        compose.onNodeWithTag("welcome-local").performClick()
        compose.waitUntil(5_000) { settingsRepo.accountChoiceDone.value }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isEmpty() }
        // Дальше — обычные подсказки первого запуска.
        val tip = s(R.string.onb_create_title)
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(tip)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun localProfile_inSettings() {
        settingsRepo.setAccountChoiceDone(true)
        settingsRepo.setOnboardingDone(true)
        launch()
        compose.onNodeWithContentDescription(s(R.string.settings)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("profile")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile").assertIsDisplayed()
        compose.onNodeWithText(s(R.string.profile_local)).assertIsDisplayed()
        compose.onNodeWithTag("sign-in").performScrollTo().assertIsDisplayed()
        shot("31-settings-local-profile")
    }

    @Test
    fun existingNotes_skipWelcome() {
        seed("Старая" to "заметка")
        launch()
        compose.waitUntil(5_000) { settingsRepo.onboardingDone.value }
        assertTrue(compose.onAllNodes(hasTestTag("welcome")).fetchSemanticsNodes().isEmpty())
        assertFalse(settingsRepo.accountChoiceDone.value)
    }
}
