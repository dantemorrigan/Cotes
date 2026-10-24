package io.github.dantemorrigan.cotes

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Экспорт для других приложений: zip, по файлу .md на заметку. Заголовок — первой строкой
 * «# …», так файлы понимают Obsidian, Joplin, Bear, Simplenote и обратный импорт в Cotes.
 */
object NoteExport {
    private val unsafe = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun markdownZip(notes: List<Note>, out: OutputStream) {
        val used = HashSet<String>()
        ZipOutputStream(out).use { zip ->
            notes.filterNot { it.isBlank }.sortedByDescending { it.updated }.forEach { note ->
                val base = note.displayTitle.replace(unsafe, "_").trim().trim('.').take(60).ifEmpty { "Note" }
                var name = "$base.md"
                var n = 2
                while (!used.add(name.lowercase())) name = "$base (${n++}).md"
                zip.putNextEntry(ZipEntry(name).apply { time = note.updated })
                zip.write(markdown(note).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    fun markdown(note: Note): String {
        val title = note.title.trim()
        return if (title.isEmpty()) note.content else "# $title\n\n${note.content}"
    }
}
