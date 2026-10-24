package io.github.dantemorrigan.cotes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

/** Цвет акцента: светлый и тёмный вариант. Лаванда — фирменный цвет Cotes. */
enum class Accent(val title: String, val light: Long, val dark: Long) {
    LAVENDER("Лаванда", 0xFF6A3FB5, 0xFFC7A6FF),
    MINT("Мята", 0xFF1F7A5C, 0xFF7FD8B5),
    OCEAN("Океан", 0xFF1E5FA8, 0xFF9CC4FF),
    CORAL("Коралл", 0xFFB3412E, 0xFFFFA48F),
    GRAPHITE("Графит", 0xFF4A4F57, 0xFFC5CAD3),
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
