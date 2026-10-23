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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import io.github.dantemorrigan.cotes.Note
import io.github.dantemorrigan.cotes.NotesRepository
import io.github.dantemorrigan.cotes.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val byPinThenRecent = compareByDescending<Note> { it.pinned }.thenByDescending { it.updated }

@Composable
fun CotesApp(repo: NotesRepository, incoming: String?, onIncomingHandled: () -> Unit) {
    val notes by repo.notes.collectAsStateWithLifecycle()
    val saving by repo.saving.collectAsStateWithLifecycle()
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
        if (selectedId != id) repo.discardIfBlank(selectedId)
        selectedId = id
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
            val result = snackbar.showSnackbar("Заметка удалена", actionLabel = "Отменить", duration = SnackbarDuration.Long)
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
            message(if (ok) "Копия сохранена: $total ${plural(total, "заметка", "заметки", "заметок")}" else "Не удалось сохранить файл")
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
                    added == null -> "Это не похоже на копию Cotes"
                    added == 0 -> "Все заметки из файла уже есть"
                    else -> "Восстановлено: $added ${plural(added, "заметка", "заметки", "заметок")}"
                }
            )
        }
    }

    // Текст, которым поделились из другого приложения, становится новой заметкой.
    LaunchedEffect(incoming) {
        if (incoming != null) {
            open(repo.create(incoming).id)
            onIncomingHandled()
        }
    }

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
                    onDelete = { delete(it) },
                    onTogglePin = { togglePin(it) },
                    onExport = { exportLauncher.launch("cotes-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = modifier,
                )
            }
            val editor = @Composable { note: Note, modifier: Modifier ->
                EditorPane(
                    note = note,
                    saving = saving,
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
                    // На планшете сразу открываем последнюю заметку, как в веб-версии.
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
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val q = query.trim()
    val focus = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
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
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Резервная копия всех (.json)") },
                                    onClick = { menuOpen = false; onExport() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Восстановить из копии…") },
                                    onClick = { menuOpen = false; onImport() },
                                )
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
                        placeholder = { Text("Поиск") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                IconButton(onClick = { onQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
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
                    q.isNotEmpty() -> "Найдено: ${notes.size}"
                    total > 0 -> "$total ${plural(total, "заметка", "заметки", "заметок")} · свайп → закрепить, ← удалить"
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
                            title = "Ничего не нашлось",
                            text = "По запросу «$q» заметок нет.",
                            action = "Сбросить поиск",
                            onAction = { onQuery("") },
                            filled = false,
                        )
                        2 -> EmptyMessage(
                            title = "Пока пусто",
                            text = "Первая заметка сохранится только на этом устройстве.",
                            action = "Написать заметку",
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
                            items(notes, key = { it.id }) { note ->
                                SwipeableNoteRow(
                                    note = note,
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
                text = { Text("Новая") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
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
    query: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentDelete by rememberUpdatedState(onDelete)
    val currentPin by rememberUpdatedState(onTogglePin)
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })

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
    val mark = SpanStyle(background = LocalCotesColors.current.mark, color = colors.onSurface)
    val title = note.displayTitle
    val bg by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerLow, label = "row")
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (title.isEmpty()) AnnotatedString(if (note.isBlank) "Новая заметка" else "Без названия")
            else highlight(title, query, mark),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (title.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = note.preview(query)
        if (preview.isNotEmpty()) {
            Text(
                text = highlight(preview, query, mark),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(note.pinned, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(
                    painterResource(R.drawable.ic_pin_filled),
                    contentDescription = "Закреплена",
                    tint = colors.primary,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(12.dp),
                )
            }
            Text(shortDate(note.updated), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
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
            "Выберите заметку",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "или начните новую — сохранять ничего не нужно.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Button(onClick = onNew) { Text("Новая заметка") }
    }
}

@Composable
private fun EditorPane(
    note: Note,
    saving: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку заметок")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onTogglePin, modifier = Modifier.testTag("pin")) {
                    Crossfade(note.pinned, label = "pin-icon") { pinned ->
                        Icon(
                            painterResource(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                            contentDescription = if (pinned) "Открепить" else "Закрепить",
                            tint = pinTint,
                            modifier = Modifier.scale(pinScale),
                        )
                    }
                }
                IconButton(onClick = onShare, enabled = !note.isBlank) {
                    Icon(Icons.Default.Share, contentDescription = "Поделиться")
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete")) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить заметку")
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
                                        "Заголовок",
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
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        decorationBox = { inner ->
                            Box {
                                if (content.text.isEmpty()) {
                                    Text(
                                        "Начните писать…",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = when {
                    note.isBlank -> "Новая заметка"
                    saving -> "Сохранение…"
                    else -> "Сохранено · ${longDate(note.updated)}"
                }
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    modifier = Modifier.weight(1f),
                    label = "status",
                ) { text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (text.startsWith("Сохранено")) {
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
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val text = content.text
                val words = wordCount(text)
                Text(
                    "$words ${plural(words, "слово", "слова", "слов")} · ${text.length} ${plural(text.length, "символ", "символа", "символов")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("counts"),
                )
            }
        }
    }
}
