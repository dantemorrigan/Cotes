package io.github.dantemorrigan.cotes.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dantemorrigan.cotes.AppLanguage
import io.github.dantemorrigan.cotes.ImportResult
import io.github.dantemorrigan.cotes.ImportSource
import io.github.dantemorrigan.cotes.NoteExport
import io.github.dantemorrigan.cotes.NoteImport
import io.github.dantemorrigan.cotes.Note
import io.github.dantemorrigan.cotes.NotesRepository
import io.github.dantemorrigan.cotes.SettingsRepository
import io.github.dantemorrigan.cotes.Sound
import io.github.dantemorrigan.cotes.Sounds
import io.github.dantemorrigan.cotes.Markdown
import io.github.dantemorrigan.cotes.R
import io.github.dantemorrigan.cotes.sync.SignInResult
import io.github.dantemorrigan.cotes.sync.SyncManager
import io.github.dantemorrigan.cotes.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class SaveStatus { Failed, New, Saving, Saved }

private val byPinThenRecent = compareByDescending<Note> { it.pinned }.thenByDescending { it.updated }

@Composable
fun CotesApp(
    repo: NotesRepository,
    settingsRepo: SettingsRepository,
    sync: SyncManager,
    incoming: String?,
    onIncomingHandled: () -> Unit,
) {
    val notes by repo.notes.collectAsStateWithLifecycle()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val saving by repo.saving.collectAsStateWithLifecycle()
    val writeFailed by repo.writeFailed.collectAsStateWithLifecycle()
    val damaged by repo.damaged.collectAsStateWithLifecycle()
    var damagedHandled by rememberSaveable { mutableStateOf(false) }
    val onboardingDone by settingsRepo.onboardingDone.collectAsStateWithLifecycle()
    var writeTipSeen by rememberSaveable { mutableStateOf(false) }
    val anchors = remember { CoachAnchors() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Режим выбора для массового удаления и заметки, ожидающие подтверждения удаления.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var confirmDelete by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val res = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val syncState by sync.state.collectAsStateWithLifecycle()
    val sounds = remember { Sounds.get(context) }
    var refreshing by remember { mutableStateOf(false) }
    // Выбор «Google или только здесь» — один раз, при самом первом запуске.
    var welcome by rememberSaveable {
        mutableStateOf(
            !settingsRepo.accountChoiceDone.value && !onboardingDone && repo.notes.value.none { !it.isBlank }
        )
    }

    val q = query.trim()
    val visible = remember(notes, q) { notes.filter { it.matches(q) }.sortedWith(byPinThenRecent) }
    val total = notes.count { !it.isBlank }
    val selected = notes.firstOrNull { it.id == selectedId }

    fun open(id: String?) {
        if (selectedId != id) {
            repo.discardIfBlank(selectedId)
            // Ушли из редактора — подсказку про автосохранение больше не показываем.
            if (selectedId != null) writeTipSeen = true
        }
        selectedId = id
    }

    fun finishOnboarding() {
        if (!onboardingDone) settingsRepo.setOnboardingDone(true)
    }

    fun newNote(sound: Boolean = true) {
        val current = repo.find(selectedId)
        if (current != null && current.isBlank) return
        if (sound) sounds.play(Sound.Create)
        open(repo.create().id)
    }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    val signIn = rememberGoogleSignIn(sync) { result ->
        val account = sync.state.value.account
        if (result == SignInResult.Success && account != null) {
            sounds.play(Sound.SignedIn)
            message(res.getString(R.string.signed_in, account.email))
        } else {
            res.signInError(result)?.let { message(it) }
        }
    }

    fun exitSelection() {
        selecting = false
        selection = ArrayList()
    }

    fun delete(ids: List<String>) {
        val removed = repo.deleteAll(ids)
        if (selectedId in ids) selectedId = null
        exitSelection()
        val real = removed.filterNot { it.isBlank }
        if (real.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        sounds.play(Sound.Delete)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                if (real.size == 1) res.getString(R.string.note_deleted)
                else res.getQuantityString(R.plurals.notes_deleted, real.size, real.size),
                actionLabel = res.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                repo.restoreAll(real)
                sounds.play(Sound.Restore)
            }
        }
    }

    /** Удаление — только после подтверждения; пустую новую заметку убираем сразу. */
    fun requestDelete(ids: List<String>) {
        val notesToDelete = ids.mapNotNull { repo.find(it) }
        if (notesToDelete.isEmpty()) return
        if (notesToDelete.all { it.isBlank }) delete(ids) else confirmDelete = ArrayList(ids)
    }

    fun toggleSelected(id: String) {
        val next = ArrayList(selection)
        if (!next.remove(id)) next += id
        selection = next
        if (next.isEmpty()) selecting = false
    }

    fun togglePin(id: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        sounds.play(Sound.Pin)
        repo.togglePin(id)
    }

    fun share(note: Note) {
        val text = if (note.title.isNotBlank()) note.title.trim() + "\n\n" + note.content else note.content
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, note.displayTitle)
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = repo.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            message(
                if (ok) res.getString(R.string.backup_saved, res.notesCount(total))
                else res.getString(R.string.backup_failed)
            )
        }
    }

    // Импорт: можно выбрать сразу несколько файлов — копии Cotes, экспорт других приложений, zip.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                uris.fold(ImportResult.Empty) { acc, uri -> acc + readImport(context, uri) }
            }
            val added = if (result.notes.isEmpty()) null else repo.importNotes(result.notes)
            val from = result.sources.sorted().joinToString(", ") { source ->
                when (source) {
                    ImportSource.Text -> res.getString(R.string.import_source_text)
                    ImportSource.Json -> res.getString(R.string.import_source_json)
                    ImportSource.GoogleKeep -> "Google Keep"
                    ImportSource.StandardNotes -> "Standard Notes"
                    else -> source.name
                }
            }
            message(
                when {
                    added == null -> res.getString(R.string.import_invalid)
                    added == 0 -> res.getString(R.string.import_nothing)
                    else -> res.getString(R.string.import_done, res.notesCount(added), from)
                }
            )
        }
    }

    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val list = repo.notes.value.filterNot { it.isBlank }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { NoteExport.markdownZip(list, it) } != null
                }.getOrDefault(false)
            }
            message(
                if (ok) res.getString(R.string.export_md_done, res.notesCount(list.size))
                else res.getString(R.string.backup_failed)
            )
        }
    }

    // Копия испорченного файла заметок — пользователь сохраняет её куда хочет.
    val damagedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val source = damaged
        if (uri == null || source == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
            }
            message(res.getString(if (ok) R.string.damaged_saved else R.string.backup_failed))
        }
    }

    // Текст, которым поделились из другого приложения, становится новой заметкой;
    // пустой текст — это ярлык «Новая заметка».
    LaunchedEffect(incoming) {
        if (incoming != null) {
            if (incoming.isEmpty()) newNote(sound = false) else open(repo.create(incoming).id)
            onIncomingHandled()
        }
    }

    if (welcome) {
        WelcomeScreen(sync) {
            settingsRepo.setAccountChoiceDone(true)
            // Вошли в Google — заметки подтягиваются из облака, подсказки для новичка не нужны.
            if (sync.signedIn || repo.notes.value.any { !it.isBlank }) finishOnboarding()
            welcome = false
        }
        return
    }

    // Подсказки — только для тех, кто начинает с нуля.
    LaunchedEffect(Unit) {
        if (!onboardingDone && repo.notes.value.any { !it.isBlank }) finishOnboarding()
    }

    LaunchedEffect(writeFailed) {
        if (writeFailed) message(res.getString(R.string.save_failed))
    }

    CompositionLocalProvider(LocalCoachAnchors provides anchors.takeUnless { onboardingDone }) {
        // Surface задаёт цвет текста и иконок по умолчанию для своей темы.
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                val listShown = !wide && selected == null
                val rootColor by animateColorAsState(
                    if (listShown) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background,
                    label = "root",
                )

                val list = @Composable { modifier: Modifier ->
                    NoteListPane(
                        notes = visible,
                        total = total,
                        query = query,
                        onQuery = { query = it },
                        selectedId = if (wide) selectedId else null,
                        onOpen = { open(it) },
                        onNew = { newNote() },
                        onDelete = { requestDelete(listOf(it)); finishOnboarding() },
                        onTogglePin = { togglePin(it); finishOnboarding() },
                        onOpenSettings = { settingsOpen = true },
                        selecting = selecting,
                        selection = selection.toSet(),
                        onStartSelection = { id ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selecting = true
                            if (id != null && id !in selection) selection = ArrayList(selection + id)
                        },
                        onToggleSelected = { toggleSelected(it) },
                        onSelectAll = {
                            val all = visible.filterNot { it.isBlank }.map { it.id }
                            selection = if (selection.containsAll(all)) ArrayList() else ArrayList(all)
                        },
                        onCancelSelection = { exitSelection() },
                        onDeleteSelected = { requestDelete(selection.toList()) },
                        refreshing = refreshing,
                        syncProgress = syncState.progress,
                        onRefresh = if (syncState.account != null) ({
                            if (!refreshing) scope.launch {
                                refreshing = true
                                val status = sync.refresh()
                                refreshing = false
                                when (status) {
                                    SyncStatus.Synced -> sounds.play(Sound.SyncDone)
                                    SyncStatus.Offline -> message(res.getString(R.string.sync_status_offline))
                                    SyncStatus.NeedsSignIn -> scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val r = snackbar.showSnackbar(
                                            res.getString(R.string.sync_expired),
                                            actionLabel = res.getString(R.string.sync_reconnect),
                                        )
                                        if (r == SnackbarResult.ActionPerformed) signIn.reconnect()
                                    }
                                    SyncStatus.SetupError, SyncStatus.Failed -> message(res.getString(R.string.sync_status_failed))
                                    else -> Unit
                                }
                            }
                        }) else null,
                        modifier = modifier,
                    )
                }
                val editor = @Composable { note: Note, modifier: Modifier ->
                    EditorPane(
                        note = note,
                        saving = saving,
                        saveFailed = writeFailed,
                        showBack = !wide,
                        onBack = { open(null) },
                        onEdit = { title, content -> repo.edit(note.id, title, content) },
                        onTogglePin = { togglePin(note.id) },
                        onDelete = { requestDelete(listOf(note.id)) },
                        onShare = { share(note) },
                        modifier = modifier,
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(rootColor)
                        .safeDrawingPadding()
                ) {
                    if (wide) {
                        // На планшете сразу открываем последнюю заметку.
                        LaunchedEffect(Unit) {
                            if (selectedId == null) visible.firstOrNull()?.let { selectedId = it.id }
                        }
                        Row(Modifier.fillMaxSize()) {
                            list(Modifier.width(340.dp).fillMaxHeight())
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Crossfade(targetState = selected?.id, modifier = Modifier.weight(1f), label = "editor") { id ->
                                val note = notes.firstOrNull { it.id == id }
                                if (note != null) editor(note, Modifier.fillMaxSize())
                                else EmptyPane(onNew = { newNote() })
                            }
                        }
                    } else {
                        BackHandler(enabled = selected != null) { open(null) }
                        AnimatedContent(
                            targetState = selected?.id,
                            transitionSpec = {
                                if (targetState != null) {
                                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(160)))
                                } else {
                                    (slideInHorizontally(tween(260)) { -it / 8 } + fadeIn(tween(200)))
                                        .togetherWith(slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(160)))
                                }
                            },
                            label = "pane",
                        ) { id ->
                            val note = notes.firstOrNull { it.id == id }
                            if (note != null) editor(note, Modifier.fillMaxSize())
                            else list(Modifier.fillMaxSize())
                        }
                    }
                    AnimatedVisibility(
                        visible = settingsOpen,
                        enter = slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220)),
                        exit = slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(180)),
                    ) {
                        SettingsScreen(
                            settings = settings,
                            onChange = { settingsRepo.update(it) },
                            onBack = { settingsOpen = false },
                            onExport = { exportLauncher.launch("cotes-${LocalDate.now()}.json") },
                            onExportMarkdown = { markdownLauncher.launch("cotes-markdown-${LocalDate.now()}.zip") },
                            onImport = { importLauncher.launch(arrayOf("*/*")) },
                            onReset = {
                                settingsRepo.reset()
                                message(res.getString(R.string.settings_reset_done))
                                AppLanguage.set(AppLanguage.SYSTEM)
                            },
                            syncState = syncState,
                            signIn = signIn,
                            onSyncNow = { sync.requestSync() },
                            onSignOut = {
                                sync.signOut()
                                sounds.play(Sound.SignOut)
                                message(res.getString(R.string.signed_out))
                            },
                        )
                    }
                    BackHandler(enabled = selecting) { exitSelection() }
                    // Объявлен последним — «назад» сначала закрывает настройки.
                    BackHandler(enabled = settingsOpen) { settingsOpen = false }

                    // На экране списка сообщение поднимается над кнопкой «Новая».
                    SnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (listShown && !settingsOpen) 84.dp else 0.dp),
                    )
                }

                val tip = when {
                    onboardingDone || settingsOpen || damaged != null && !damagedHandled -> null
                    selected != null && !writeTipSeen -> Tip.Write
                    selected != null && !wide -> null
                    total == 0 -> Tip.Create
                    else -> Tip.Swipe
                }
                if (tip != null) {
                    key(tip) {
                        CoachMark(
                            tip = tip,
                            anchors = anchors,
                            onPrimary = {
                                if (tip == Tip.Write) writeTipSeen = true else finishOnboarding()
                            },
                            onSkip = if (tip == Tip.Write) ({ finishOnboarding() }) else null,
                        )
                    }
                }

                confirmDelete?.let { ids ->
                    val single = ids.singleOrNull()?.let { id -> notes.firstOrNull { it.id == id } }
                    AlertDialog(
                        onDismissRequest = { confirmDelete = null },
                        title = {
                            Text(
                                if (single != null) stringResource(R.string.delete_one_title)
                                else pluralStringResource(R.plurals.delete_many_title, ids.size, ids.size)
                            )
                        },
                        text = {
                            Text(
                                if (single != null) stringResource(
                                    R.string.delete_one_text,
                                    single.displayTitle.ifEmpty { stringResource(R.string.untitled) },
                                )
                                else stringResource(R.string.delete_many_text)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { confirmDelete = null; delete(ids) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("confirm-delete"),
                            ) { Text(stringResource(R.string.delete)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                }

                if (damaged != null && !damagedHandled) {
                    AlertDialog(
                        onDismissRequest = { damagedHandled = true },
                        title = { Text(stringResource(R.string.damaged_title)) },
                        text = { Text(stringResource(R.string.damaged_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                damagedHandled = true
                                damagedLauncher.launch(damaged?.name ?: "cotes-damaged.json")
                            }) { Text(stringResource(R.string.damaged_save)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { damagedHandled = true }) { Text(stringResource(R.string.later)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandTile(size: Int) {
    // Пасхалка: касание картинки — «мяу» и пружинистый поворот-прыжок логотипа.
    val context = LocalContext.current
    val sounds = remember { Sounds.get(context) }
    val scope = rememberCoroutineScope()
    val wiggle = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    Box(
        Modifier
            .size(size.dp)
            .shadow(1.dp, RoundedCornerShape((size / 3.2f).dp))
            .clip(RoundedCornerShape((size / 3.2f).dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_cotes_cat),
            contentDescription = null,
            modifier = Modifier
                .size((size * 0.72f).dp)
                .graphicsLayer {
                    rotationZ = wiggle.value
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    sounds.play(Sound.Meow)
                    scope.launch {
                        launch {
                            pop.snapTo(1f)
                            pop.animateTo(1.22f, tween(110, easing = FastOutSlowInEasing))
                            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        wiggle.snapTo(0f)
                        for (a in listOf(-14f, 12f, -8f, 5f, 0f)) wiggle.animateTo(a, tween(90))
                    }
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteListPane(
    notes: List<Note>,
    total: Int,
    query: String,
    onQuery: (String) -> Unit,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSettings: () -> Unit,
    selecting: Boolean,
    selection: Set<String>,
    onStartSelection: (String?) -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
    syncProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val anchors = LocalCoachAnchors.current
    val q = query.trim()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val noteListContext = LocalContext.current
    val sounds = remember { Sounds.get(noteListContext) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val headerColor by animateColorAsState(
        if (selecting) colors.secondaryContainer else if (scrolled) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        label = "header",
    )

    // Кнопка «Новая» сворачивается в круг, пока список листают вниз, и раскрывается,
    // когда его тянут вверх или оставляют в покое.
    var fabCollapsed by remember { mutableStateOf(false) }
    val fabScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) fabCollapsed = true else if (available.y > 2f) fabCollapsed = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { moving ->
            if (!moving) {
                delay(900)
                fabCollapsed = false
            }
        }
    }

    // Шестерёнка проворачивается, и на середине оборота открываются настройки.
    val gear = remember { Animatable(0f) }

    Surface(modifier, color = colors.surfaceContainerLow, contentColor = colors.onSurface) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.background(headerColor)) {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "header-mode",
                    ) { inSelection ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(start = if (inSelection) 4.dp else 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (inSelection) {
                                IconButton(onClick = onCancelSelection, modifier = Modifier.testTag("cancel-selection")) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_selection))
                                }
                                Text(
                                    pluralStringResource(R.plurals.selected_count, selection.size, selection.size),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("selected-count"),
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = onSelectAll) {
                                    Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.select_all))
                                }
                                IconButton(onClick = onDeleteSelected, enabled = selection.isNotEmpty()) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                                }
                            } else {
                                BrandTile(34)
                                Spacer(Modifier.width(12.dp))
                                Text("Cotes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                AnimatedVisibility(total > 0, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                                    IconButton(onClick = { focus.clearFocus(); onStartSelection(null) }) {
                                        Icon(painterResource(R.drawable.ic_select), contentDescription = stringResource(R.string.select))
                                    }
                                }
                                IconButton(onClick = {
                                    sounds.play(Sound.Gear)
                                    scope.launch {
                                        val start = gear.value
                                        launch {
                                            gear.animateTo(start + 180f, tween(620, easing = FastOutSlowInEasing))
                                            gear.snapTo(gear.value % 360f)
                                        }
                                        snapshotFlow { gear.value }.first { it >= start + 70f || it < start }
                                        onOpenSettings()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.settings),
                                        modifier = Modifier.graphicsLayer { rotationZ = gear.value },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                            .testTag("search"),
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                IconButton(onClick = { onQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background,
                            unfocusedBorderColor = colors.outlineVariant,
                        ),
                    )
                }

                val countText = when {
                    q.isNotEmpty() -> stringResource(R.string.found, notes.size)
                    total > 0 -> stringResource(R.string.list_hint, pluralStringResource(R.plurals.notes_count, total, total))
                    else -> ""
                }
                AnimatedVisibility(countText.isNotEmpty()) {
                    Text(
                        countText,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    )
                }

                // Первый вход с сотнями заметок: показываем, сколько уже загружено.
                AnimatedVisibility(syncProgress != null) {
                    LinearProgressIndicator(
                        progress = { syncProgress ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .testTag("sync-progress"),
                    )
                }

                val mode = when {
                    notes.isNotEmpty() -> 0
                    q.isNotEmpty() -> 1
                    else -> 2
                }
                Crossfade(targetState = mode, label = "list-state") { m ->
                    when (m) {
                        1 -> EmptyMessage(
                            title = stringResource(R.string.empty_search_title),
                            text = stringResource(R.string.empty_search_text, q),
                            action = stringResource(R.string.clear_search),
                            onAction = { onQuery("") },
                            filled = false,
                        )
                        2 -> EmptyMessage(
                            title = stringResource(R.string.empty_title),
                            text = stringResource(R.string.empty_text),
                            action = stringResource(R.string.empty_action),
                            onAction = onNew,
                            filled = true,
                        )
                        else -> {
                          val column = @Composable {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(fabScroll)
                                .testTag("note-list"),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 104.dp),
                        ) {
                            itemsIndexed(notes, key = { _, it -> it.id }) { index, note ->
                                SwipeableNoteRow(
                                    note = note,
                                    first = index == 0,
                                    query = q,
                                    selected = note.id == selectedId,
                                    checked = if (selecting) note.id in selection else null,
                                    onClick = {
                                        focus.clearFocus()
                                        if (selecting) onToggleSelected(note.id) else onOpen(note.id)
                                    },
                                    onLongClick = {
                                        focus.clearFocus()
                                        if (selecting) onToggleSelected(note.id) else onStartSelection(note.id)
                                    },
                                    onDelete = { onDelete(note.id) },
                                    onTogglePin = { onTogglePin(note.id) },
                                    modifier = Modifier.animateItem(fadeOutSpec = null),
                                )
                            }
                        }
                          }
                          // Потянуть список вниз — синхронизировать с Google; без входа жеста нет.
                          if (onRefresh != null) {
                              PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                                  column()
                              }
                          } else column()
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !selecting,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    expanded = !fabCollapsed,
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note)) },
                    text = { Text(stringResource(R.string.fab_new)) },
                    modifier = Modifier
                        .coachAnchor(anchors, CoachTarget.NewNote)
                        .testTag("fab"),
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                )
            }
        }
    }
}

private enum class SwipeAction { Pin, Delete }

/**
 * Строка со свайпом. Тянется «резиново»: чем дальше, тем туже. Действие срабатывает,
 * только если протянуть примерно на две трети ширины, — тогда строка вибрирует, подпись
 * и значок становятся яркими. Отпустили раньше — строка просто пружинит обратно.
 */
@Composable
private fun SwipeableNoteRow(
    note: Note,
    first: Boolean,
    query: String,
    selected: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val anchors = LocalCoachAnchors.current
    if (first && anchors != null) {
        DisposableEffect(anchors) { onDispose { anchors.bounds.remove(CoachTarget.FirstNote) } }
    }
    val currentDelete by rememberUpdatedState(onDelete)
    val currentPin by rememberUpdatedState(onTogglePin)
    val haptics = LocalHapticFeedback.current
    var width by remember { mutableIntStateOf(1) }
    var raw by remember { mutableFloatStateOf(0f) }
    val limit = width * 0.7f
    val shown = if (raw == 0f) 0f else sign(raw) * limit * (1f - exp(-abs(raw) / limit))
    val threshold = width * 0.4f
    val armed = abs(shown) >= threshold
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val dragState = rememberDraggableState { delta -> raw += delta }

    Box(
        modifier
            .padding(vertical = 2.dp)
            .then(if (first) Modifier.coachAnchor(anchors, CoachTarget.FirstNote) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .testTag("row"),
    ) {
        if (shown != 0f) {
            SwipeBackground(
                action = if (shown > 0) SwipeAction.Pin else SwipeAction.Delete,
                pinned = note.pinned,
                progress = (abs(shown) / threshold).coerceIn(0f, 1f),
                armed = armed,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(shown.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = checked == null,
                    onDragStopped = { velocity ->
                        val action = when {
                            shown >= threshold -> SwipeAction.Pin
                            shown <= -threshold -> SwipeAction.Delete
                            else -> null
                        }
                        animate(
                            initialValue = raw,
                            targetValue = 0f,
                            initialVelocity = velocity,
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> raw = value }
                        when (action) {
                            SwipeAction.Pin -> currentPin()
                            SwipeAction.Delete -> currentDelete()
                            null -> Unit
                        }
                    },
                )
        ) {
            NoteRow(note, query, selected, checked, onClick, onLongClick)
        }
    }
}

@Composable
private fun SwipeBackground(action: SwipeAction, pinned: Boolean, progress: Float, armed: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val pin = action == SwipeAction.Pin
    val base = colors.surfaceContainerHigh
    val target = if (pin) colors.primaryContainer else colors.errorContainer
    val bg by animateColorAsState(if (armed) (if (pin) colors.primary else colors.error) else lerp(base, target, progress), label = "swipe-bg")
    val fg by animateColorAsState(
        if (armed) (if (pin) colors.onPrimary else colors.onError) else if (pin) colors.primary else colors.error,
        label = "swipe-fg",
    )
    val iconScale by animateFloatAsState(
        if (armed) 1.2f else 0.75f + 0.25f * progress,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swipe-icon",
    )
    Row(
        modifier
            .background(bg)
            .padding(horizontal = 22.dp),
        horizontalArrangement = if (pin) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = @Composable {
            Icon(
                if (pin) painterResource(if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_filled)
                else rememberVectorPainter(Icons.Default.Delete),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.scale(iconScale),
            )
        }
        val label = @Composable {
            Text(
                stringResource(if (!pin) R.string.delete else if (pinned) R.string.unpin else R.string.pin),
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .graphicsLayer { alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            )
        }
        if (pin) { icon(); label() } else { label(); icon() }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    query: String,
    selected: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val settings = LocalAppSettings.current
    val compact = settings.compactList
    val mark = SpanStyle(background = LocalCotesColors.current.mark, color = colors.onSurface)
    val title = note.displayTitle
    val bg by animateColorAsState(
        if (checked == true || selected) colors.secondaryContainer else colors.surfaceContainerLow,
        label = "row",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { if (checked != null) this.selected = checked }
            .padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = checked != null,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            SelectionMark(checked == true, Modifier.padding(end = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = if (title.isEmpty()) AnnotatedString(stringResource(if (note.isBlank) R.string.new_note else R.string.untitled))
                else highlight(title, query, mark),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (title.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = if (settings.showPreview) note.preview(query) else ""
            if (preview.isNotEmpty()) {
                Text(
                    text = highlight(preview, query, mark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(note.pinned, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(
                        painterResource(R.drawable.ic_pin_filled),
                        contentDescription = stringResource(R.string.pinned),
                        tint = colors.primary,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(12.dp),
                    )
                }
                Text(shortDate(LocalContext.current, note.updated), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Кружок выбора: заливается цветом и показывает галочку с пружинкой. */
@Composable
private fun SelectionMark(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) colors.primary else Color.Transparent, label = "mark-fill")
    val border by animateColorAsState(if (checked) colors.primary else colors.outline, label = "mark-border")
    val checkScale by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mark-check",
    )
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .scale(checkScale),
        )
    }
}

@Composable
private fun EmptyMessage(title: String, text: String, action: String, onAction: () -> Unit, filled: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        if (filled) Button(onClick = onAction) { Text(action) }
        else OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun EmptyPane(onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandTile(64)
        Text(
            stringResource(R.string.pane_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.pane_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Button(onClick = onNew) { Text(stringResource(R.string.new_note)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorPane(
    note: Note,
    saving: Boolean,
    saveFailed: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val anchors = LocalCoachAnchors.current
    if (anchors != null) {
        DisposableEffect(anchors) { onDispose { anchors.bounds.remove(CoachTarget.SaveStatus) } }
    }
    var title by rememberSaveable(note.id) { mutableStateOf(note.title) }
    var content by rememberSaveable(note.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(note.content))
    }
    val titleFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val pinTint by animateColorAsState(if (note.pinned) colors.primary else colors.onSurfaceVariant, label = "pin")
    val pinScale by animateFloatAsState(
        if (note.pinned) 1.15f else 1f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "pin-scale",
    )

    LaunchedEffect(note.id) {
        if (note.isBlank) runCatching { titleFocus.requestFocus() }
    }

    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    // Готовая заметка открывается для чтения — разметка скрыта; новая — сразу для правки.
    var editing by rememberSaveable(note.id) { mutableStateOf(note.isBlank) }
    var focusContentOnEdit by remember { mutableStateOf(false) }

    fun startEditing() {
        if (!editing) {
            editing = true
            content = content.copy(selection = TextRange(content.text.length))
        }
        focusContentOnEdit = true
    }

    LaunchedEffect(editing, focusContentOnEdit) {
        if (editing && focusContentOnEdit) {
            focusContentOnEdit = false
            runCatching { contentFocus.requestFocus() }
        }
    }

    val markdownColors = MarkdownColors(
        accent = colors.primary,
        faint = colors.onSurfaceVariant.copy(alpha = 0.5f),
        muted = colors.onSurfaceVariant,
        codeBackground = colors.surfaceContainerHigh,
        mark = colors.primary.copy(alpha = 0.22f),
    )
    val markdown = remember(markdownColors, settings.textSize) {
        MarkdownVisualTransformation(markdownColors, settings.textSize.sp)
    }

    // Заметку изменили на другом устройстве, пока она открыта здесь: показываем новую версию,
    // иначе следующая правка записала бы поверх неё старый текст. Свои правки сюда не попадают:
    // они меняют и поле, и заметку одновременно.
    LaunchedEffect(note.title, note.content) {
        if (note.title != title) title = note.title
        if (note.content != content.text) {
            val end = note.content.length
            content = TextFieldValue(
                note.content,
                TextRange(minOf(content.selection.start, end), minOf(content.selection.end, end)),
            )
        }
    }

    Surface(modifier, color = colors.background, contentColor = colors.onBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_list))
                    }
                }
                Spacer(Modifier.weight(1f))
                // Карандаш — править (разметка видна), галочка — готово (оформленный текст).
                IconButton(
                    onClick = {
                        if (editing) {
                            focusManager.clearFocus()
                            editing = false
                        } else {
                            startEditing()
                        }
                    },
                    modifier = Modifier.testTag("edit-toggle"),
                ) {
                    Crossfade(editing, label = "edit-icon") { e ->
                        Icon(
                            if (e) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = stringResource(if (e) R.string.done_editing else R.string.edit_note),
                        )
                    }
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.testTag("pin")) {
                    Crossfade(note.pinned, label = "pin-icon") { pinned ->
                        Icon(
                            painterResource(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                            contentDescription = stringResource(if (pinned) R.string.unpin else R.string.pin),
                            tint = pinTint,
                            modifier = Modifier.scale(pinScale),
                        )
                    }
                }
                IconButton(onClick = onShare, enabled = !note.isBlank) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete")) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_note))
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { value ->
                            // Заголовок переносится по словам, а Enter переводит к тексту заметки.
                            val clean = value.replace("\n", "")
                            title = clean
                            onEdit(clean, content.text)
                            if (clean != value) startEditing()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocus)
                            .testTag("title"),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 4,
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { contentFocus.requestFocus() }),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) {
                                    Text(
                                        stringResource(R.string.title_hint),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    if (editing) BasicTextField(
                        value = content,
                        onValueChange = { value ->
                            var next = value
                            if (value.text != content.text) {
                                // Enter в пункте списка продолжает список, на пустом пункте — заканчивает.
                                if (value.selection.collapsed) {
                                    Markdown.continueList(content.text, value.text, value.selection.start)?.let { (text, cursor) ->
                                        next = TextFieldValue(text, TextRange(cursor))
                                    }
                                }
                            } else if (value.selection.collapsed && value.selection != content.selection) {
                                // Нажали в «[ ]» — отмечаем пункт (или снимаем отметку).
                                Markdown.checkboxAt(value.text, value.selection.start)?.let { inner ->
                                    next = value.copy(text = Markdown.toggleCheckbox(value.text, inner))
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            val changed = next.text != content.text
                            content = next
                            if (changed) onEdit(title, next.text)
                        },
                        visualTransformation = markdown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .focusRequester(contentFocus)
                            .testTag("content"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colors.onSurface,
                            fontSize = settings.textSize.sp.sp,
                            lineHeight = settings.textSize.lineHeight.sp,
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        decorationBox = { inner ->
                            Box {
                                if (content.text.isEmpty()) {
                                    Text(
                                        stringResource(R.string.content_hint),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = settings.textSize.sp.sp,
                                            lineHeight = settings.textSize.lineHeight.sp,
                                        ),
                                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                inner()
                            }
                        },
                    ) else NoteView(
                        text = content.text,
                        colors = markdownColors,
                        baseSp = settings.textSize.sp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = colors.onSurface,
                            fontSize = settings.textSize.sp.sp,
                            lineHeight = settings.textSize.lineHeight.sp,
                        ),
                        hintColor = colors.onSurfaceVariant.copy(alpha = 0.6f),
                        onToggle = { inner ->
                            val text = Markdown.toggleCheckbox(content.text, inner)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            content = content.copy(text = text)
                            onEdit(title, text)
                        },
                        onEdit = { startEditing() },
                    )
                    // Нажатие на пустое место под текстом ставит курсор в конец.
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                content = content.copy(selection = TextRange(content.text.length))
                                startEditing()
                            }
                    )
                }
            }

            // Панель форматирования — только в режиме правки, над клавиатурой.
            AnimatedVisibility(editing) {
                FormatBar { action ->
                    val e = action(content.text, content.selection.start, content.selection.end)
                    content = TextFieldValue(e.text, TextRange(e.start, e.end))
                    onEdit(title, e.text)
                    focusContentOnEdit = true
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            val barModifier = Modifier
                .fillMaxWidth()
                .coachAnchor(anchors, CoachTarget.SaveStatus)
                .padding(horizontal = 20.dp, vertical = 8.dp)
            val statusLine = @Composable { modifier: Modifier ->
                val status = when {
                    saveFailed -> SaveStatus.Failed
                    note.isBlank -> SaveStatus.New
                    saving -> SaveStatus.Saving
                    else -> SaveStatus.Saved
                }
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    modifier = modifier,
                    label = "status",
                ) { state ->
                    val text = when (state) {
                        SaveStatus.Failed -> stringResource(R.string.status_save_failed)
                        SaveStatus.New -> stringResource(R.string.new_note)
                        SaveStatus.Saving -> stringResource(R.string.status_saving)
                        SaveStatus.Saved -> stringResource(R.string.status_saved, longDate(context, note.updated))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state == SaveStatus.Saved) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(14.dp),
                            )
                        }
                        Text(
                            text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state == SaveStatus.Failed) colors.error else colors.onSurfaceVariant,
                        )
                    }
                }
            }
            // Курсор стоит в ссылке — её можно открыть (в поле ввода нажатие ставит курсор, а не переходит).
            val link = remember(content.text, content.selection, editing) {
                if (editing && content.selection.collapsed) Markdown.linkAt(content.text, content.selection.start) else null
            }
            val uriHandler = LocalUriHandler.current
            val countsLine = @Composable {
                val text = content.text
                val words = wordCount(text)
                Text(
                    stringResource(
                        R.string.counts,
                        pluralStringResource(R.plurals.words, words, words),
                        pluralStringResource(R.plurals.chars, text.length, text.length),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("counts"),
                )
            }
            // Статус и счётчики стоят в одной строке, а если не помещаются (узкий экран, крупный шрифт) —
            // счётчики переходят на следующую; текст нигде не обрезается.
            FlowRow(
                barModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                statusLine(Modifier)
                countsLine()
                if (link != null) {
                    Text(
                        stringResource(R.string.open_link),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { runCatching { uriHandler.openUri(link) } }
                            .testTag("open-link"),
                    )
                }
            }
        }
    }
}

/** Читает выбранный файл для импорта: имя и время изменения — от системы, если она их знает. */
private fun readImport(context: Context, uri: Uri): ImportResult = runCatching {
    var name = uri.lastPathSegment.orEmpty()
    var modified: Long? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                ?.let { c.getString(it) }?.let { name = it }
            c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf { it >= 0 }
                ?.let { if (!c.isNull(it)) c.getLong(it) else null }?.takeIf { it > 0 }?.let { modified = it }
        }
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching ImportResult.Empty
    NoteImport.fromFile(name, bytes, modified)
}.getOrDefault(ImportResult.Empty)

/**
 * Заметка в режиме чтения: оформленный текст без значков разметки. Ссылки открываются,
 * ☐ / ☑ отмечают пункт, нажатие в остальном месте переводит в правку.
 */
@Composable
private fun NoteView(
    text: String,
    colors: MarkdownColors,
    baseSp: Int,
    style: TextStyle,
    hintColor: Color,
    onToggle: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val toggle by rememberUpdatedState(onToggle)
    val rendered = remember(text, colors, baseSp) { MarkdownRender.render(text, colors, baseSp) { toggle(it) } }
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 120.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit)
        .testTag("note-view")
    if (text.isEmpty()) {
        Text(stringResource(R.string.content_hint), style = style, color = hintColor, modifier = modifier)
    } else {
        Text(rendered, style = style, modifier = modifier)
    }
}

private typealias Format = (String, Int, Int) -> Markdown.Edit

private fun fmt(label: String, description: Int, action: Format) = Triple(label, description, action)

/** Кнопки Markdown для выделенного текста или текущей строки. */
@Composable
private fun FormatBar(onFormat: (Format) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val items: List<Triple<String, Int, Format>> = listOf(
        fmt("H", R.string.fmt_heading) { t, s, e -> Markdown.heading(t, s, e) },
        fmt("B", R.string.fmt_bold) { t, s, e -> Markdown.wrap(t, s, e, "**") },
        fmt("I", R.string.fmt_italic) { t, s, e -> Markdown.wrap(t, s, e, "*") },
        fmt("S", R.string.fmt_strike) { t, s, e -> Markdown.wrap(t, s, e, "~~") },
        fmt("<>", R.string.fmt_code) { t, s, e -> Markdown.wrap(t, s, e, "`") },
        fmt("•", R.string.fmt_bullets) { t, s, e -> Markdown.block(t, s, e, Markdown.Block.Bullet) },
        fmt("1.", R.string.fmt_numbers) { t, s, e -> Markdown.block(t, s, e, Markdown.Block.Numbered) },
        fmt("☐", R.string.fmt_task) { t, s, e -> Markdown.block(t, s, e, Markdown.Block.Task) },
        fmt("❝", R.string.fmt_quote) { t, s, e -> Markdown.block(t, s, e, Markdown.Block.Quote) },
        fmt("🔗", R.string.fmt_link) { t, s, e -> Markdown.link(t, s, e) },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("format-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, description, action) ->
            val name = stringResource(description)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onFormat(action) }
                    .semantics { contentDescription = name }
                    .testTag("fmt-$label"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = colors.onSurfaceVariant,
                    fontSize = 17.sp,
                    fontWeight = if (label == "B" || label == "H") FontWeight.Bold else FontWeight.Medium,
                    fontStyle = if (label == "I") FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (label == "S") TextDecoration.LineThrough else null,
                    fontFamily = if (label == "<>") FontFamily.Monospace else null,
                )
            }
        }
    }
}
