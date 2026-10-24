package io.github.dantemorrigan.cotes

import android.app.LocaleManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Общее для инструментальных тестов. Язык задаёт аргумент `locale` (en или ru),
 * скриншоты кладутся в /sdcard/cotes-shots/<язык>-<тема>-<имя>.png.
 */
abstract class CotesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    protected lateinit var scenario: ActivityScenario<MainActivity>
    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val context get() = instrumentation.targetContext
    protected val repo get() = NotesRepository.get(context)
    protected val settingsRepo get() = SettingsRepository.get(context)
    protected val lang: String = InstrumentationRegistry.getArguments().getString("locale").orEmpty().ifEmpty { "en" }
    private val night: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Подсказки первого запуска мешают большинству сценариев; их проверяет OnboardingTest. */
    protected open val onboardingDone = true

    @Before
    fun baseSetUp() {
        applyLocale(lang)
        repo.resetForTests()
        settingsRepo.reset()
        settingsRepo.setOnboardingDone(onboardingDone)
    }

    @After
    fun baseTearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    protected fun applyLocale(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (context.resources.configuration.locales[0].language != tag && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    protected fun launch(intent: Intent? = null) {
        scenario = if (intent != null) ActivityScenario.launch(intent) else ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** Строка на текущем языке приложения — как её видит Activity. */
    protected fun s(@StringRes id: Int, vararg args: Any): String {
        var out = ""
        scenario.onActivity { out = it.getString(id, *args) }
        return out
    }

    protected fun q(@PluralsRes id: Int, n: Int): String {
        var out = ""
        scenario.onActivity { out = it.resources.getQuantityString(id, n, n) }
        return out
    }

    protected fun seed(vararg notes: Pair<String, String>) {
        notes.forEach { (title, content) ->
            val note = repo.create()
            repo.edit(note.id, title, content)
            Thread.sleep(5)
        }
        repo.flush()
    }

    protected fun shell(cmd: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }
    }

    protected fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val dir = "/sdcard/cotes-shots"
        shell("mkdir -p $dir")
        shell("screencap -p $dir/$lang-${if (night) "dark" else "light"}-$name.png")
    }

    protected fun waitSaved() {
        compose.waitUntil(5_000) { !repo.saving.value }
    }

    protected fun systemBack() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    protected fun row(title: String) =
        compose.onAllNodesWithTag("row").filter(hasAnyDescendant(hasText(title))).onFirst()
}
