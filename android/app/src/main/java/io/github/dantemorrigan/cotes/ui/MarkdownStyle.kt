package io.github.dantemorrigan.cotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/** Цвета подсветки: акцент для ссылок и маркеров, бледный — для значков разметки. */
data class MarkdownColors(val accent: Color, val faint: Color, val muted: Color, val codeBackground: Color, val mark: Color)

/**
 * Подсветка Markdown прямо в поле ввода. Меняет только оформление: текст и его длина те же,
 * поэтому курсор, выделение и вставка работают как в обычном поле. Значки разметки не прячутся,
 * а становятся бледными — видно, что написано, и ничего не «прыгает».
 */
class MarkdownVisualTransformation(
    private val colors: MarkdownColors,
    private val baseSp: Int,
) : VisualTransformation {
    // Поле вызывает filter на каждую перерисовку; пересчитываем, только если текст изменился.
    private var lastText: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val styled = if (s == lastText) lastResult!! else style(s).also { lastText = s; lastResult = it }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is MarkdownVisualTransformation && other.colors == colors && other.baseSp == baseSp

    override fun hashCode(): Int = colors.hashCode() * 31 + baseSp

    private fun style(s: String): AnnotatedString {
        // Очень длинный текст оставляем без подсветки: набор не должен тормозить.
        if (s.length > MAX_LENGTH) return AnnotatedString(s)
        val b = AnnotatedString.Builder(s)
        val faint = SpanStyle(color = colors.faint)
        var inFence = false
        var start = 0
        while (start <= s.length) {
            val nl = s.indexOf('\n', start).let { if (it < 0) s.length else it }
            val line = s.substring(start, nl)
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    inFence = !inFence
                    b.addStyle(faint, start, nl)
                }
                inFence -> b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground), start, nl)
                else -> styleLine(b, line, start, nl, indent, trimmed)
            }
            if (nl == s.length) break
            start = nl + 1
        }
        return b.toAnnotatedString()
    }

    private fun styleLine(b: AnnotatedString.Builder, line: String, start: Int, end: Int, indent: Int, trimmed: String) {
        val faint = SpanStyle(color = colors.faint)
        heading.find(line)?.let { m ->
            val level = m.groupValues[1].length
            val scale = when (level) { 1 -> 1.45f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
            val size = baseSp * scale
            b.addStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold), start, end)
            if (end > start) b.addStyle(ParagraphStyle(lineHeight = (size * 1.35f).sp), start, end)
            b.addStyle(faint, start, start + m.range.last + 1)
            inline(b, line, start, m.range.last + 1)
            return
        }
        if (rule.matches(line)) {
            b.addStyle(faint, start, end)
            return
        }
        quote.find(line)?.let { m ->
            b.addStyle(SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic), start, end)
            b.addStyle(SpanStyle(color = colors.accent), start, start + m.groupValues[1].length)
            if (m.groupValues[2].isNotEmpty()) {
                val tagStart = start + m.groupValues[1].length
                b.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal), tagStart, tagStart + m.groupValues[2].length)
            }
            inline(b, line, start, m.range.last + 1)
            return
        }
        task.find(line)?.let { m ->
            val markerEnd = start + m.range.last + 1
            b.addStyle(faint, start + indent, markerEnd)
            val boxStart = start + m.groupValues[1].length
            b.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), boxStart, boxStart + 3)
            if (m.groupValues[2] != " ") {
                b.addStyle(SpanStyle(color = colors.muted, textDecoration = TextDecoration.LineThrough), markerEnd, end)
            }
            inline(b, line, start, m.range.last + 1)
            return
        }
        bullet.find(line)?.let { m ->
            b.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), start + indent, start + m.range.last + 1)
            inline(b, line, start, m.range.last + 1)
            return
        }
        if (trimmed.isNotEmpty()) inline(b, line, start, 0)
    }

    /** Выделения внутри строки: жирный, курсив, код, ссылки… [from] — откуда искать в строке. */
    private fun inline(b: AnnotatedString.Builder, line: String, start: Int, from: Int) {
        val faint = SpanStyle(color = colors.faint)
        fun wrap(regex: Regex, delimiter: Int, style: SpanStyle) {
            regex.findAll(line, from).forEach { m ->
                val a = start + m.range.first
                val z = start + m.range.last + 1
                b.addStyle(style, a, z)
                b.addStyle(faint, a, a + delimiter)
                b.addStyle(faint, z - delimiter, z)
            }
        }
        code.findAll(line, from).forEach { m ->
            val a = start + m.range.first
            val z = start + m.range.last + 1
            b.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground), a, z)
            b.addStyle(faint, a, a + 1)
            b.addStyle(faint, z - 1, z)
        }
        bold.findAll(line, from).forEach { m ->
            val a = start + m.range.first
            val z = start + m.range.last + 1
            b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), a, z)
            b.addStyle(faint, a, a + 2)
            b.addStyle(faint, z - 2, z)
        }
        wrap(italicStar, 1, SpanStyle(fontStyle = FontStyle.Italic))
        wrap(italicUnderscore, 1, SpanStyle(fontStyle = FontStyle.Italic))
        wrap(strike, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
        wrap(highlight, 2, SpanStyle(background = colors.mark))
        mdLink.findAll(line, from).forEach { m ->
            val a = start + m.range.first
            val textEnd = a + 1 + m.groupValues[1].length
            b.addStyle(faint, a, a + 1)
            b.addStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline), a + 1, textEnd)
            b.addStyle(faint, textEnd, start + m.range.last + 1)
        }
        wiki.findAll(line, from).forEach { m ->
            val a = start + m.range.first
            val z = start + m.range.last + 1
            b.addStyle(SpanStyle(color = colors.accent), a, z)
            b.addStyle(faint, a, a + m.groupValues[1].length)
            b.addStyle(faint, z - 2, z)
        }
        url.findAll(line, from).forEach { m ->
            b.addStyle(
                SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                start + m.range.first,
                start + m.range.last + 1,
            )
        }
    }

    private companion object {
        const val MAX_LENGTH = 60_000
        val heading = Regex("""^\s{0,3}(#{1,6})\s+""")
        val rule = Regex("""^\s{0,3}(-{3,}|\*{3,}|_{3,})\s*$""")
        val quote = Regex("""^(\s*(?:>\s?)+)(\[![^\]]*\])?""")
        val task = Regex("""^(\s*[-*+]\s)\[([ xX])\]\s?""")
        val bullet = Regex("""^\s*([-*+]|\d{1,9}[.)])\s""")
        val code = Regex("""`[^`]+`""")
        val bold = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
        val italicStar = Regex("""(?<![*\p{L}\p{N}])\*(?=\S)([^*]+?)(?<=\S)\*(?![*\p{L}\p{N}])""")
        val italicUnderscore = Regex("""(?<![_\p{L}\p{N}])_(?=\S)([^_]+?)(?<=\S)_(?![_\p{L}\p{N}])""")
        val strike = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
        val highlight = Regex("""==(?=\S)(.+?)(?<=\S)==""")
        val mdLink = Regex("""\[([^\]\n]+)\]\(([^)\s]+)\)""")
        val wiki = Regex("""(!?\[\[)[^\]\n]+\]\]""")
        val url = Regex("""https?://[^\s)\]>"]+""")
    }
}
