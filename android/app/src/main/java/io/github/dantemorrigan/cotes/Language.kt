package io.github.dantemorrigan.cotes

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Язык интерфейса. Хранит его AppCompat (на Android 13+ — сама система,
 * поэтому выбор виден и в системных настройках приложения).
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    EN("en"),
    RU("ru");

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            return entries.firstOrNull { it.tag == locales[0]?.language } ?: SYSTEM
        }

        fun set(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
            )
        }
    }
}
