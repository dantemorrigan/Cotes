package io.github.dantemorrigan.cotes

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dantemorrigan.cotes.sync.SyncManager
import io.github.dantemorrigan.cotes.ui.CotesApp
import io.github.dantemorrigan.cotes.ui.CotesTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// AppCompatActivity — ради выбора языка внутри приложения на Android 8–12.
class MainActivity : AppCompatActivity() {
    /** Текст новой заметки: из «Поделиться» или пустой — из ярлыка «Новая заметка». */
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readShared(intent)
        // Пока приложение открыто, раз в минуту подтягиваем правки с других устройств.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(FOREGROUND_SYNC_MS)
                    SyncManager.get(this@MainActivity).requestSync()
                }
            }
        }
        val repo = NotesRepository.get(this)
        val settingsRepo = SettingsRepository.get(this)
        val sync = SyncManager.get(this)
        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle()
            val dark = settings.isDark(isSystemInDarkTheme())
            // Значки строки состояния — под тему приложения, а не системы.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            CotesTheme(settings, dark) {
                CotesApp(repo, settingsRepo, sync, incoming.value, onIncomingHandled = { incoming.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShared(intent)
    }

    override fun onStart() {
        super.onStart()
        // Подтянуть то, что изменилось на других устройствах.
        SyncManager.get(this).requestSync()
    }

    override fun onStop() {
        super.onStop()
        NotesRepository.get(this).flush()
        SyncManager.get(this).scheduleBackgroundSync()
    }

    private fun readShared(intent: Intent?) {
        if (intent?.action == ACTION_NEW_NOTE) {
            incoming.value = ""
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        incoming.value = if (subject.isNullOrBlank() || text.startsWith(subject)) text else "$subject\n\n$text"
    }

    companion object {
        /** Ярлык на иконке приложения (res/xml/shortcuts.xml). */
        private const val FOREGROUND_SYNC_MS = 60_000L
        const val ACTION_NEW_NOTE = "io.github.dantemorrigan.cotes.action.NEW_NOTE"
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
