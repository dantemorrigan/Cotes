package io.github.dantemorrigan.cotes.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    fun delete(note: Note) {
        val removed = repo.delete(note.id) ?: return
        selectedId = null
        if (removed.isBlank) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Заметка удалена", actionLabel = "Отменить", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                repo.restore(removed)
                open(removed.id)
            }
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp

            val list = @Composable { modifier: Modifier ->
                NoteListPane(
                    notes = visible,
                    total = total,
                    query = query,
                    onQuery = { query = it },
                    selectedId = if (wide) selectedId else null,
                    onOpen = { open(it) },
                    onNew = { newNote() },
                    onExport = { exportLauncher.launch("cotes-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = modifier,
                )
            }
            val editor = @Composable { note: Note, modifier: Modifier ->
                EditorPane(
                    note = note,
                    showBack = !wide,
                    onBack = { open(null) },
                    onEdit = { title, content -> repo.edit(note.id, title, content) },
                    onTogglePin = { repo.togglePin(note.id) },
                    onDelete = { delete(note) },
                    onShare = { share(note) },
                    modifier = modifier,
                )
            }

            if (wide) {
                // На планшете сразу открываем последнюю заметку, как в веб-версии.
                LaunchedEffect(Unit) {
                    if (selectedId == null) visible.firstOrNull()?.let { selectedId = it.id }
                }
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(340.dp).fillMaxHeight())
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (selected != null) editor(selected, Modifier.weight(1f))
                    else EmptyPane(onNew = { newNote() }, modifier = Modifier.weight(1f))
                }
            } else {
                BackHandler(enabled = selected != null) { open(null) }
                if (selected != null) editor(selected, Modifier.fillMaxSize())
                else list(Modifier.fillMaxSize())
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val q = query.trim()
    val focus = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier.background(colors.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize()) {
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
                    .padding(horizontal = 12.dp),
                placeholder = { Text("Поиск") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background,
                    unfocusedBorderColor = colors.outlineVariant,
                ),
            )

            val countText = when {
                q.isNotEmpty() -> "Найдено: ${notes.size}"
                total > 0 -> "$total ${plural(total, "заметка", "заметки", "заметок")}"
                else -> ""
            }
            if (countText.isNotEmpty()) {
                Text(
                    countText,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }

            when {
                notes.isEmpty() && q.isNotEmpty() -> EmptyMessage(
                    title = "Ничего не нашлось",
                    text = "По запросу «$q» заметок нет.",
                    action = "Сбросить поиск",
                    onAction = { onQuery("") },
                    filled = false,
                )
                notes.isEmpty() -> EmptyMessage(
                    title = "Пока пусто",
                    text = "Первая заметка сохранится только на этом устройстве.",
                    action = "Новая заметка",
                    onAction = onNew,
                    filled = true,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteRow(note, q, selected = note.id == selectedId, onClick = { focus.clearFocus(); onOpen(note.id) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Новая заметка")
        }
    }
}

@Composable
private fun NoteRow(note: Note, query: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val mark = SpanStyle(background = LocalCotesColors.current.mark, color = colors.onSurface)
    val title = note.displayTitle
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
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
            if (note.pinned) {
                Icon(
                    painterResource(R.drawable.ic_pin_filled),
                    contentDescription = "Закреплена",
                    tint = colors.primary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
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
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    var content by rememberSaveable(note.id) { mutableStateOf(note.content) }
    val titleFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }

    LaunchedEffect(note.id) {
        if (note.isBlank) runCatching { titleFocus.requestFocus() }
    }

    Column(modifier.background(colors.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку заметок")
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onTogglePin) {
                Icon(
                    painterResource(if (note.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                    contentDescription = if (note.pinned) "Открепить" else "Закрепить",
                    tint = if (note.pinned) colors.primary else colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare, enabled = !note.isBlank) {
                Icon(Icons.Default.Share, contentDescription = "Поделиться")
            }
            IconButton(onClick = onDelete) {
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
                    onValueChange = { title = it; onEdit(it, content) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
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
                    onValueChange = { content = it; onEdit(title, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .focusRequester(contentFocus),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.onSurface,
                        fontSize = 17.sp,
                        lineHeight = 28.sp,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box {
                            if (content.isEmpty()) {
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
            }
        }

        HorizontalDivider(color = colors.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val words = wordCount(content)
            Text(
                if (note.isBlank) "Новая заметка" else "Изменено ${longDate(note.updated)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$words ${plural(words, "слово", "слова", "слов")} · ${content.length} ${plural(content.length, "символ", "символа", "символов")}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
