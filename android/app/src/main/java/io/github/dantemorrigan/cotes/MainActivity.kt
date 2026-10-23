package io.github.dantemorrigan.cotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.dantemorrigan.cotes.ui.CotesApp
import io.github.dantemorrigan.cotes.ui.CotesTheme

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readShared(intent)
        val repo = NotesRepository.get(this)
        setContent {
            CotesTheme {
                CotesApp(repo, incoming.value, onIncomingHandled = { incoming.value = null })
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
}
