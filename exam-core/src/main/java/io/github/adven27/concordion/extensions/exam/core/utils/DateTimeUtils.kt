@file:Suppress("TooManyFunctions")

package io.github.adven27.concordion.extensions.exam.core.utils

import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.ISO_LOCAL_DATETIME_FORMAT
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_DATE
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle.SMART
import java.time.temporal.ChronoField
import java.util.*

private val DEFAULT_ZONED_DATETIME_FORMAT = ISO_OFFSET_DATE_TIME.withResolverStyle(SMART)
private val DEFAULT_LOCAL_DATETIME_FORMAT = ISO_DATE_TIME.withResolverStyle(SMART)
private val DEFAULT_LOCAL_DATE_FORMAT = ISO_DATE.withResolverStyle(SMART)

fun ZonedDateTime.toString(pattern: String): String = this.format(DateTimeFormatter.ofPattern(pattern))
fun Date.toString(pattern: String): String =
    pattern.toDatePattern().withZone(ZoneId.systemDefault()).format(this.toInstant())

fun ZonedDateTime.toDate(): Date = Date.from(this.toInstant())
fun LocalDateTime.toDate(zoneId: ZoneId = ZoneId.systemDefault()): Date = Date.from(this.atZone(zoneId).toInstant())
fun LocalDate.toDate(zoneId: ZoneId = ZoneId.systemDefault()): Date = Date.from(this.atStartOfDay(zoneId).toInstant())
fun Date.toZonedDateTime(): ZonedDateTime = ZonedDateTime.from(this.toInstant().atZone(ZoneId.systemDefault()))
fun Date.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    this.toInstant().atZone(zoneId).toLocalDateTime()

fun Date.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    this.toInstant().atZone(zoneId).toLocalDate()

fun String.parseDate(format: String? = null) = try {
    parseDateTime(format).toDate()
} catch (e: DateTimeParseException) {
    try {
        parseLocalDateTime(format).toDate()
    } catch (e: DateTimeParseException) {
        parseLocalDate(format).toDate()
    }
}

fun String.parseDateTime(format: String? = null): ZonedDateTime =
    ZonedDateTime.parse(this, format?.toDatePattern() ?: DEFAULT_ZONED_DATETIME_FORMAT)

fun String.parseLocalDateTime(format: String? = null): LocalDateTime =
    LocalDateTime.parse(this, format?.toDatePattern() ?: DEFAULT_LOCAL_DATETIME_FORMAT)

fun String.parseLocalDate(format: String? = null): LocalDate =
    LocalDate.parse(this, format?.toDatePattern() ?: DEFAULT_LOCAL_DATE_FORMAT)

@Suppress("MagicNumber")
fun String.toDatePattern(): DateTimeFormatter = if (this == "ISO_LOCAL") {
    DateTimeFormatterBuilder()
        .appendPattern(ISO_LOCAL_DATETIME_FORMAT)
        .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 9, true)
        .toFormatter()
} else {
    DateTimeFormatter.ofPattern(this)
}

fun date(item: Any?, pattern: String? = null): Result<ZonedDateTime> = try {
    Result.success(
        when (item) {
            is ZonedDateTime -> item
            is LocalDateTime -> item.atZone(ZoneId.systemDefault())
            is Date -> item.toZonedDateTime()
            else -> item.toString().parseDate(pattern).toZonedDateTime()
        }
    )
} catch (expected: Exception) {
    Result.failure(expected)
}

fun ldt(item: Any?): LocalDateTime = when (item) {
    is ZonedDateTime -> item.toLocalDateTime()
    is LocalDateTime -> item
    is Date -> item.toLocalDateTime()
    else -> item.toString().parseDate().toLocalDateTime()
}
