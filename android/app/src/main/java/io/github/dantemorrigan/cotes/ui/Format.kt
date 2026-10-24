package io.github.dantemorrigan.cotes.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.dantemorrigan.cotes.Note
import io.github.dantemorrigan.cotes.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Даты — на языке интерфейса и с 12- или 24-часовым временем, как в системе. */
private class DateFormats(context: Context) {
    private val locale = context.resources.configuration.locales[0]
    private fun pattern(skeleton: String) =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    val time: DateTimeFormatter = pattern(if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    val day: DateTimeFormatter = pattern("dMMM")
    val full: DateTimeFormatter = pattern("dMMMyyyy")
}

private fun zoned(ts: Long): ZonedDateTime = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())

/** Для списка: время сегодня, «вчера», число и месяц, а для прошлых лет — с годом. */
fun shortDate(context: Context, ts: Long): String {
    val f = DateFormats(context)
    val at = zoned(ts)
    val today = LocalDate.now(at.zone)
    val day = at.toLocalDate()
    return when {
        day == today -> f.time.format(at)
        day == today.minusDays(1) -> context.getString(R.string.date_yesterday)
        day.year == today.year -> f.day.format(at)
        else -> f.full.format(at)
    }
}

/** Для строки «Сохранено · …» в редакторе. */
fun longDate(context: Context, ts: Long): String {
    val f = DateFormats(context)
    val at = zoned(ts)
    val today = LocalDate.now(at.zone)
    val time = f.time.format(at)
    return when (at.toLocalDate()) {
        today -> context.getString(R.string.date_today_at, time)
        today.minusDays(1) -> context.getString(R.string.date_yesterday_at, time)
        else -> context.getString(R.string.date_at, f.full.format(at), time)
    }
}

fun Context.notesCount(n: Int): String = resources.getQuantityString(R.plurals.notes_count, n, n)

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
