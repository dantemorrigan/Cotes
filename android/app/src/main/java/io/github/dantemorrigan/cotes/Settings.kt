package io.github.dantemorrigan.cotes

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(@StringRes val title: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

/** Цвет акцента: светлый и тёмный вариант. Лаванда — фирменный цвет Cotes. */
enum class Accent(@StringRes val title: Int, val light: Long, val dark: Long) {
    LAVENDER(R.string.accent_lavender, 0xFF6A3FB5, 0xFFC7A6FF),
    MINT(R.string.accent_mint, 0xFF1F7A5C, 0xFF7FD8B5),
    OCEAN(R.string.accent_ocean, 0xFF1E5FA8, 0xFF9CC4FF),
    CORAL(R.string.accent_coral, 0xFFB3412E, 0xFFFFA48F),
    GRAPHITE(R.string.accent_graphite, 0xFF4A4F57, 0xFFC5CAD3),
}

enum class TextSize(val title: String, val sp: Int, val lineHeight: Int) {
    S("S", 15, 24),
    M("M", 17, 28),
    L("L", 19, 30),
    XL("XL", 22, 34),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.LAVENDER,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val textSize: TextSize = TextSize.M,
    val compactList: Boolean = false,
    val showPreview: Boolean = true,
    val sounds: Boolean = true,
) {
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

class SettingsRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Отдельно от AppSettings: «Сбросить настройки» не должен заново запускать подсказки.
    private val _onboardingDone = MutableStateFlow(prefs.getBoolean("onboardingDone", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setOnboardingDone(done: Boolean) {
        _onboardingDone.value = done
        prefs.edit().putBoolean("onboardingDone", done).apply()
    }

    // Выбор «Google или только на устройстве» показываем один раз — при первом запуске.
    private val _accountChoiceDone = MutableStateFlow(prefs.getBoolean("accountChoiceDone", false))
    val accountChoiceDone: StateFlow<Boolean> = _accountChoiceDone.asStateFlow()

    fun setAccountChoiceDone(done: Boolean) {
        _accountChoiceDone.value = done
        prefs.edit().putBoolean("accountChoiceDone", done).apply()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        write(next)
    }

    fun reset() = update { AppSettings() }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(prefs.getString("theme", null), d.theme),
            accent = enumOr(prefs.getString("accent", null), d.accent),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            pureBlack = prefs.getBoolean("pureBlack", d.pureBlack),
            textSize = enumOr(prefs.getString("textSize", null), d.textSize),
            compactList = prefs.getBoolean("compactList", d.compactList),
            showPreview = prefs.getBoolean("showPreview", d.showPreview),
            sounds = prefs.getBoolean("sounds", d.sounds),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("accent", s.accent.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("pureBlack", s.pureBlack)
            .putString("textSize", s.textSize.name)
            .remove("editorFont")
            .putBoolean("compactList", s.compactList)
            .putBoolean("showPreview", s.showPreview)
            .putBoolean("sounds", s.sounds)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
