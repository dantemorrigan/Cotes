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
) {
    val isBlank: Boolean get() = title.isBlank() && content.isBlank()

    /** Заголовок, а если его нет — первая непустая строка текста. */
    val displayTitle: String
        get() = title.trim().ifEmpty {
            content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        }
}

fun newNoteId(): String =
    System.currentTimeMillis().toString(36) + UUID.randomUUID().toString().take(6)

/**
 * Формат файла заметок и резервной копии:
 * { app, version, exported, notes: [{ id, title, content, created, updated, pinned }] }.
 * Читается и голый массив заметок (старые копии).
 */
object NotesJson {
    fun encode(notes: List<Note>): String {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("content", n.content)
                    .put("created", n.created)
                    .put("updated", n.updated)
                    .put("pinned", n.pinned)
            )
        }
        return JSONObject()
            .put("app", "Cotes")
            .put("version", 1)
            .put("exported", Instant.now().toString())
            .put("notes", arr)
            .toString(2)
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
            out += Note(
                id = id,
                title = o.opt("title") as? String ?: "",
                content = o.opt("content") as? String ?: "",
                created = created,
                updated = o.optLong("updated", 0L).takeIf { it > 0 } ?: created,
                pinned = o.optBoolean("pinned", false),
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

    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
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
                if (it.id == id) it.copy(title = title, content = content, updated = System.currentTimeMillis()) else it
            }
        }
        scheduleSave()
    }

    fun togglePin(id: String) {
        _notes.update { list -> list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
        saveNow()
    }

    fun delete(id: String): Note? {
        val note = find(id) ?: return null
        _notes.update { list -> list.filterNot { it.id == id } }
        saveNow()
        return note
    }

    fun restore(note: Note) {
        _notes.update { list -> list.filterNot { it.id == note.id } + note }
        saveNow()
    }

    /** Закрывая заметку: пустую выбрасываем. */
    fun discardIfBlank(id: String?) {
        val note = find(id) ?: return
        if (note.isBlank) {
            _notes.update { list -> list.filterNot { it.id == note.id } }
            saveNow()
        }
    }

    /** Возвращает число добавленных заметок или null, если файл не подошёл. */
    fun importJson(text: String): Int? {
        val incoming = NotesJson.decode(text) ?: return null
        var added = 0
        _notes.update { current ->
            val byId = current.associateBy { it.id }.toMutableMap()
            val result = current.toMutableList()
            incoming.filterNot { it.isBlank }.forEach { n ->
                val same = byId[n.id]
                if (same != null && same.title == n.title && same.content == n.content) return@forEach
                val note = if (same != null) n.copy(id = newNoteId()) else n
                byId[note.id] = note
                result += note
                added++
            }
            result
        }
        if (added > 0) saveNow()
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

    private fun load(): List<Note> {
        val bytes = try {
            file.readFully()
        } catch (e: FileNotFoundException) {
            return emptyList()
        } catch (e: IOException) {
            setAsideDamaged()
            return emptyList()
        }
        if (bytes.isEmpty()) return emptyList()
        val notes = NotesJson.decode(bytes.toString(Charsets.UTF_8))
        if (notes == null) setAsideDamaged()
        return notes.orEmpty()
    }

    private fun setAsideDamaged() {
        val base = file.baseFile
        val target = File(base.parentFile, "notes-damaged-${System.currentTimeMillis()}.json")
        if (base.renameTo(target)) _damaged.value = target else writesBlocked = true
    }

    @Synchronized
    private fun write() {
        val ok = !writesBlocked && try {
            val json = NotesJson.encode(_notes.value.filterNot { it.isBlank })
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
