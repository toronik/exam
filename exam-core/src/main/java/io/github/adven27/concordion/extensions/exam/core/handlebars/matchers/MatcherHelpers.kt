package io.github.adven27.concordion.extensions.exam.core.handlebars.matchers

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Options
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import org.concordion.api.Evaluator
import java.util.regex.Pattern

const val PLACEHOLDER_TYPE = "placeholder_type"
const val DB_ACTUAL = "db_actual"
const val ISO_LOCAL_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
const val ISO_LOCAL_DATE_FORMAT = "yyyy-MM-dd"

/* ktlint-disable enum-entry-name-case */

@Suppress("EnumNaming", "EnumEntryName")
enum class MatcherHelpers(
    override val example: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val expected: Any? = "",
    override val options: Map<String, String> = emptyMap()
) : ExamHelper {
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
    regex(
        example = "{{regex '\\d+'}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.regex}\\d+"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            if (placeholderType(options.context) == "db") regexMatches(context.toString(), dbActual(options.context))
            else "\${${placeholderType(options.context)}-unit.regex}$context"
    },
    matches(
        example = "{{matches 'name' 'params'}}",
        context = mapOf(PLACEHOLDER_TYPE to "json"),
        expected = "\${json-unit.matches:name}params"
    ) {
        override fun invoke(context: Any?, options: Options): Any =
            "\${${placeholderType(options.context)}-unit.matches:$context}${options.param(0, "")}"
    };

    protected fun dbActual(context: Context) = (context.model() as Evaluator).getVariable("#$DB_ACTUAL")

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

private fun regexMatches(p: String, value: Any?): Boolean =
    value.takeIf { it != null }?.let { Pattern.compile(p).matcher(it.toString()).matches() } ?: false
