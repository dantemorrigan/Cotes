package io.github.dantemorrigan.cotes.sync

import io.github.dantemorrigan.cotes.Note

/** Файл заметки в облаке: [changed] — время изменения, [deleted] — заметку удалили. */
data class RemoteEntry(
    val noteId: String,
    val fileId: String,
    val changed: Long,
    val deleted: Boolean,
)

/** Одна и та же заметка изменилась и здесь, и в облаке: после сравнения текста победит более новая. */
data class Conflict(val local: Note, val remote: RemoteEntry)

/**
 * Что сделать за одну синхронизацию.
 * [base] — время изменения, на котором устройство и облако сошлись в прошлый раз (id → changed).
 */
data class SyncPlan(
    val upload: List<Pair<Note, RemoteEntry?>> = emptyList(),
    val uploadDeleted: List<Pair<Pair<String, Long>, RemoteEntry?>> = emptyList(),
    val download: List<RemoteEntry> = emptyList(),
    val applyDeleted: Map<String, Long> = emptyMap(),
    val conflicts: List<Conflict> = emptyList(),
    val duplicates: List<RemoteEntry> = emptyList(),
    val inSync: Map<String, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = upload.isEmpty() && uploadDeleted.isEmpty() && download.isEmpty() &&
            applyDeleted.isEmpty() && conflicts.isEmpty()
}

/**
 * Слияние заметок устройства и облака. Чистая функция: сеть и файлы — в [SyncManager].
 *
 * Правило: побеждает более позднее изменение (правка, закрепление или удаление).
 * Если заметку меняли и здесь, и в облаке с прошлой синхронизации — это конфликт:
 * при разном тексте сохраняются обе версии.
 */
object SyncMerge {
    fun plan(
        local: List<Note>,
        deleted: Map<String, Long>,
        remote: List<RemoteEntry>,
        base: Map<String, Long>,
    ): SyncPlan {
        val localById = local.filterNot { it.isBlank }.associateBy { it.id }
        // Одна заметка могла попасть в облако дважды (два устройства одновременно) — берём новейший файл.
        val remoteById = HashMap<String, RemoteEntry>()
        val duplicates = ArrayList<RemoteEntry>()
        remote.forEach { e ->
            val prev = remoteById[e.noteId]
            if (prev == null) {
                remoteById[e.noteId] = e
            } else if (e.changed > prev.changed) {
                remoteById[e.noteId] = e
                duplicates += prev
            } else {
                duplicates += e
            }
        }

        val upload = ArrayList<Pair<Note, RemoteEntry?>>()
        val uploadDeleted = ArrayList<Pair<Pair<String, Long>, RemoteEntry?>>()
        val download = ArrayList<RemoteEntry>()
        val applyDeleted = HashMap<String, Long>()
        val conflicts = ArrayList<Conflict>()
        val inSync = HashMap<String, Long>()

        val ids = LinkedHashSet<String>().apply {
            addAll(localById.keys)
            addAll(deleted.keys)
            addAll(remoteById.keys)
        }
        for (id in ids) {
            val note = localById[id]
            val localTime = note?.changed ?: deleted[id]
            val r = remoteById[id]
            when {
                r == null -> when {
                    note != null -> upload += note to null
                    localTime != null -> uploadDeleted += (id to localTime) to null
                }
                localTime == null -> if (!r.deleted) download += r
                localTime == r.changed -> inSync[id] = localTime
                else -> {
                    val since = base[id] ?: Long.MIN_VALUE
                    val bothEdited = note != null && !r.deleted && localTime > since && r.changed > since
                    when {
                        bothEdited -> conflicts += Conflict(note!!, r)
                        localTime > r.changed ->
                            if (note != null) upload += note to r else uploadDeleted += (id to localTime) to r
                        r.deleted -> applyDeleted[id] = r.changed
                        else -> download += r
                    }
                }
            }
        }
        return SyncPlan(upload, uploadDeleted, download, applyDeleted, conflicts, duplicates, inSync)
    }

    /** Есть ли здесь изменения, которых облако ещё не видело. */
    fun hasLocalChanges(local: List<Note>, deleted: Map<String, Long>, base: Map<String, Long>): Boolean =
        local.any { !it.isBlank && base[it.id] != it.changed } || deleted.any { (id, at) -> base[id] != at }

    fun sameText(a: Note, b: Note): Boolean =
        a.title == b.title && a.content == b.content && a.pinned == b.pinned
}
