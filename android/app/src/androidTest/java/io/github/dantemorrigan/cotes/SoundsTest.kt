package io.github.dantemorrigan.cotes

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Звуки: включены по умолчанию и выключаются в настройках. */
@RunWith(AndroidJUnit4::class)
class SoundsTest : CotesTest() {

    @Test
    fun soundsSwitch_turnsOffAndPersists() {
        seed("Одна" to "заметка")
        launch()
        assertTrue(settingsRepo.settings.value.sounds)
        compose.onNodeWithContentDescription(s(R.string.settings)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(s(R.string.sounds))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(s(R.string.sounds)).performScrollTo().performClick()
        compose.waitUntil(5_000) { !settingsRepo.settings.value.sounds }
        assertFalse(settingsRepo.settings.value.sounds)
        shot("41-settings-sounds")
    }
}
