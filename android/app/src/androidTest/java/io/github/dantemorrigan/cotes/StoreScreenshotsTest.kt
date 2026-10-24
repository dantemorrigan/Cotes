package io.github.dantemorrigan.cotes

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Скриншоты для магазинов: правдоподобные заметки на языке прогона. */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest : CotesTest() {

    private val ru = lang == "ru"

    private fun sample() {
        if (ru) seed(
            "Цитаты" to "«Простота — высшая степень изысканности».\n«Лучшее — враг хорошего».",
            "Подарки" to "Маме — тёплый шарф\nСаше — пластинка\nБабушке — фотокнига",
            "Идеи для рассказа" to "Смотритель маяка собирает потерянные письма и однажды находит своё.",
            "Выходные" to "Суббота — выставка, потом прогулка по набережной.\nВоскресенье — дочитать книгу и испечь пирог.",
            "Покупки" to "Молоко\nХлеб\nКофе в зёрнах\nЯблоки",
        ) else seed(
            "Quotes" to "“Simplicity is the ultimate sophistication.”\n“Done is better than perfect.”",
            "Gift ideas" to "Mom — a warm scarf\nAlex — a vinyl record\nGrandma — a photo book",
            "Story ideas" to "A lighthouse keeper collects lost letters and one day finds his own.",
            "Weekend" to "Saturday — the exhibition, then a walk by the river.\nSunday — finish the book and bake a pie.",
            "Groceries" to "Milk\nBread\nCoffee beans\nApples",
        )
        repo.notes.value.first { it.title == if (ru) "Покупки" else "Groceries" }.let { repo.togglePin(it.id) }
        repo.flush()
    }

    @Test
    fun storeScreenshots() {
        sample()
        launch()
        shot("store-1-list")

        compose.onNodeWithText(if (ru) "Выходные" else "Weekend").performClick()
        shot("store-2-editor")
        compose.onNodeWithTag("back").performClick()

        // Настройки — до поиска, чтобы на снимке не было клавиатуры.
        compose.onNodeWithContentDescription(s(R.string.settings)).performClick()
        shot("store-4-settings")
        systemBack()

        compose.onNodeWithTag("search").performTextInput(if (ru) "книг" else "book")
        shot("store-3-search")
    }
}
