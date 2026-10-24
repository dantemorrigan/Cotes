package io.github.dantemorrigan.cotes

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream

/** Откуда пришли заметки — для сообщения после импорта. */
enum class ImportSource { Cotes, Simplenote, GoogleKeep, StandardNotes, Evernote, Json, Text }

data class ImportResult(val notes: List<Note>, val sources: Set<ImportSource>) {
    operator fun plus(other: ImportResult) = ImportResult(notes + other.notes, sources + other.sources)

    companion object {
        val Empty = ImportResult(emptyList(), emptySet())
    }
}

/**
 * Импорт заметок из других приложений. Понимает:
 * - резервную копию Cotes;
 * - Simplenote: notes.json ({activeNotes, trashedNotes}) или весь zip из «Export notes»;
 * - Google Keep из Google Takeout: отдельные .json каждой заметки или весь zip;
 * - Standard Notes: расшифрованная резервная копия ({items: [...]}), даже если файл назван .txt;
 * - Evernote: файлы .enex;
 * - JSON других приложений: массив заметок с полями title/content/text/body;
 * - текст и Markdown (.txt, .md) — по файлу на заметку, в том числе в zip (Obsidian, Joplin, Bear, Markor).
 * Заметки из корзины не импортируются. Теги и метки дописываются в конец заметки как #тег.
 */
object NoteImport {
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024
    private const val MAX_ZIP_BYTES = 200L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 20_000

    /** Один выбранный файл: zip, JSON или текст. [modified] — время изменения файла, если известно. */
    fun fromFile(name: String, bytes: ByteArray, modified: Long? = null): ImportResult {
        if (isZip(bytes)) return fromZip(bytes.inputStream())
        if (name.endsWith(".enex", ignoreCase = true)) return fromEnex(bytes)
        // Сначала по содержимому: копия Standard Notes — это JSON в файле .txt.
        if (looksLikeJson(bytes)) {
            fromJson(bytes.toString(Charsets.UTF_8))?.let { (source, notes) -> return ImportResult(notes, setOf(source)) }
        }
        if (isTextName(name) || name.substringAfterLast('/').indexOf('.') < 0) {
            fromText(name, bytes.toString(Charsets.UTF_8), modified)
                ?.let { return ImportResult(listOf(it), setOf(ImportSource.Text)) }
        }
        return ImportResult.Empty
    }

    /** Evernote .enex. Пустой результат — если это не экспорт Evernote. */
    fun fromEnex(bytes: ByteArray): ImportResult {
        val notes = runCatching { Enex.parse(bytes.inputStream()) }.getOrNull().orEmpty().filterNot { it.isBlank }
        return if (notes.isEmpty()) ImportResult.Empty else ImportResult(notes, setOf(ImportSource.Evernote))
    }

    /**
     * Архив целиком. Если в нём есть заметки в JSON (Simplenote, Google Keep), текстовые
     * файлы рядом — это их копии, и они пропускаются.
     */
    fun fromZip(input: InputStream): ImportResult {
        var fromJson = ImportResult.Empty
        val texts = ArrayList<Note>()
        var total = 0L
        var entries = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ZIP_ENTRIES) break
                val path = entry.name.replace('\\', '/')
                if (entry.isDirectory || skipPath(path)) continue
                val json = path.endsWith(".json", ignoreCase = true)
                val enex = path.endsWith(".enex", ignoreCase = true)
                if (!json && !enex && !isTextName(path)) continue
                val bytes = readLimited(zip, MAX_FILE_BYTES) ?: continue
                total += bytes.size
                if (total > MAX_ZIP_BYTES) break
                val modified = entry.time.takeIf { it > 0 }
                val parsed = when {
                    enex -> fromEnex(bytes).takeIf { it.notes.isNotEmpty() }
                    json || looksLikeJson(bytes) -> fromJson(bytes.toString(Charsets.UTF_8))
                        ?.let { (source, notes) -> ImportResult(notes, setOf(source)) }
                    else -> null
                }
                if (parsed != null) {
                    fromJson += parsed
                } else if (!json && !enex) {
                    fromText(path, bytes.toString(Charsets.UTF_8), modified)?.let { texts += it }
                }
            }
        }
        if (fromJson.notes.isNotEmpty()) return fromJson
        return if (texts.isEmpty()) ImportResult.Empty else ImportResult(texts, setOf(ImportSource.Text))
    }

    /** JSON любого из поддерживаемых форматов; null — если заметок в нём нет. */
    fun fromJson(text: String): Pair<ImportSource, List<Note>>? {
        val root: Any = try {
            val t = text.trim().removePrefix("﻿")
            when {
                t.startsWith("{") -> JSONObject(t)
                t.startsWith("[") -> JSONArray(t)
                else -> return null
            }
        } catch (e: Exception) {
            return null
        }
        val result = when {
            root is JSONObject && root.has("activeNotes") -> ImportSource.Simplenote to simplenote(root)
            root is JSONObject && root.optJSONArray("items") != null && hasContentType(root.getJSONArray("items")) ->
                ImportSource.StandardNotes to standardNotes(root.getJSONArray("items"))
            root is JSONObject && isKeepNote(root) -> ImportSource.GoogleKeep to listOfNotNull(keep(root))
            root is JSONObject && root.optString("app") == "Cotes" -> ImportSource.Cotes to NotesJson.decode(text).orEmpty()
            root is JSONArray && root.length() > 0 && (0 until root.length()).all { isKeepNote(root.optJSONObject(it)) } ->
                ImportSource.GoogleKeep to (0 until root.length()).mapNotNull { keep(root.getJSONObject(it)) }
            else -> generic(root)?.let { ImportSource.Json to it }
        } ?: return null
        val notes = result.second.filterNot { it.isBlank }
        return if (notes.isEmpty()) null else result.first to uniqueIds(notes)
    }

    /**
     * Текстовый файл — одна заметка. Заголовок: «# Заголовок» в первой строке, первая строка,
     * если имя файла с неё начинается (так сохраняют Simplenote и многие другие), иначе имя файла.
     */
    fun fromText(path: String, raw: String, modified: Long? = null): Note? {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val stem = path.substringAfterLast('/').substringBeforeLast('.').trim()
        val body = stripFrontMatter(text).trim('\n')
        if (body.isBlank()) return null
        val firstLine = body.substringBefore('\n').trim()
        val rest = body.substringAfter('\n', "").trimStart('\n')
        val (title, content) = when {
            firstLine.startsWith("# ") -> firstLine.removePrefix("# ").trim() to rest
            firstLine.isNotEmpty() && stem.isNotEmpty() && firstLine.startsWith(stem.removeSuffix("…")) -> firstLine to rest
            else -> stem to body
        }
        if (title.isBlank() && content.isBlank()) return null
        val ts = modified ?: System.currentTimeMillis()
        return Note(id = newNoteId(), title = title, content = content, created = ts, updated = ts)
    }

    // --- Simplenote ---

    private fun simplenote(root: JSONObject): List<Note> {
        val arr = root.optJSONArray("activeNotes") ?: return emptyList()
        return objects(arr).map { o ->
            val (title, content) = splitFirstLine(o.optString("content"))
            val created = time(o.opt("creationDate")) ?: System.currentTimeMillis()
            Note(
                id = newNoteId(),
                title = title,
                content = withTags(content, strings(o.optJSONArray("tags"))),
                created = created,
                updated = time(o.opt("lastModified")) ?: created,
                pinned = o.optBoolean("pinned", false),
            )
        }
    }

    // --- Google Keep (Takeout) ---

    private fun isKeepNote(o: JSONObject?): Boolean =
        o != null && (o.has("textContent") || o.has("listContent")) &&
            (o.has("userEditedTimestampUsec") || o.has("isTrashed") || o.has("isArchived"))

    private fun keep(o: JSONObject): Note? {
        if (o.optBoolean("isTrashed", false)) return null
        val list = o.optJSONArray("listContent")
        val body = if (list != null && list.length() > 0) {
            objects(list).joinToString("\n") { item ->
                (if (item.optBoolean("isChecked", false)) "☑ " else "☐ ") + item.optString("text")
            }
        } else {
            o.optString("textContent")
        }
        val labels = o.optJSONArray("labels")?.let { arr -> objects(arr).map { it.optString("name") } }.orEmpty()
        val updated = time(o.opt("userEditedTimestampUsec")) ?: System.currentTimeMillis()
        return Note(
            id = newNoteId(),
            title = o.optString("title").trim(),
            content = withTags(body, labels),
            created = time(o.opt("createdTimestampUsec")) ?: updated,
            updated = updated,
            pinned = o.optBoolean("isPinned", false),
        )
    }

    // --- Standard Notes ---

    private fun hasContentType(items: JSONArray): Boolean =
        objects(items).any { it.has("content_type") }

    private fun standardNotes(items: JSONArray): List<Note> {
        val all = objects(items).filterNot { it.optBoolean("deleted", false) }
        val tagsByNote = HashMap<String, MutableList<String>>()
        all.filter { it.optString("content_type") == "Tag" }.forEach { tag ->
            val content = tag.optJSONObject("content") ?: return@forEach
            if (content.optBoolean("trashed", false)) return@forEach
            val name = content.optString("title").trim()
            if (name.isEmpty()) return@forEach
            content.optJSONArray("references")?.let { refs ->
                objects(refs).forEach { ref -> tagsByNote.getOrPut(ref.optString("uuid")) { ArrayList() } += name }
            }
        }
        return all.filter { it.optString("content_type") == "Note" }.mapNotNull { item ->
            val content = item.optJSONObject("content") ?: return@mapNotNull null
            if (content.optBoolean("trashed", false)) return@mapNotNull null
            val pinned = content.optJSONObject("appData")?.optJSONObject("org.standardnotes.sn")?.optBoolean("pinned", false)
                ?: content.optBoolean("pinned", false)
            val created = time(item.opt("created_at")) ?: System.currentTimeMillis()
            Note(
                id = newNoteId(),
                title = content.optString("title").trim(),
                content = withTags(content.optString("text"), tagsByNote[item.optString("uuid")].orEmpty()),
                created = created,
                updated = time(item.opt("updated_at")) ?: created,
                pinned = pinned,
            )
        }
    }

    // --- Прочие JSON: массив заметок или объект с массивом notes/items/data ---

    private val titleKeys = listOf("title", "name", "subject", "heading")
    private val bodyKeys = listOf("content", "text", "body", "note", "markdown", "textContent")
    private val createdKeys = listOf("created", "created_at", "createdAt", "creationDate", "created_time", "createdTime", "dateCreated", "date")
    private val updatedKeys = listOf("updated", "updated_at", "updatedAt", "lastModified", "modified", "modified_at", "updated_time", "modificationDate", "dateModified")
    private val trashKeys = listOf("deleted", "trashed", "isTrashed", "is_deleted", "in_trash")

    private fun generic(root: Any): List<Note>? {
        val arr = when (root) {
            is JSONArray -> root
            is JSONObject -> listOf("notes", "items", "data", "entries").firstNotNullOfOrNull { root.optJSONArray(it) }
                ?: return null
            else -> return null
        }
        val notes = objects(arr).mapNotNull { o ->
            val bodyValue = bodyKeys.firstNotNullOfOrNull { k -> o.opt(k)?.takeIf { it is String } as String? }
            val title = titleKeys.firstNotNullOfOrNull { k -> o.opt(k)?.takeIf { it is String } as String? }
            if (bodyValue == null && title == null) return@mapNotNull null
            if (trashKeys.any { o.optBoolean(it, false) }) return@mapNotNull null
            val created = createdKeys.firstNotNullOfOrNull { time(o.opt(it)) } ?: System.currentTimeMillis()
            Note(
                id = newNoteId(),
                title = title.orEmpty().trim(),
                content = bodyValue.orEmpty(),
                created = created,
                updated = updatedKeys.firstNotNullOfOrNull { time(o.opt(it)) } ?: created,
                pinned = o.optBoolean("pinned", false) || o.optBoolean("isPinned", false),
            )
        }
        return notes.ifEmpty { null }
    }

    // --- Общее ---

    /**
     * Время из строки ISO 8601 или числа: секунды, миллисекунды или микросекунды
     * (Google Keep) — различаем по величине.
     */
    internal fun time(value: Any?): Long? {
        val ms = when (value) {
            is Number -> scale(value.toDouble())
            is String -> {
                val s = value.trim()
                if (s.isEmpty()) return null
                s.toDoubleOrNull()?.let { scale(it) }
                    ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
                    ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                    ?: runCatching { OffsetDateTime.parse(s.replace(' ', 'T')).toInstant().toEpochMilli() }.getOrNull()
            }
            else -> null
        }
        return ms?.takeIf { it > 0 }
    }

    private fun scale(v: Double): Long = when {
        v > 1e17 -> (v / 1_000_000).toLong() // наносекунды
        v > 1e14 -> (v / 1_000).toLong() // микросекунды
        v > 1e11 -> v.toLong() // миллисекунды
        else -> (v * 1000).toLong() // секунды
    }

    private fun splitFirstLine(raw: String): Pair<String, String> {
        val text = raw.replace("\r\n", "\n").trimStart('\n', ' ')
        val first = text.substringBefore('\n').trim()
        val rest = text.substringAfter('\n', "").trimStart('\n')
        return first to rest
    }

    private fun withTags(content: String, tags: List<String>): String {
        val clean = tags.map { it.trim().replace(Regex("\\s+"), "_") }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return content
        val line = clean.joinToString(" ") { "#$it" }
        return if (content.isBlank()) line else content.trimEnd() + "\n\n" + line
    }

    private fun stripFrontMatter(text: String): String {
        if (!text.startsWith("---\n")) return text
        val end = text.indexOf("\n---\n", 4)
        return if (end < 0) text else text.substring(end + 5)
    }

    private fun objects(arr: JSONArray): List<JSONObject> = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.opt(it) as? String }

    private fun uniqueIds(notes: List<Note>): List<Note> {
        val seen = HashSet<String>()
        return notes.map { if (seen.add(it.id)) it else it.copy(id = newNoteId().also { id -> seen.add(id) }) }
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2].toInt() == 3 && bytes[3].toInt() == 4

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val head = bytes.take(64).toByteArray().toString(Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
        return head.startsWith("{") || head.startsWith("[")
    }

    private fun isTextName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("txt", "md", "markdown", "text")
    }

    /** Корзина Simplenote, служебные папки macOS и скрытые файлы. */
    private fun skipPath(path: String): Boolean {
        val parts = path.split('/')
        return parts.any { it == "__MACOSX" || it.startsWith(".") } || parts.first() == "trash" ||
            parts.dropLast(1).any { it.equals("trash", ignoreCase = true) }
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
