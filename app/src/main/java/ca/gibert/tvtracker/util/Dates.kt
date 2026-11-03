package ca.gibert.tvtracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun parseTraktInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()
        }
        .getOrNull()
}

fun Instant.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    atZone(zone).toLocalDate()

fun isAired(firstAired: String?, now: Instant = Instant.now()): Boolean {
    val instant = parseTraktInstant(firstAired) ?: return true
    return !instant.isAfter(now)
}

fun episodeCode(season: Int, number: Int): String = "S${season}E$number"

fun formatRelativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()),
        )
    }

fun formatShortDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.localDate(zone)
    val pattern = if (date.year == LocalDate.now(zone).year) "MMM d" else "MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun formatFullDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(zone).toLocalDate())

fun formatTime(instant: Instant, is24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return instant.atZone(zone).format(
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()),
    )
}

fun formatAirDateTime(
    instant: Instant,
    is24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val dateLabel = formatRelativeDay(instant.localDate(zone))
    return "$dateLabel · ${formatTime(instant, is24Hour, zone)}"
}
