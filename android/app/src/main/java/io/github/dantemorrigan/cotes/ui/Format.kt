package io.github.dantemorrigan.cotes.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.dantemorrigan.cotes.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU: Locale = Locale.forLanguageTag("ru")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", RU)
private val dayFmt = DateTimeFormatter.ofPattern("d MMM", RU)
private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy", RU)

fun shortDate(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(ts).atZone(zone)
    val today = LocalDate.now(zone)
    val day = at.toLocalDate()
    return when {
        day == today -> timeFmt.format(at)
        day == today.minusDays(1) -> "вчера"
        day.year == today.year -> dayFmt.format(at)
        else -> fullFmt.format(at)
    }
}

fun longDate(ts: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(ts).atZone(zone)
    val today = LocalDate.now(zone)
    val day = at.toLocalDate()
    val time = timeFmt.format(at)
    return when (day) {
        today -> "сегодня в $time"
        today.minusDays(1) -> "вчера в $time"
        else -> fullFmt.format(at) + " в $time"
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val a = n % 100
    val b = n % 10
    return when {
        a in 11..19 -> many
        b == 1 -> one
        b in 2..4 -> few
        else -> many
    }
}

fun wordCount(text: String): Int = Regex("\\S+").findAll(text).count()

fun Note.matches(query: String): Boolean =
    query.isEmpty() || title.contains(query, ignoreCase = true) || content.contains(query, ignoreCase = true)

/** Превью: текст без первой строки (если она служит заголовком), в одну строку, вокруг совпадения. */
fun Note.preview(query: String): String {
    var body = content
    if (title.isBlank()) body = body.trimStart().substringAfter('\n', "")
    body = body.replace(Regex("\\s+"), " ").trim()
    if (query.isNotEmpty()) {
        val i = body.indexOf(query, ignoreCase = true)
        if (i > 40) body = "…" + body.substring(i - 30)
    }
    return body.take(160)
}

fun highlight(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    if (query.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var start = 0
    while (start < text.length) {
        val i = text.indexOf(query, start, ignoreCase = true)
        if (i < 0) break
        append(text.substring(start, i))
        withStyle(style) { append(text.substring(i, i + query.length)) }
        start = i + query.length
    }
    if (start < text.length) append(text.substring(start))
}
