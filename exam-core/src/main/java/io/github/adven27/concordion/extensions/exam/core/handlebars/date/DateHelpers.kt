package io.github.adven27.concordion.extensions.exam.core.handlebars.date

import com.github.jknack.handlebars.Options
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.ISO_LOCAL_DATETIME_FORMAT
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.ISO_LOCAL_DATE_FORMAT
import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.Companion.detectAndParse
import io.github.adven27.concordion.extensions.exam.core.utils.parseDate
import io.github.adven27.concordion.extensions.exam.core.utils.toDate
import io.github.adven27.concordion.extensions.exam.core.utils.toLocalDateTime
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ofPattern
import java.util.*

/* ktlint-disable enum-entry-name-case */
@Suppress("EnumEntryNameCase", "EnumNaming", "MagicNumber")
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
        override fun invoke(context: Any?, options: Options) = toDate(context, AT, options.params)
    },
    now(
        example = """{{now '-2d' '-1m'}}""",
        expected = LocalDateTime.now().minusDays(2).minusMinutes(1).toDate()
    ) {
        override fun invoke(context: Any?, options: Options) = toDate(context, LocalDateTime.now(), options.params)
    },
    today(
        example = """{{today '-1y' '-2M' '-3d'}}""",
        expected = now().minusYears(1).minusMonths(2).minusDays(3).toDate()
    ) {
        override fun invoke(context: Any?, options: Options) =
            toDate(context, now().atStartOfDay(), options.params)
    },
    parse(
        example = """{{parse '01.02.2000 10:20' 'dd.MM.yyyy HH:mm'}}""",
        expected = LocalDateTime.of(2000, 2, 1, 10, 20).toDate()
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            if (context is String) context.parseDate(options.param(0, null)) else context as Date
    },
    shift(
        example = """{{shift date '-2d' '1m'}}""",
        context = mapOf("date" to "2000-01-03T10:20".parseDate()),
        expected = LocalDateTime.of(2000, 1, 1, 10, 21).toDate()
    ) {
        override fun invoke(context: Any?, options: Options): Any = options.params.toList().shift(
            when (context) {
                is Date -> context.toLocalDateTime()
                is LocalDateTime -> context
                is LocalDate -> context.atStartOfDay()
                else -> throw IllegalArgumentException("Expected date object")
            }
        ).toDate()
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
    format(
        example = """{{format date "yyyy-MM-dd'T'HH:mm"}}""",
        context = mapOf("date" to "2000-01-02T10:20".parseDate()),
        expected = "2000-01-02T10:20"
    ) {
        override fun invoke(context: Any?, options: Options): Any? = when (context) {
            is Date -> ofPattern(options.param(0, ISO_LOCAL_DATETIME_FORMAT)).format(context.toLocalDateTime())
            is LocalDateTime -> ofPattern(options.param(0, ISO_LOCAL_DATETIME_FORMAT)).format(context)
            is LocalDate -> ofPattern(options.param(0, ISO_LOCAL_DATE_FORMAT)).format(context)
            else -> context.toString()
        }
    };

    internal fun toDate(c: Any?, at: LocalDateTime, params: Array<out Any>?) =
        (c.takeIf { it is String }?.let { (listOf(c) + (params?.toList() ?: listOf())).shift(at) } ?: at).toDate()

    fun List<Any?>.shift(at: LocalDateTime) = map { detectAndParse(it as String) }.fold(at) { r, d -> r.plus(d) }

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
        val AT = LocalDateTime.now()
    }
}
