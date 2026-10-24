package io.github.dantemorrigan.cotes

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.util.UUID

data class Note(
    val id: String,
    val title: String = "",
    val content: String = "",
    val created: Long,
    val updated: Long = created,
    val pinned: Boolean = false,
    /** Время любого изменения, включая закрепление: по нему синхронизация решает, чья версия новее. */
    val changed: Long = updated,
) {
    val isBlank: Boolean get() = title.isBlank() && content.isBlank()

    /** Заголовок, а если его нет — первая непустая строка текста. */
    val displayTitle: String
        get() = title.trim().ifEmpty {
            // Первая строка без значков Markdown: «# Покупки» → «Покупки».
            content.lineSequence().map { Markdown.plainLine(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
        }
}

fun newNoteId(): String =
    System.currentTimeMillis().toString(36) + UUID.randomUUID().toString().take(6)

/**
 * Формат файла заметок и резервной копии:
 * { app, version, exported, notes: [{ id, title, content, created, updated, pinned, changed }], deleted: { id: время } }.
 * deleted — следы удалённых заметок для синхронизации, только в файле приложения.
 * Читается и голый массив заметок (старые копии).
 */
object NotesJson {
    fun encode(notes: List<Note>, deleted: Map<String, Long> = emptyMap()): String {
        val arr = JSONArray()
        notes.forEach { arr.put(noteToJson(it)) }
        val root = JSONObject()
            .put("app", "Cotes")
            .put("version", 1)
            .put("exported", Instant.now().toString())
            .put("notes", arr)
        if (deleted.isNotEmpty()) {
            val d = JSONObject()
            deleted.toSortedMap().forEach { (id, at) -> d.put(id, at) }
            root.put("deleted", d)
        }
        return root.toString(2)
    }

    fun noteToJson(n: Note): JSONObject = JSONObject()
        .put("id", n.id)
        .put("title", n.title)
        .put("content", n.content)
        .put("created", n.created)
        .put("updated", n.updated)
        .put("pinned", n.pinned)
        .put("changed", n.changed)

    /** Следы удалённых заметок из файла приложения; у резервных копий их нет. */
    fun decodeDeleted(text: String): Map<String, Long> {
        val d = runCatching { JSONObject(text.trim()).optJSONObject("deleted") }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, Long>()
        d.keys().forEach { id -> d.optLong(id, 0L).takeIf { it > 0 }?.let { out[id] = it } }
        return out
    }

    /** null — если это не похоже на заметки Cotes. */
    fun decode(text: String): List<Note>? {
        val trimmed = text.trim()
        val arr = try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("notes") ?: return null
                else -> return null
            }
        } catch (e: Exception) {
            return null
        }
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        val out = ArrayList<Note>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            // У старых заметок нет дат: чем позже в списке, тем «новее».
            val fallback = now - (arr.length() - i) * 1000L
            val created = o.optLong("created", 0L).takeIf { it > 0 } ?: fallback
            val id = (o.opt("id") as? String)?.takeIf { it.isNotEmpty() && seen.add(it) }
                ?: newNoteId().also { seen.add(it) }
            val updated = o.optLong("updated", 0L).takeIf { it > 0 } ?: created
            out += Note(
                id = id,
                title = o.opt("title") as? String ?: "",
                content = o.opt("content") as? String ?: "",
                created = created,
                updated = updated,
                pinned = o.optBoolean("pinned", false),
                changed = o.optLong("changed", 0L).takeIf { it > 0 } ?: updated,
            )
        }
        return out
    }
}

/**
 * Одно хранилище на процесс: экраны и повторные запуски Activity видят одни и те же заметки.
 * Пустые заметки живут только в памяти, пока открыты, и в файл не попадают.
 *
 * Файл, который не удалось прочитать, никогда не перезаписывается: он переименовывается
 * в notes-damaged-<время>.json, а приложение предлагает сохранить его копию.
 */
class NotesRepository internal constructor(notesFile: File) {
    private val file = AtomicFile(notesFile)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSave: Job? = null

    /** Если файл заметок испорчен и его не удалось отложить в сторону — не пишем ничего. */
    private var writesBlocked = false
    private val _damaged = MutableStateFlow<File?>(null)

    /** Отложенная копия испорченного файла заметок, если она появилась при этом запуске. */
    val damaged: StateFlow<File?> = _damaged.asStateFlow()

    private val _notes: MutableStateFlow<List<Note>>
    val notes: StateFlow<List<Note>>
    private val _deleted: MutableStateFlow<Map<String, Long>>

    /** Следы удалённых заметок: id → время удаления. Нужны, чтобы удаление дошло до других устройств. */
    val deleted: StateFlow<Map<String, Long>>

    init {
        val (loadedNotes, loadedDeleted) = load()
        _notes = MutableStateFlow(loadedNotes)
        notes = _notes.asStateFlow()
        _deleted = MutableStateFlow(loadedDeleted)
        deleted = _deleted.asStateFlow()
    }
    private val _saving = MutableStateFlow(false)

    /** true, пока последнее изменение ещё не записано в файл. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _writeFailed = MutableStateFlow(false)

    /** true, если последняя запись не удалась (нет места, файл недоступен). */
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    fun find(id: String?): Note? = id?.let { key -> _notes.value.firstOrNull { it.id == key } }

    fun create(content: String = ""): Note {
        val now = System.currentTimeMillis()
        val note = Note(id = newNoteId(), content = content, created = now)
        _notes.update { it + note }
        if (!note.isBlank) saveNow()
        return note
    }

    fun edit(id: String, title: String, content: String) {
        _notes.update { list ->
            list.map {
                if (it.id == id) {
                    val now = System.currentTimeMillis()
                    it.copy(title = title, content = content, updated = now, changed = now)
                } else it
            }
        }
        scheduleSave()
    }

    fun togglePin(id: String) {
        val now = System.currentTimeMillis()
        _notes.update { list -> list.map { if (it.id == id) it.copy(pinned = !it.pinned, changed = now) else it } }
        saveNow()
    }

    fun delete(id: String): Note? {
        val note = find(id) ?: return null
        _notes.update { list -> list.filterNot { it.id == id } }
        markDeleted(listOf(note))
        saveNow()
        return note
    }

    fun restore(note: Note) = restoreAll(listOf(note))

    /** Удаляет сразу несколько заметок одной записью файла; возвращает удалённые — для «Отменить». */
    fun deleteAll(ids: Collection<String>): List<Note> {
        val set = ids.toSet()
        val removed = _notes.value.filter { it.id in set }
        if (removed.isEmpty()) return removed
        _notes.update { list -> list.filterNot { it.id in set } }
        markDeleted(removed)
        saveNow()
        return removed
    }

    /** «Отменить» удаление: заметка возвращается и считается изменённой сейчас — чтобы вернулась и в облаке. */
    fun restoreAll(notes: List<Note>) {
        if (notes.isEmpty()) return
        val now = System.currentTimeMillis()
        val restored = notes.map { it.copy(changed = now) }
        val ids = restored.mapTo(HashSet()) { it.id }
        _notes.update { list -> list.filterNot { it.id in ids } + restored }
        _deleted.update { it - ids }
        saveNow()
    }

    /** Закрывая заметку: пустую выбрасываем. */
    fun discardIfBlank(id: String?) {
        val note = find(id) ?: return
        if (note.isBlank) {
            _notes.update { list -> list.filterNot { it.id == note.id } }
            // Заметку, которая была с текстом и опустела, удаляем и на других устройствах.
            if (note.changed != note.created) markDeleted(listOf(note))
            saveNow()
        }
    }

    private fun markDeleted(notes: List<Note>) {
        val real = notes.filterNot { it.isBlank && it.changed == it.created }
        if (real.isEmpty()) return
        val now = System.currentTimeMillis()
        _deleted.update { map -> map + real.associate { it.id to now } }
    }

    /**
     * Применяет результат синхронизации. Каждое изменение применяется, только если заметка
     * не менялась здесь с момента, когда синхронизация её прочитала: правки во время обмена
     * с облаком не теряются, а уйдут при следующей синхронизации.
     *
     * [incoming] — заметки из облака, [removed] — удалённые в облаке (id → время удаления),
     * [added] — новые заметки (копии при конфликте), [seen] — время изменения каждой заметки
     * на момент чтения (null — заметки здесь не было).
     */
    fun applySync(
        incoming: List<Note>,
        removed: Map<String, Long>,
        added: List<Note>,
        seen: Map<String, Long?>,
    ) {
        if (incoming.isEmpty() && removed.isEmpty() && added.isEmpty()) return
        var changed = false
        _notes.update { list ->
            val byId = LinkedHashMap<String, Note>()
            list.forEach { byId[it.id] = it }
            fun untouched(id: String) = byId[id]?.changed == seen[id]
            incoming.forEach { n ->
                if (untouched(n.id)) {
                    byId[n.id] = n
                    changed = true
                }
            }
            removed.keys.forEach { id ->
                if (untouched(id) && byId.remove(id) != null) changed = true
            }
            added.forEach { n ->
                if (n.id !in byId) {
                    byId[n.id] = n
                    changed = true
                }
            }
            byId.values.toList()
        }
        _deleted.update { map ->
            val next = map.toMutableMap()
            incoming.forEach { next.remove(it.id) }
            removed.forEach { (id, at) -> if (_notes.value.none { it.id == id }) next[id] = maxOf(at, next[id] ?: 0L) }
            next
        }
        if (changed || removed.isNotEmpty()) saveNow()
    }

    /** Забывает старые следы удалений: к этому времени их уже получили все устройства. */
    fun forgetDeleted(ids: Collection<String>) {
        if (ids.isEmpty()) return
        _deleted.update { it - ids.toSet() }
        saveNow()
    }

    /** Возвращает число добавленных заметок или null, если файл не подошёл. */
    fun importJson(text: String): Int? = NotesJson.decode(text)?.let { importNotes(it) }

    /**
     * Добавляет заметки, которых ещё нет: заметка с тем же заголовком и текстом пропускается,
     * поэтому повторный импорт того же файла ничего не дублирует.
     */
    fun importNotes(incoming: List<Note>): Int {
        var added = 0
        _notes.update { current ->
            added = 0
            val ids = current.mapTo(HashSet()) { it.id }
            val texts = current.mapTo(HashSet()) { it.title.trim() to it.content.trim() }
            val result = current.toMutableList()
            val now = System.currentTimeMillis()
            incoming.filterNot { it.isBlank }.forEach { n ->
                if (!texts.add(n.title.trim() to n.content.trim())) return@forEach
                val note = if (!ids.add(n.id)) n.copy(id = newNoteId().also { ids.add(it) }) else n
                result += note.copy(changed = now)
                added++
            }
            result
        }
        if (added > 0) {
            val ids = _notes.value.mapTo(HashSet()) { it.id }
            _deleted.update { map -> map.filterKeys { it !in ids } }
            saveNow()
        }
        return added
    }

    fun exportJson(): String = NotesJson.encode(_notes.value.filterNot { it.isBlank })

    /** Сохранить немедленно и синхронно — при уходе приложения в фон. */
    fun flush() {
        pendingSave?.cancel()
        write()
    }

    /** Для инструментальных тестов: начать с пустого списка. */
    fun resetForTests() {
        pendingSave?.cancel()
        _notes.value = emptyList()
        _deleted.value = emptyMap()
        write()
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        _saving.value = true
        pendingSave = scope.launch {
            delay(SAVE_DELAY_MS)
            write()
        }
    }

    private fun saveNow() {
        pendingSave?.cancel()
        _saving.value = true
        pendingSave = scope.launch { write() }
    }

    private fun load(): Pair<List<Note>, Map<String, Long>> {
        val empty = emptyList<Note>() to emptyMap<String, Long>()
        val bytes = try {
            file.readFully()
        } catch (e: FileNotFoundException) {
            return empty
        } catch (e: IOException) {
            setAsideDamaged()
            return empty
        }
        if (bytes.isEmpty()) return empty
        val text = bytes.toString(Charsets.UTF_8)
        val notes = NotesJson.decode(text)
        if (notes == null) {
            setAsideDamaged()
            return empty
        }
        return notes to NotesJson.decodeDeleted(text)
    }

    private fun setAsideDamaged() {
        val base = file.baseFile
        val target = File(base.parentFile, "notes-damaged-${System.currentTimeMillis()}.json")
        if (base.renameTo(target)) _damaged.value = target else writesBlocked = true
    }

    @Synchronized
    private fun write() {
        val ok = !writesBlocked && try {
            val json = NotesJson.encode(_notes.value.filterNot { it.isBlank }, _deleted.value)
            val out = file.startWrite()
            try {
                out.write(json.toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
                true
            } catch (e: IOException) {
                file.failWrite(out)
                false
            }
        } catch (e: IOException) {
            false
        }
        _writeFailed.value = !ok
        _saving.value = false
    }

    companion object {
        private const val SAVE_DELAY_MS = 400L

        @Volatile
        private var instance: NotesRepository? = null

        fun get(context: Context): NotesRepository =
            instance ?: synchronized(this) {
                instance ?: NotesRepository(File(context.applicationContext.filesDir, "notes.json")).also { instance = it }
            }
    }
}
