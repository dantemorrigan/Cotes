package io.github.dantemorrigan.cotes

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dantemorrigan.cotes.ui.CotesApp
import io.github.dantemorrigan.cotes.ui.CotesTheme

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readShared(intent)
        val repo = NotesRepository.get(this)
        val settingsRepo = SettingsRepository.get(this)
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
                CotesApp(repo, settingsRepo, incoming.value, onIncomingHandled = { incoming.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShared(intent)
    }

    override fun onStop() {
        super.onStop()
        NotesRepository.get(this).flush()
    }

    private fun readShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        incoming.value = if (subject.isNullOrBlank() || text.startsWith(subject)) text else "$subject\n\n$text"
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
