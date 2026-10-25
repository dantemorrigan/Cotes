package io.github.dantemorrigan.cotes.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Режим просмотра: Markdown превращается в оформленный текст, значки разметки скрыты.
 * Ссылки открываются нажатием, а нажатие на ☐ / ☑ отмечает пункт — [onToggle] получает
 * позицию символа внутри «[ ]» в исходном тексте.
 */
object MarkdownRender {
    fun render(
        text: String,
        colors: MarkdownColors,
        baseSp: Int,
        onToggle: (Int) -> Unit,
    ): AnnotatedString {
        if (text.length > MAX_LENGTH) return AnnotatedString(text)
        val linkStyles = TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline))
        // Сначала строки по отдельности, потом склейка: абзацный стиль заголовка должен
        // захватить и перевод строки, иначе Compose вставит после заголовка пустую строку.
        val pieces = ArrayList<Pair<AnnotatedString, ParagraphStyle?>>()
        var inFence = false
        var lineStart = 0
        while (lineStart <= text.length) {
            val nl = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, nl)
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                // Строки-ограды блока кода не показываем.
                inFence = !inFence
            } else {
                val b = AnnotatedString.Builder()
                val paragraph = if (inFence) {
                    b.append(line)
                    b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground), 0, b.length)
                    null
                } else {
                    renderLine(b, line, lineStart, colors, baseSp, linkStyles, onToggle)
                }
                pieces += b.toAnnotatedString() to paragraph
            }
            if (nl == text.length) break
            lineStart = nl + 1
        }
        val out = AnnotatedString.Builder()
        pieces.forEachIndexed { i, (piece, paragraph) ->
            val at = out.length
            out.append(piece)
            if (i < pieces.lastIndex) out.append('\n')
            if (paragraph != null && out.length > at) out.addStyle(paragraph, at, out.length)
        }
        return out.toAnnotatedString()
    }

    private fun renderLine(
        out: AnnotatedString.Builder,
        line: String,
        lineOffset: Int,
        colors: MarkdownColors,
        baseSp: Int,
        linkStyles: TextLinkStyles,
        onToggle: (Int) -> Unit,
    ): ParagraphStyle? {
        val at = out.length
        heading.find(line)?.let { m ->
            val level = m.groupValues[1].length
            val size = baseSp * when (level) { 1 -> 1.45f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
            appendInline(out, line.substring(m.range.last + 1), colors, linkStyles)
            out.addStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold), at, out.length)
            return ParagraphStyle(lineHeight = (size * 1.35f).sp)
        }
        if (rule.matches(line)) {
            out.append("⎯⎯⎯")
            out.addStyle(SpanStyle(color = colors.faint, letterSpacing = 0.sp), at, out.length)
            return null
        }
        quote.find(line)?.let { m ->
            out.append("▍")
            out.addStyle(SpanStyle(color = colors.accent), at, out.length)
            val type = m.groupValues[2].removePrefix("[!").removeSuffix("]").trim()
            if (type.isNotEmpty()) {
                val tagAt = out.length
                out.append(type.replaceFirstChar { it.uppercase() })
                out.append(if (line.length > m.range.last + 1) " · " else "")
                out.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), tagAt, out.length)
            }
            val bodyAt = out.length
            appendInline(out, line.substring(m.range.last + 1), colors, linkStyles)
            out.addStyle(SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic), bodyAt, out.length)
            return null
        }
        task.find(line)?.let { m ->
            val indent = m.groupValues[1].takeWhile { it.isWhitespace() }
            out.append(indent)
            val checked = m.groupValues[2] != " "
            val inner = lineOffset + m.groupValues[1].length + 1
            val boxAt = out.length
            out.append(if (checked) "☑" else "☐")
            out.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), boxAt, out.length)
            out.addLink(LinkAnnotation.Clickable("task:$inner") { onToggle(inner) }, boxAt, out.length)
            out.append(' ')
            val bodyAt = out.length
            appendInline(out, line.substring(m.range.last + 1), colors, linkStyles)
            if (checked) {
                out.addStyle(SpanStyle(color = colors.muted, textDecoration = TextDecoration.LineThrough), bodyAt, out.length)
            }
            return null
        }
        bullet.find(line)?.let { m ->
            val indent = m.groupValues[1]
            val marker = m.groupValues[2]
            out.append(indent)
            val markAt = out.length
            out.append(if (marker.first().isDigit()) "$marker " else "•  ")
            out.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), markAt, out.length)
            appendInline(out, line.substring(m.range.last + 1), colors, linkStyles)
            return null
        }
        appendInline(out, line, colors, linkStyles)
        return null
    }

    /**
     * Строка без значков: находим выделения в исходной строке, помечаем значки скрытыми
     * и переносим текст с оформлением, пересчитывая позиции.
     */
    private fun appendInline(out: AnnotatedString.Builder, line: String, colors: MarkdownColors, linkStyles: TextLinkStyles) {
        val hidden = BooleanArray(line.length)
        val spans = ArrayList<Triple<Int, Int, Any>>()
        val taken = BooleanArray(line.length)

        fun free(a: Int, z: Int) = (a until z).none { taken[it] }
        fun take(a: Int, z: Int) = (a until z).forEach { taken[it] = true }
        fun hide(a: Int, z: Int) = (a until z).forEach { hidden[it] = true }

        // Код первым: внутри него остальная разметка не действует.
        code.findAll(line).forEach { m ->
            val a = m.range.first
            val z = m.range.last + 1
            if (!free(a, z)) return@forEach
            take(a, z)
            hide(a, a + 1)
            hide(z - 1, z)
            spans += Triple(a, z, SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground))
        }
        mdLink.findAll(line).forEach { m ->
            val a = m.range.first
            val z = m.range.last + 1
            if (!free(a, z)) return@forEach
            take(a, z)
            val textEnd = a + 1 + m.groupValues[1].length
            hide(a, a + 1)
            hide(textEnd, z)
            spans += Triple(a + 1, textEnd, LinkAnnotation.Url(m.groupValues[2], linkStyles))
        }
        wiki.findAll(line).forEach { m ->
            val a = m.range.first
            val z = m.range.last + 1
            if (!free(a, z)) return@forEach
            take(a, z)
            val inner = line.substring(a + m.groupValues[1].length, z - 2)
            val pipe = inner.indexOf('|')
            val shownFrom = a + m.groupValues[1].length + if (pipe >= 0) pipe + 1 else 0
            hide(a, shownFrom)
            hide(z - 2, z)
            spans += Triple(shownFrom, z - 2, SpanStyle(color = colors.accent))
        }
        url.findAll(line).forEach { m ->
            val a = m.range.first
            val z = m.range.last + 1
            if (!free(a, z)) return@forEach
            take(a, z)
            spans += Triple(a, z, LinkAnnotation.Url(m.value, linkStyles))
        }
        fun wrap(regex: Regex, d: Int, style: SpanStyle) {
            regex.findAll(line).forEach { m ->
                val a = m.range.first
                val z = m.range.last + 1
                if (taken[a] || taken[z - 1]) return@forEach
                hide(a, a + d)
                hide(z - d, z)
                spans += Triple(a, z, style)
            }
        }
        wrap(bold, 2, SpanStyle(fontWeight = FontWeight.Bold))
        wrap(strike, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
        wrap(highlight, 2, SpanStyle(background = colors.mark))
        wrap(italicStar, 1, SpanStyle(fontStyle = FontStyle.Italic))
        wrap(italicUnderscore, 1, SpanStyle(fontStyle = FontStyle.Italic))

        // Перенос с пересчётом позиций: map[i] — позиция символа i исходной строки в результате.
        val base = out.length
        val map = IntArray(line.length + 1)
        var n = 0
        for (i in line.indices) {
            map[i] = n
            if (!hidden[i]) {
                out.append(line[i])
                n++
            }
        }
        map[line.length] = n
        spans.forEach { (a, z, style) ->
            val s = base + map[a]
            val e = base + map[z]
            if (e <= s) return@forEach
            when (style) {
                is SpanStyle -> out.addStyle(style, s, e)
                is LinkAnnotation -> out.addLink(style as LinkAnnotation.Url, s, e)
            }
        }
    }

    private const val MAX_LENGTH = 60_000
    private val heading = Regex("""^\s{0,3}(#{1,6})\s+""")
    private val rule = Regex("""^\s{0,3}(-{3,}|\*{3,}|_{3,})\s*$""")
    private val quote = Regex("""^(\s*(?:>\s?)+)(\[![^\]]*\]\s?)?""")
    private val task = Regex("""^(\s*[-*+]\s)\[([ xX])\]\s?""")
    private val bullet = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s""")
    private val code = Regex("""`[^`]+`""")
    private val bold = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
    private val italicStar = Regex("""(?<![*\p{L}\p{N}])\*(?=\S)([^*]+?)(?<=\S)\*(?![*\p{L}\p{N}])""")
    private val italicUnderscore = Regex("""(?<![_\p{L}\p{N}])_(?=\S)([^_]+?)(?<=\S)_(?![_\p{L}\p{N}])""")
    private val strike = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
    private val highlight = Regex("""==(?=\S)(.+?)(?<=\S)==""")
    private val mdLink = Regex("""\[([^\]\n]+)\]\(([^)\s]+)\)""")
    private val wiki = Regex("""(!?\[\[)[^\]\n]+\]\]""")
    private val url = Regex("""https?://[^\s)\]>"]+""")
}
