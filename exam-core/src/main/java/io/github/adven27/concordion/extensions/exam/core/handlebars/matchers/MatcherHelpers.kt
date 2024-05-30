package io.github.adven27.concordion.extensions.exam.core.handlebars.matchers

import com.github.jknack.handlebars.Options
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.utils.DateFormattedAndWithin.Companion.PARAMS_SEPARATOR
import io.github.adven27.concordion.extensions.exam.core.utils.ldt
import io.github.adven27.concordion.extensions.exam.core.utils.parseDate
import io.github.adven27.concordion.extensions.exam.core.utils.toLocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

const val PLACEHOLDER_TYPE = "placeholder_type"
const val ISO_LOCAL_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
const val ISO_LOCAL_DATE_FORMAT = "yyyy-MM-dd"

@Suppress("EnumNaming", "EnumEntryNameCase")
enum class MatcherHelpers(
    override val example: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val expected: Any? = "",
    override val options: Map<String, String> = emptyMap()
) : ExamHelper {
    notNull(
        example = "{{notNull}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.not-null}"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.not-null}"
    },
    string(
        example = "{{string}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.any-string}"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.any-string}"
    },
    number(
        example = "{{number}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.any-number}"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.any-number}"
    },
    bool(
        example = "{{bool}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.any-boolean}"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.any-boolean}"
    },
    ignore(
        example = "{{ignore}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.ignore}"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.ignore}"
    },
    uuid(
        example = "{{uuid}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:uuid}"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:uuid}"
    },
    regex(
        example = "{{regex '\\d+'}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.regex}\\d+"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.regex}$context"
    },
    after(
        example = "{{after (today)}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:after}${LocalDate.now()}T00:00"
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
        expected = "\${json-unit.matches:before}${LocalDate.now()}T00:00"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            when (context) {
                is Date -> "\${${placeholderType(options.context)}-unit.matches:$name}${context.toLocalDateTime()}"
                else -> "\${${placeholderType(options.context)}-unit.matches:$name}$context"
            }
    },
    formattedAndWithin(
        example = "{{formattedAndWithin 'yyyy-MM-dd' '5s' (today)}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:formattedAndWithin}yyyy-MM-dd" +
            "${PARAMS_SEPARATOR}5s" +
            "$PARAMS_SEPARATOR${LocalDate.now()}T00:00"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$name}$context" +
                "$PARAMS_SEPARATOR${options.param(0, "5s")}" +
                "$PARAMS_SEPARATOR${options.param(1, Date()).toLocalDateTime()}"
    },
    within(
        example = "{{within '5s' (today)}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:within}5s$PARAMS_SEPARATOR${LocalDate.now()}T00:00"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$name}$context" +
                "$PARAMS_SEPARATOR${ldt(options.param(0, Date()))}"
    },
    formattedAs(
        example = "{{formattedAs \"yyyy-MM-dd'T'hh:mm:ss\"}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:formattedAs}yyyy-MM-dd'T'hh:mm:ss"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$name}$context"
    },
    iso(
        example = "{{iso date}}",
        context = mapOf("date" to "2000-01-02T10:20:11.123".parseDate()),
        expected = "2000-01-02T10:20:11.123"
    ) {
        override fun invoke(context: Any?, options: Options): String =
            when (context) {
                is Date -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(context.toLocalDateTime())
                is String -> "\${${placeholderType(options.context)}-unit.matches:formattedAndWithin}ISO_LOCAL" +
                    "$PARAMS_SEPARATOR$context$PARAMS_SEPARATOR${ldt(options.param(0, Date()))}"

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
                is Date -> DateTimeFormatter.ISO_LOCAL_DATE.format(context.toLocalDateTime())
                is String ->
                    "\${${placeholderType(options.context)}-unit.matches:formattedAndWithin}$ISO_LOCAL_DATE_FORMAT" +
                        "$PARAMS_SEPARATOR$context$PARAMS_SEPARATOR${ldt(options.param(0, Date()))}"

                else -> "\${${placeholderType(options.context)}-unit.matches:formattedAs}$ISO_LOCAL_DATE_FORMAT"
            }
    },
    matches(
        example = "{{matches 'name' 'params'}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:name}params"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$context}${options.param(0, "")}"
    };

    override fun apply(context: Any?, options: Options): Any? {
        validate(options)
        val result = try {
            this(context, options)
        } catch (expected: Exception) {
            throw ExamHelper.InvocationFailed(name, context, options, expected)
        }
        return result
    }

    override fun toString() = this.describe()
    abstract operator fun invoke(context: Any?, options: Options): Any?
}
