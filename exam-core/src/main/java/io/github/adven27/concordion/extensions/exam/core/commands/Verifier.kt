package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.descendantTextContainer
import io.github.adven27.concordion.extensions.exam.core.utils.After
import io.github.adven27.concordion.extensions.exam.core.utils.Before
import org.concordion.api.Element
import org.concordion.api.Evaluator
import java.math.BigDecimal
import java.util.UUID
import java.util.regex.Pattern

interface Verifier<E, A> {
    fun verify(eval: Evaluator, expected: E, actual: (E) -> A): Check<E, A>
    data class Check<E, A>(val expected: E, val actual: A?, val fail: Throwable? = null)
}

fun String.expression() = substring(indexOf("}") + 1).trim()

fun matchesRegex(pattern: String, actualValue: Any?): Boolean =
    if (actualValue == null) false else Pattern.compile(pattern).matcher(asString(actualValue)).matches()

private fun asString(actualValue: Any) = when (actualValue) {
    is BigDecimal -> actualValue.toPlainString()
    else -> actualValue.toString()
}

fun matchesAnyNumber(actual: Any?) = matchesRegex("^\\d+\$", actual)
fun matchesAnyString(actual: Any?) = matchesRegex("^\\w+\$", actual)
fun matchesAnyBoolean(actual: Any?) = actual is String? && actual?.toBooleanStrictOrNull()?.let { true } ?: false

fun matchesAnyUuid(a: Any?) = a is String && try {
    UUID.fromString(a)
    true
} catch (ignore: Exception) {
    false
}

val checkTextUnit: (Any?, String?) -> Boolean = { a, e ->
    when {
        e == null -> a == null
        e == "\${text-unit.any-string}" -> matchesAnyString(a)
        e == "\${text-unit.any-number}" -> matchesAnyNumber(a)
        e == "\${text-unit.any-boolean}" -> matchesAnyBoolean(a)
        e == "\${text-unit.ignore}" -> true
        e.startsWith("\${text-unit.regex}") -> matchesRegex(e.substringAfter("}"), a)
        e.startsWith("\${text-unit.matches:after}") ->
            After().apply { setParameter(e.substringAfter("}")) }.matches(a!!)

        e.startsWith("\${text-unit.matches:before}") ->
            Before().apply { setParameter(e.substringAfter("}")) }.matches(a!!)

        else -> a == e
    }
}

fun <T> checkAndSet(
    eval: Evaluator,
    actual: T,
    expected: String?,
    check: (actual: T, expected: String?) -> Boolean = checkTextUnit
): Boolean {
    val exp = if (expected != null) {
        val split = expected.split(">>")
        if (split.size > 1) eval.setVariable("#${split[1]}", actual)
        split[0]
    } else {
        null
    }
    return check(actual, exp)
}

fun Element.swapText(value: String) {
    Html(descendantTextContainer(this)).removeChildren().text(value).el.appendNonBreakingSpaceIfBlank()
}
