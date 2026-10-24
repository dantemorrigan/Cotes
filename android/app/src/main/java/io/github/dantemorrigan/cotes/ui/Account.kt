package io.github.dantemorrigan.cotes.ui

import android.accounts.AccountManager
import android.app.Activity
import android.content.res.Resources
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dantemorrigan.cotes.R
import io.github.dantemorrigan.cotes.sync.GoogleAuth
import io.github.dantemorrigan.cotes.sync.SignInResult
import io.github.dantemorrigan.cotes.sync.SyncAccount
import io.github.dantemorrigan.cotes.sync.SyncManager
import io.github.dantemorrigan.cotes.sync.SyncState
import io.github.dantemorrigan.cotes.sync.SyncStatus
import kotlinx.coroutines.launch

private val GoogleSurface = Color.White
private val GoogleBorder = Color(0xFF747775)
private val GoogleText = Color(0xFF1F1F1F)
private val GoogleTextSecondary = Color(0xFF444746)

/** Запуск входа в Google: [start] — выбрать аккаунт и войти, [reconnect] — вернуть отозванный доступ. */
class GoogleSignIn(val start: () -> Unit, val reconnect: () -> Unit, val busy: Boolean)

/**
 * Вход в три шага: системный выбор аккаунта → согласие на доступ к папке приложения в Drive
 * (Google показывает его только в первый раз) → первая синхронизация.
 * [onResult] получает null, если пользователь передумал.
 */
@Composable
fun rememberGoogleSignIn(sync: SyncManager, onResult: (SignInResult?) -> Unit): GoogleSignIn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // Переживает пересоздание Activity, пока открыт экран согласия.
    var pendingEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var reconnecting by rememberSaveable { mutableStateOf(false) }

    fun done(result: SignInResult?) {
        busy = false
        pendingEmail = null
        onResult(result)
    }

    fun failed(e: Exception) = done(
        when {
            GoogleAuth.isCanceled(e) -> null
            GoogleAuth.isSetupError(e) -> SignInResult.SetupError
            GoogleAuth.isNetworkError(e) -> SignInResult.Offline
            else -> SignInResult.Failed(e)
        }
    )

    suspend fun finish(email: String, token: String) {
        if (reconnecting) {
            sync.resume(token)
            done(SignInResult.Success)
        } else {
            done(sync.signIn(email, token))
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val email = pendingEmail
        if (res.resultCode != Activity.RESULT_OK || email == null) {
            done(null)
            return@rememberLauncherForActivityResult
        }
        busy = true
        when (val t = GoogleAuth.tokenFromConsent(context, res.data)) {
            is GoogleAuth.Result.Token -> scope.launch { finish(email, t.token) }
            is GoogleAuth.Result.Failed -> failed(t.error)
            is GoogleAuth.Result.NeedsConsent -> done(null)
        }
    }

    fun authorize(email: String) {
        busy = true
        pendingEmail = email
        scope.launch {
            when (val t = GoogleAuth.token(context, email)) {
                is GoogleAuth.Result.Token -> finish(email, t.token)
                is GoogleAuth.Result.NeedsConsent ->
                    runCatching { consent.launch(IntentSenderRequest.Builder(t.intent).build()) }
                        .onFailure { failed(it as? Exception ?: RuntimeException(it)) }
                is GoogleAuth.Result.Failed -> failed(t.error)
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val email = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (res.resultCode == Activity.RESULT_OK && email != null) authorize(email) else done(null)
    }

    return GoogleSignIn(
        start = {
            if (!busy) {
                reconnecting = false
                busy = true
                runCatching { pick.launch(GoogleAuth.chooseAccountIntent()) }.onFailure { failed(RuntimeException(it)) }
            }
        },
        reconnect = {
            val email = sync.state.value.account?.email
            if (!busy && email != null) {
                reconnecting = true
                authorize(email)
            }
        },
        busy = busy,
    )
}

/** Текст для неудачного входа; null — сообщать нечего. */
fun Resources.signInError(result: SignInResult?): String? = when (result) {
    null, SignInResult.Success -> null
    SignInResult.Offline -> getString(R.string.sign_in_offline)
    SignInResult.SetupError -> getString(R.string.sign_in_setup_error)
    is SignInResult.Failed -> getString(R.string.sign_in_failed, result.error.message ?: result.error.javaClass.simpleName)
}

@Composable
fun Avatar(account: SyncAccount?, size: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val photo = account?.avatar
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (account == null) colors.surfaceContainerHighest else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(
                photo.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            account != null -> Text(
                account.name.trim().take(1).uppercase(),
                color = colors.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
            else -> Icon(Icons.Default.Person, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Раздел «Аккаунт» в настройках: локальный профиль или вошедший Google-аккаунт. */
@Composable
fun AccountSection(
    state: SyncState,
    signIn: GoogleSignIn,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val googleAvailable = remember { GoogleAuth.available(context) }
    val account = state.account
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Section(stringResource(R.string.section_account))
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("profile"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account?.name ?: stringResource(R.string.profile_local),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    account?.email ?: stringResource(R.string.profile_local_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Divider()
        if (account == null) {
            ActionRow(
                title = stringResource(R.string.sign_in_google),
                subtitle = stringResource(if (googleAvailable) R.string.sign_in_google_sub else R.string.sign_in_no_play),
                enabled = googleAvailable && !signIn.busy,
                busy = signIn.busy,
                onClick = signIn.start,
                googleLogo = true,
                modifier = Modifier.testTag("sign-in"),
            )
        } else {
            val needsSignIn = state.status == SyncStatus.NeedsSignIn
            ActionRow(
                title = stringResource(if (needsSignIn) R.string.sync_reconnect else R.string.sync_now),
                subtitle = syncStatusText(state),
                enabled = !signIn.busy && state.status != SyncStatus.Syncing,
                busy = signIn.busy || state.status == SyncStatus.Syncing,
                warning = needsSignIn || state.status == SyncStatus.SetupError,
                onClick = if (needsSignIn) signIn.reconnect else onSyncNow,
                modifier = Modifier.testTag("sync-now"),
            )
            Divider()
            Text(
                stringResource(R.string.sign_out),
                color = colors.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { confirmSignOut = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("sign-out"),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_text)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) {
                    Text(stringResource(R.string.sign_out), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun syncStatusText(state: SyncState): String {
    val context = LocalContext.current
    val last = state.lastSync.takeIf { it > 0 }?.let {
        DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
    return when (state.status) {
        SyncStatus.Syncing -> state.progress?.let { stringResource(R.string.sync_status_progress, (it * 100).toInt()) }
            ?: stringResource(R.string.sync_status_syncing)
        SyncStatus.Offline -> stringResource(R.string.sync_status_offline)
        SyncStatus.NeedsSignIn -> stringResource(R.string.sync_status_reconnect)
        SyncStatus.SetupError -> stringResource(R.string.sign_in_setup_error)
        SyncStatus.Failed -> stringResource(R.string.sync_status_failed)
        SyncStatus.Idle, SyncStatus.Synced ->
            if (last != null) stringResource(R.string.sync_status_done, last) else stringResource(R.string.sync_status_never)
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    googleLogo: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (googleLogo) {
            Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled || busy) colors.primary else colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) colors.error else colors.onSurfaceVariant,
            )
        }
        if (busy) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * Первый запуск: хранить заметки только на устройстве или войти в Google.
 * [onDone] вызывается после выбора; при входе — когда заметки уже подтянулись.
 */
@Composable
fun WelcomeScreen(sync: SyncManager, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val res = LocalResources.current
    val googleAvailable = remember { GoogleAuth.available(context) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val signIn = rememberGoogleSignIn(sync) { result ->
        when {
            result == null -> Unit
            sync.signedIn -> onDone()
            else -> error = res.signInError(result)
        }
    }

    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .testTag("welcome"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_cotes_cat), contentDescription = null, modifier = Modifier.size(54.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.welcome_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                ChoiceCard(
                    icon = { Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(28.dp)) },
                    title = stringResource(R.string.welcome_google),
                    text = stringResource(if (googleAvailable) R.string.welcome_google_text else R.string.sign_in_no_play),
                    primary = true,
                    enabled = googleAvailable && !signIn.busy,
                    busy = signIn.busy,
                    onClick = { error = null; signIn.start() },
                    modifier = Modifier.testTag("welcome-google"),
                )
                Spacer(Modifier.height(12.dp))
                ChoiceCard(
                    icon = { Text("📱", fontSize = 28.sp) },
                    title = stringResource(R.string.welcome_local),
                    text = stringResource(R.string.welcome_local_text),
                    primary = false,
                    enabled = !signIn.busy,
                    busy = false,
                    onClick = onDone,
                    modifier = Modifier.testTag("welcome-local"),
                )
                if (signIn.busy) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.welcome_connecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    error?.let { text ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.sign_in_failed_title)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                TextButton(onClick = { error = null; onDone() }) { Text(stringResource(R.string.welcome_local)) }
            },
        )
    }
}

@Composable
private fun ChoiceCard(
    icon: @Composable () -> Unit,
    title: String,
    text: String,
    primary: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            // Кнопка Google — светлая в любой теме, как требуют правила бренда Google.
            .background(if (primary) GoogleSurface else colors.surfaceContainerLow)
            .border(1.dp, if (primary) GoogleBorder else colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (primary) GoogleText else colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (primary) GoogleTextSecondary else colors.onSurfaceVariant,
            )
        }
        if (busy) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}
