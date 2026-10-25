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

    // --- Панель форматирования ---

    /** Результат кнопки форматирования: новый текст и выделение. */
    data class Edit(val text: String, val start: Int, val end: Int)

    enum class Block { Bullet, Numbered, Task, Quote }

    private val anyPrefix = Regex("""^(\s*)(#{1,6}\s+|[-*+]\s\[[ xX]\]\s|[-*+]\s|\d{1,9}[.)]\s|>\s?)?""")
    private val headingPrefix = Regex("""^\s*(#{1,6})\s+""")

    /**
     * Обернуть выделение значками ([marker] — «**», «*», «~~», «`», «==»). Если выделение уже
     * обёрнуто — значки снимаются. Без выделения вставляет пару значков и ставит курсор между ними.
     */
    fun wrap(text: String, start: Int, end: Int, marker: String): Edit {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        val m = marker.length
        val before = if (s >= m) text.substring(s - m, s) else ""
        val after = if (e + m <= text.length) text.substring(e, e + m) else ""
        // Одиночная «*» рядом с «**» — это жирный, а не курсив: его не снимаем.
        val partOfLonger = m == 1 && (s - m - 1 >= 0 && text[s - m - 1] == marker[0] || e + m < text.length && text[e + m] == marker[0])
        if (before == marker && after == marker && !partOfLonger) {
            return Edit(text.removeRange(e, e + m).removeRange(s - m, s), s - m, e - m)
        }
        val selected = text.substring(s, e)
        if (selected.length >= 2 * m && selected.startsWith(marker) && selected.endsWith(marker)) {
            return Edit(text.replaceRange(s, e, selected.substring(m, selected.length - m)), s, e - 2 * m)
        }
        // Пробелы по краям выделения остаются снаружи: «** слово **» Markdown не понимает.
        var a = s
        var z = e
        while (a < z && text[a].isWhitespace()) a++
        while (z > a && text[z - 1].isWhitespace()) z--
        return Edit(text.substring(0, a) + marker + text.substring(a, z) + marker + text.substring(z), a + m, z + m)
    }

    /** Ссылка: выделенный текст становится [текстом](https://), адрес выделен — его можно сразу вставить. */
    fun link(text: String, start: Int, end: Int): Edit {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        val url = "https://"
        val out = text.substring(0, s) + "[" + text.substring(s, e) + "](" + url + ")" + text.substring(e)
        return if (s == e) Edit(out, s + 1, s + 1) else Edit(out, e + 3, e + 3 + url.length)
    }

    /** Заголовок по кругу: обычный текст → # → ## → ### → обычный текст. */
    fun heading(text: String, start: Int, end: Int): Edit {
        val (from, to) = lineRange(text, start, end)
        val level = headingPrefix.find(text.substring(from, to))?.groupValues?.get(1)?.length ?: 0
        val next = if (level >= 3) 0 else level + 1
        return mapLines(text, start, end) { _, line ->
            val m = anyPrefix.find(line)!!
            val body = line.substring(m.range.last + 1)
            if (next == 0) m.groupValues[1] + body else m.groupValues[1] + "#".repeat(next) + " " + body
        }
    }

    /** Список, нумерация, задача или цитата для строк выделения; повторное нажатие убирает. */
    fun block(text: String, start: Int, end: Int, kind: Block): Edit {
        val (from, to) = lineRange(text, start, end)
        val lines = text.substring(from, to).split('\n')
        val all = lines.filter { it.isNotBlank() }.ifEmpty { lines }.all { kindOf(it) == kind }
        var number = 0
        return mapLines(text, start, end) { _, line ->
            val m = anyPrefix.find(line)!!
            val indent = m.groupValues[1]
            val body = line.substring(m.range.last + 1)
            when {
                all -> indent + body
                line.isBlank() && lines.size > 1 -> line
                else -> indent + when (kind) {
                    Block.Bullet -> "- "
                    Block.Numbered -> "${++number}. "
                    Block.Task -> "- [ ] "
                    Block.Quote -> "> "
                } + body
            }
        }
    }

    private fun kindOf(line: String): Block? {
        val p = anyPrefix.find(line)?.groupValues?.get(2).orEmpty().trim()
        return when {
            p.startsWith("#") || p.isEmpty() -> null
            p.contains("[") -> Block.Task
            p == ">" -> Block.Quote
            p.first().isDigit() -> Block.Numbered
            else -> Block.Bullet
        }
    }

    private fun lineRange(text: String, start: Int, end: Int): Pair<Int, Int> {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        val from = text.lastIndexOf('\n', s - 1) + 1
        val to = text.indexOf('\n', e).let { if (it < 0) text.length else it }
        return from to to
    }

    /** Меняет строки выделения и сдвигает выделение вместе с текстом. */
    private fun mapLines(text: String, start: Int, end: Int, transform: (Int, String) -> String): Edit {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        val (from, to) = lineRange(text, s, e)
        val old = text.substring(from, to).split('\n')
        val new = old.mapIndexed(transform)
        val firstDelta = new.first().length - old.first().length
        val totalDelta = new.sumOf { it.length } - old.sumOf { it.length }
        val out = text.substring(0, from) + new.joinToString("\n") + text.substring(to)
        val newStart = (s + firstDelta).coerceIn(from, from + new.first().length)
        val newEnd = if (e == s) newStart else (e + totalDelta).coerceIn(newStart, out.length)
        return Edit(out, newStart, newEnd)
    }
}

