package io.github.dantemorrigan.cotes

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Экспорт Evernote (.enex): <en-export><note><title/><content>(ENML в CDATA)</content>
 * <created/><updated/><tag/>…</note></en-export>. Внешние DTD не загружаются.
 */
internal object Enex {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun parse(input: InputStream): List<Note> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val notes = ArrayList<Note>()
        var title = ""
        var content = ""
        var created: Long? = null
        var updated: Long? = null
        val tags = ArrayList<String>()
        var field: String? = null
        val text = StringBuilder()
        var inNote = false
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> return notes
                XmlPullParser.START_TAG -> when (parser.name) {
                    "note" -> {
                        inNote = true
                        title = ""; content = ""; created = null; updated = null; tags.clear()
                    }
                    "title", "content", "created", "updated", "tag" -> if (inNote) {
                        field = parser.name
                        text.setLength(0)
                    }
                    // Вложения, метаданные и прочее — пропускаем целиком.
                    "resource", "note-attributes" -> if (inNote) skip(parser)
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (field != null) text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "note" -> {
                        inNote = false
                        val ts = updated ?: created ?: System.currentTimeMillis()
                        val body = Html.toText(content)
                        notes += Note(
                            id = newNoteId(),
                            title = title.trim(),
                            content = if (tags.isEmpty()) body
                            else (if (body.isBlank()) "" else body.trimEnd() + "\n\n") +
                                tags.joinToString(" ") { "#" + it.trim().replace(Regex("\\s+"), "_") },
                            created = created ?: ts,
                            updated = ts,
                        )
                    }
                    field -> {
                        val value = text.toString()
                        when (field) {
                            "title" -> title = value
                            "content" -> content = value
                            "created" -> created = date(value)
                            "updated" -> updated = date(value)
                            "tag" -> if (value.isNotBlank()) tags += value
                        }
                        field = null
                    }
                }
            }
        }
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun date(value: String): Long? =
        runCatching { LocalDateTime.parse(value.trim(), dateFmt).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: NoteImport.time(value)
}

/** Упрощённый перевод HTML/ENML в текст: абзацы и переносы, списки, чекбоксы, сущности. */
internal object Html {
    private val blockEnd = Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote|pre|en-note)>")
    private val br = Regex("(?i)<br\\s*/?>")
    private val li = Regex("(?i)<li[^>]*>")
    private val todo = Regex("(?i)<en-todo([^>]*)/?>(</en-todo>)?")
    private val tag = Regex("<[^>]+>")
    private val numeric = Regex("&#(x?)([0-9a-fA-F]+);")
    private val named = mapOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&#39;" to "'")

    fun toText(html: String): String {
        var s = html.replace(Regex("(?is)<\\?xml.*?\\?>|<!DOCTYPE.*?>|<head.*?</head>|<style.*?</style>|<script.*?</script>"), "")
        s = s.replace(Regex("[\r\n]+"), " ")
        s = todo.replace(s) { m -> if (m.groupValues[1].contains("checked=\"true\"", ignoreCase = true)) "☑ " else "☐ " }
        s = br.replace(s, "\n")
        s = li.replace(s, "• ")
        s = blockEnd.replace(s, "\n")
        s = tag.replace(s, "")
        named.forEach { (k, v) -> s = s.replace(k, v) }
        s = numeric.replace(s) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        s = s.replace("&amp;", "&")
        return s.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
