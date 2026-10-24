package io.github.dantemorrigan.cotes

/**
 * Лёгкий Markdown для заметок. Текст заметки всегда хранится как есть — эти функции
 * только помогают редактировать (списки, чекбоксы) и показывать превью без значков разметки.
 */
object Markdown {
    private val listItem = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s(\[[ xX]\]\s)?""")
    private val checkbox = Regex("""^(\s*[-*+]\s)\[([ xX])\]""", RegexOption.MULTILINE)
    private val mdLink = Regex("""\[([^\]\n]+)\]\(([^)\s]+)\)""")
    private val url = Regex("""https?://[^\s)\]>"]+""")

    /**
     * Нажали Enter в пункте списка: следующая строка начинается с такого же маркера
     * («- », «2. », «- [ ] »). Enter на пустом пункте заканчивает список.
     * [before] и [after] — текст до и после правки, [cursor] — курсор после неё.
     * Возвращает новый текст и курсор или null, если это не тот случай.
     */
    fun continueList(before: String, after: String, cursor: Int): Pair<String, Int>? {
        if (after.length != before.length + 1 || cursor !in 1..after.length || after[cursor - 1] != '\n') return null
        if (after.removeRange(cursor - 1, cursor) != before) return null
        val lineStart = after.lastIndexOf('\n', cursor - 2) + 1
        val line = after.substring(lineStart, cursor - 1)
        val m = listItem.find(line) ?: return null
        if (line.substring(m.range.last + 1).isBlank()) {
            // Пустой пункт: убираем маркер вместе с новым переводом строки — список закончен.
            return after.removeRange(lineStart, cursor) to lineStart
        }
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val number = marker.dropLast(1).toIntOrNull()
        val next = if (number != null) "${number + 1}${marker.last()}" else marker
        val prefix = indent + next + " " + if (m.groupValues[3].isNotEmpty()) "[ ] " else ""
        return after.substring(0, cursor) + prefix + after.substring(cursor) to cursor + prefix.length
    }

    /** Позиция символа внутри «[ ]» / «[x]», если курсор поставили в чекбокс; иначе null. */
    fun checkboxAt(text: String, offset: Int): Int? {
        for (m in checkbox.findAll(text)) {
            val inner = m.range.first + m.groupValues[1].length + 1
            if (offset == inner || offset == inner + 1) return inner
            if (m.range.first > offset) break
        }
        return null
    }

    fun toggleCheckbox(text: String, inner: Int): String =
        text.replaceRange(inner, inner + 1, if (text[inner] == ' ') "x" else " ")

    /** Адрес ссылки под курсором: обычный URL или [текст](адрес). */
    fun linkAt(text: String, offset: Int): String? {
        mdLink.findAll(text).forEach { if (offset in it.range.first..it.range.last + 1) return it.groupValues[2] }
        url.findAll(text).forEach { if (offset in it.range.first..it.range.last + 1) return it.value }
        return null
    }

    /** Строка без значков разметки — для заголовка в списке и превью. */
    fun plainLine(line: String): String {
        var s = line.trim()
        s = s.replace(Regex("""^#{1,6}\s+"""), "")
        s = s.replace(Regex("""^(>\s?)+(\[![^\]]*\]\s*)?"""), "")
        s = s.replace(Regex("""^[-*+]\s\[[xX]\]\s"""), "☑ ")
        s = s.replace(Regex("""^[-*+]\s\[ \]\s"""), "☐ ")
        s = s.replace(Regex("""^[-*+]\s(?=\S)"""), "• ")
        if (Regex("""^(```|~~~|---+|\*\*\*+|___+)\s*\S*$""").matches(s)) return ""
        return inline(s)
    }

    /** Весь текст без разметки: строки по отдельности, пустые после очистки убираются. */
    fun plain(text: String): String =
        text.lineSequence().map { plainLine(it) }.filter { it.isNotEmpty() }.joinToString("\n")

    private fun inline(s: String): String = s
        .replace(Regex("""!?\[\[([^\]|\n]+)\|([^\]\n]+)\]\]"""), "$2")
        .replace(Regex("""!?\[\[([^\]\n]+)\]\]"""), "$1")
        .replace(mdLink, "$1")
        .replace(Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1"""), "$2")
        .replace(Regex("""~~(?=\S)(.+?)(?<=\S)~~"""), "$1")
        .replace(Regex("""==(?=\S)(.+?)(?<=\S)=="""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""(?<![*\p{L}\p{N}])\*(?=\S)([^*]+?)(?<=\S)\*(?![*\p{L}\p{N}])"""), "$1")
        .replace(Regex("""(?<![_\p{L}\p{N}])_(?=\S)([^_]+?)(?<=\S)_(?![_\p{L}\p{N}])"""), "$1")
}
