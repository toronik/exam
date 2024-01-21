package io.github.adven27.concordion.extensions.exam.core.handlebars.date

import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.internal.lang3.LocaleUtils
import com.github.jknack.handlebars.internal.lang3.Validate
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.ISO_LOCAL_DATE_FORMAT
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.PLACEHOLDER_TYPE
import io.github.adven27.concordion.extensions.exam.core.utils.DateWithin.Companion.PARAMS_SEPARATOR
import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.Companion.detectAndParse
import io.github.adven27.concordion.extensions.exam.core.utils.minus
import io.github.adven27.concordion.extensions.exam.core.utils.parseDate
import io.github.adven27.concordion.extensions.exam.core.utils.parsePeriodFrom
import io.github.adven27.concordion.extensions.exam.core.utils.plus
import io.github.adven27.concordion.extensions.exam.core.utils.toDate
import io.github.adven27.concordion.extensions.exam.core.utils.toLocalDate
import io.github.adven27.concordion.extensions.exam.core.utils.toLocalDateTime
import io.github.adven27.concordion.extensions.exam.core.utils.toString
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.util.*

private const val TZ = "tz"
private const val FORMAT = "format"
private const val PLUS = "plus"
private const val MINUS = "minus"

/* ktlint-disable enum-entry-name-case */

@Suppress("EnumNaming", "MagicNumber")
enum class DateHelpers(
    override val example: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val expected: Any? = "",
    override val options: Map<String, String> = emptyMap()
) : ExamHelper {
    at(
        example = "{{at '-2d' '-1m'}}",
        expected = LocalDateTime.now().minusDays(2).minusMinutes(1).toDate()
    ) {
        override fun invoke(context: Any?, options: Options) = (
            context.takeIf { it is String }?.let {
                (setOf(context) + options.params).map { detectAndParse(it as String) }.fold(AT) { r, d -> r.plus(d) }
            } ?: AT
            ).toDate()
    },
    now(
        example = """{{now "yyyy-MM-dd'T'HH:mm:ss" tz="GMT+3" minus="1 y, 2 months, d 3" plus="4 h, 5 min, 6 s"}}""",
        expected = ZonedDateTime.now("GMT+3".timeZoneId())
            .minusYears(1).minusMonths(2).minusDays(3)
            .plusHours(4).plusMinutes(5).plusSeconds(6)
            .toDate(),
        options = mapOf(TZ to "\"GMT+3\"", PLUS to "\"1 day\"", MINUS to "\"5 hours\"")
    ) {
        override fun invoke(context: Any?, options: Options): Any? = if (context is String && context.isNotBlank()) {
            dateFormat(
                Date(),
                context,
                options.param(0, Locale.getDefault().toString()),
                options.hash(PLUS, ""),
                options.hash(MINUS, ""),
                options.hash(TZ)
            )
        } else {
            LocalDateTime.now()
                .plus(parsePeriodFrom(options.hash(PLUS, "")))
                .minus(parsePeriodFrom(options.hash(MINUS, "")))
                .toDate(options.hash<String?>(TZ)?.timeZoneId() ?: ZoneId.systemDefault())
        }
    },

    today(
        example = """{{today "yyyy-MM-dd" minus="1 y, 2 months, d 3"}}""",
        expected = ZonedDateTime.now(ZoneId.systemDefault())
            .minusYears(1).minusMonths(2).minusDays(3)
            .toString("yyyy-MM-dd"),
        options = mapOf(PLUS to "\"1 day\"", MINUS to "\"5 hours\"")
    ) {
        override fun invoke(context: Any?, options: Options): Any? = if (context is String && context.isNotBlank()) {
            dateFormat(
                Date.from(now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                context,
                options.param(0, Locale.getDefault().toString()),
                options.hash(PLUS, ""),
                options.hash(MINUS, ""),
                options.hash(TZ)
            )
        } else {
            now().atStartOfDay()
                .plus(parsePeriodFrom(options.hash(PLUS, "")))
                .minus(parsePeriodFrom(options.hash(MINUS, "")))
                .toLocalDate().toDate()
        }
    },
    date(
        example = """{{date '01.02.2000 10:20' format="dd.MM.yyyy HH:mm" minus="1 h" plus="1 h"}}""",
        expected = LocalDateTime.of(2000, 2, 1, 10, 20).toDate(),
        options = mapOf(FORMAT to "\"dd.MM.yyyy\"", PLUS to "\"1 day\"", MINUS to "\"5 hours\"")
    ) {
        override fun invoke(context: Any?, options: Options): Any = parseDate(context, options)
            .plus(parsePeriodFrom(options.hash(PLUS, "")))
            .minus(parsePeriodFrom(options.hash(MINUS, "")))
            .toDate()

        private fun parseDate(context: Any?, options: Options): Date = if (context is String && context.isNotBlank()) {
            context.parseDate(options.hash<String>(FORMAT, null))
        } else {
            context as Date
        }
    },
    weeksAgo(
        example = "{{weeksAgo 2}}",
        expected = now().minusWeeks(2).atStartOfDay().toDate()
    ) {
        override fun invoke(context: Any?, options: Options) =
            now().minusWeeks(context?.toString()?.toLongOrNull() ?: 1).atStartOfDay().toDate()
    },
    daysAgo(
        example = "{{daysAgo 2}}",
        expected = now().minusDays(2).atStartOfDay().toDate()
    ) {
        override fun invoke(context: Any?, options: Options) =
            now().minusDays(context?.toString()?.toLongOrNull() ?: 1).atStartOfDay().toDate()
    },
    iso(
        example = "{{iso date}}",
        context = mapOf("date" to "2000-01-02T10:20:11.123".parseDate()),
        expected = "2000-01-02T10:20:11.123"
    ) {
        override fun invoke(context: Any?, options: Options): String =
            when (context) {
                is Date -> ISO_LOCAL_DATE_TIME.format(context.toLocalDateTime())
                is String -> "\${${placeholderType(options.context)}-unit.matches:formattedAndWithin}ISO_LOCAL" +
                    "$PARAMS_SEPARATOR$context$PARAMS_SEPARATOR${options.param(0, Date()).toLocalDateTime()}"

                else -> "\${${placeholderType(options.context)}-unit.matches:formattedAs}ISO_LOCAL"
            }
    },
    isoDate(
        example = "{{isoDate date}}",
        context = mapOf("date" to "2000-01-02T10:20:11.123".parseDate()),
        expected = "2000-01-02"
    ) {
        override fun invoke(context: Any?, options: Options): String =
            when (context) {
                is Date -> ISO_LOCAL_DATE.format(context.toLocalDateTime())
                is String ->
                    "\${${placeholderType(options.context)}-unit.matches:formattedAndWithin}$ISO_LOCAL_DATE_FORMAT" +
                        "$PARAMS_SEPARATOR$context$PARAMS_SEPARATOR${options.param(0, Date()).toLocalDate()}"

                else -> "\${${placeholderType(options.context)}-unit.matches:formattedAs}$ISO_LOCAL_DATE_FORMAT"
            }
    },
    formattedAndWithin(
        example = "{{formattedAndWithin 'yyyy-MM-dd' '5s' (today)}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:formattedAndWithin}yyyy-MM-dd" +
            "${PARAMS_SEPARATOR}5s" +
            "${PARAMS_SEPARATOR}${now()}T00:00"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$name}$context" +
                "${PARAMS_SEPARATOR}${options.param(0, "5s")}" +
                "${PARAMS_SEPARATOR}${options.param(1, Date()).toLocalDateTime()}"
    },
    formattedAs(
        example = "{{formattedAs \"yyyy-MM-dd'T'hh:mm:ss\"}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:formattedAs}yyyy-MM-dd'T'hh:mm:ss"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$name}$context"
    },
    after(
        example = "{{after (today)}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:after}${now()}T00:00"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            when (context) {
                is Date -> "\${${placeholderType(options.context)}-unit.matches:$name}${context.toLocalDateTime()}"
                else -> "\${${placeholderType(options.context)}-unit.matches:$name}$context"
            }
    },
    before(
        example = "{{before (today)}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:before}${now()}T00:00"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            when (context) {
                is Date -> "\${${placeholderType(options.context)}-unit.matches:$name}${context.toLocalDateTime()}"
                else -> "\${${placeholderType(options.context)}-unit.matches:$name}$context"
            }
    },
    dateFormat(
        example = """{{dateFormat date "yyyy-MM-dd'T'HH:mm O" tz="GMT+3"}}""",
        context = mapOf("date" to "2000-01-02T10:20+03:00".parseDate()),
        expected = "2000-01-02T10:20 GMT+3",
        options = mapOf(TZ to "\"GMT+3\"")
    ) {
        override fun invoke(context: Any?, options: Options): Any? {
            Validate.isInstanceOf(
                Date::class.java,
                context,
                "Wrong context for helper '%s': '%s', expected instance of Date. Example: %s",
                options.fn.text(),
                context,
                example
            )
            return dateFormat(
                context as Date,
                options.param(0, DEFAULT_FORMAT),
                options.param(1, Locale.getDefault().toString()),
                "",
                "",
                options.hash(TZ)
            )
        }
    };

    @Suppress("LongParameterList")
    protected fun dateFormat(date: Date, format: String, local: String, plus: String, minus: String, tz: String?) =
        DateTimeFormatter.ofPattern(format)
            .withLocale(LocaleUtils.toLocale(local))
            .apply { tz?.let { withZone(it.timeZoneId()) } }
            .format(date.plus(parsePeriodFrom(plus)).minus(parsePeriodFrom(minus)).atZone(ZoneId.systemDefault()))

    override fun apply(context: Any?, options: Options): Any? {
        validate(options)
        return try {
            this(context, options)
        } catch (expected: Exception) {
            throw ExamHelper.InvocationFailed(name, context, options, expected)
        }
    }

    override fun toString() = this.describe()
    abstract operator fun invoke(context: Any?, options: Options): Any?

    companion object {
        const val DEFAULT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
        private val AT = LocalDateTime.now()
    }
}

fun String.timeZoneId(): ZoneId = ZoneId.of(this)
