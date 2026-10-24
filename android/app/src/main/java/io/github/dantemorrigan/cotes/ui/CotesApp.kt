package io.github.dantemorrigan.cotes.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.SwipeToDismissBoxState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dantemorrigan.cotes.AppLanguage
import io.github.dantemorrigan.cotes.Note
import io.github.dantemorrigan.cotes.NotesRepository
import io.github.dantemorrigan.cotes.SettingsRepository
import io.github.dantemorrigan.cotes.R
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
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

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

    fun newNote() {
        val current = repo.find(selectedId)
        if (current != null && current.isBlank) return
        open(repo.create().id)
    }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun delete(id: String) {
        val removed = repo.delete(id) ?: return
        if (selectedId == id) selectedId = null
        if (removed.isBlank) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                context.getString(R.string.note_deleted),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) repo.restore(removed)
        }
    }

    fun togglePin(id: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                if (ok) context.getString(R.string.backup_saved, context.notesCount(total))
                else context.getString(R.string.backup_failed)
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
            }
            val added = text?.let { repo.importJson(it) }
            message(
                when {
                    added == null -> context.getString(R.string.import_invalid)
                    added == 0 -> context.getString(R.string.import_nothing)
                    else -> context.getString(R.string.import_done, context.notesCount(added))
                }
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
            message(context.getString(if (ok) R.string.damaged_saved else R.string.backup_failed))
        }
    }

    // Текст, которым поделились из другого приложения, становится новой заметкой;
    // пустой текст — это ярлык «Новая заметка».
    LaunchedEffect(incoming) {
        if (incoming != null) {
            if (incoming.isEmpty()) newNote() else open(repo.create(incoming).id)
            onIncomingHandled()
        }
    }

    // Подсказки — только для тех, кто начинает с нуля.
    LaunchedEffect(Unit) {
        if (!onboardingDone && repo.notes.value.any { !it.isBlank }) finishOnboarding()
    }

    LaunchedEffect(writeFailed) {
        if (writeFailed) message(context.getString(R.string.save_failed))
    }

    CompositionLocalProvider(LocalCoachAnchors provides anchors) {
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
                        onDelete = { delete(it); finishOnboarding() },
                        onTogglePin = { togglePin(it); finishOnboarding() },
                        onOpenSettings = { settingsOpen = true },
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
                        onDelete = { delete(note.id) },
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
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                            onReset = {
                                settingsRepo.reset()
                                message(context.getString(R.string.settings_reset_done))
                                AppLanguage.set(AppLanguage.SYSTEM)
                            },
                        )
                    }
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
            modifier = Modifier.size((size * 0.72f).dp),
        )
    }
}

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
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val anchors = LocalCoachAnchors.current
    val q = query.trim()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val headerColor by animateColorAsState(
        if (scrolled) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        label = "header",
    )

    Surface(modifier, color = colors.surfaceContainerLow, contentColor = colors.onSurface) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.background(headerColor)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BrandTile(34)
                        Spacer(Modifier.width(12.dp))
                        Text("Cotes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
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
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("note-list"),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 104.dp),
                        ) {
                            itemsIndexed(notes, key = { _, it -> it.id }) { index, note ->
                                SwipeableNoteRow(
                                    note = note,
                                    first = index == 0,
                                    query = q,
                                    selected = note.id == selectedId,
                                    onClick = { focus.clearFocus(); onOpen(note.id) },
                                    onDelete = { onDelete(note.id) },
                                    onTogglePin = { onTogglePin(note.id) },
                                    // Без анимации исчезновения: иначе после «Отменить» строка вернулась бы
                                    // со старым состоянием свайпа (смахнутой за край).
                                    modifier = Modifier.animateItem(fadeOutSpec = null),
                                )
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = onNew,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_new)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .coachAnchor(anchors, CoachTarget.NewNote)
                    .testTag("fab"),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteRow(
    note: Note,
    first: Boolean,
    query: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val anchors = LocalCoachAnchors.current
    if (first) {
        DisposableEffect(Unit) { onDispose { anchors.bounds.remove(CoachTarget.FirstNote) } }
    }
    val currentDelete by rememberUpdatedState(onDelete)
    val currentPin by rememberUpdatedState(onTogglePin)
    // Обычный remember, а не rememberSaveable: список хранит сохраняемое состояние по ключу
    // заметки, и после «Отменить» строка вернулась бы уже смахнутой за край.
    val density = LocalDensity.current
    val state = remember {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, density, positionalThreshold = { it * 0.35f })
    }

    // Действие — когда свайп доведён до конца; закрепление возвращает строку на место.
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> currentDelete()
            SwipeToDismissBoxValue.StartToEnd -> {
                currentPin()
                state.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier
            .padding(vertical = 2.dp)
            .then(if (first) Modifier.coachAnchor(anchors, CoachTarget.FirstNote) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .testTag("row"),
        backgroundContent = {
            val direction = state.dismissDirection
            val bg by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> colors.error.copy(alpha = 0.18f)
                    SwipeToDismissBoxValue.StartToEnd -> colors.primaryContainer
                    else -> Color.Transparent
                },
                label = "swipe-bg",
            )
            val reached = state.targetValue != SwipeToDismissBoxValue.Settled
            val iconScale by animateFloatAsState(
                if (reached) 1.25f else 0.9f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "swipe-icon",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        Icons.Default.Delete, contentDescription = null, tint = colors.error,
                        modifier = Modifier.scale(iconScale),
                    )
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        painterResource(if (note.pinned) R.drawable.ic_pin else R.drawable.ic_pin_filled),
                        contentDescription = null, tint = colors.primary,
                        modifier = Modifier.scale(iconScale),
                    )
                    else -> Unit
                }
            }
        },
    ) {
        NoteRow(note, query, selected, onClick)
    }
}

@Composable
private fun NoteRow(note: Note, query: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val settings = LocalAppSettings.current
    val compact = settings.compactList
    val mark = SpanStyle(background = LocalCotesColors.current.mark, color = colors.onSurface)
    val title = note.displayTitle
    val bg by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerLow, label = "row")
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 12.dp)
    ) {
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
    DisposableEffect(Unit) { onDispose { anchors.bounds.remove(CoachTarget.SaveStatus) } }
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
                        onValueChange = { title = it; onEdit(it, content.text) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocus)
                            .testTag("title"),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                        singleLine = true,
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
                    BasicTextField(
                        value = content,
                        onValueChange = {
                            val changed = it.text != content.text
                            content = it
                            if (changed) onEdit(title, it.text)
                        },
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
                    )
                    // Нажатие на пустое место под текстом ставит курсор в конец.
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                content = content.copy(selection = TextRange(content.text.length))
                                runCatching { contentFocus.requestFocus() }
                            }
                    )
                }
            }

            HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .coachAnchor(anchors, CoachTarget.SaveStatus)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = when {
                    saveFailed -> SaveStatus.Failed
                    note.isBlank -> SaveStatus.New
                    saving -> SaveStatus.Saving
                    else -> SaveStatus.Saved
                }
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    modifier = Modifier.weight(1f),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
        }
    }
}
