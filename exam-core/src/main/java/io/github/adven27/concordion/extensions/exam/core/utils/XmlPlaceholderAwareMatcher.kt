package io.github.adven27.concordion.extensions.exam.core.utils

import net.javacrumbs.jsonunit.core.ParametrizedMatcher
import org.hamcrest.Matcher
import java.util.regex.Pattern

open class XmlPlaceholderAwareMatcher(private val matchers: Map<String, Matcher<*>>) {
    open fun matches(expected: String, actual: String) = when (expected) {
        ANY_NUMBER -> CheckType.NUMBER.suit(actual)
        ANY_BOOLEAN -> CheckType.BOOLEAN.suit(actual)
        ANY_STRING -> CheckType.STRING.suit(actual)
        IGNORE_PLACEHOLDER -> true
        else -> {
            val pattern = MATCHER_PLACEHOLDER_PATTERN.matcher(expected)
            when {
                pattern.matches() -> match(actual = actual, matcher = pattern.group(1), param = pattern.group(2))
                expected.regex() -> actual.matches(expected.regexPattern().toRegex())
                else -> false
            }
        }
    }

    private fun match(actual: String, matcher: String, param: String?) =
        checkNotNull(matchers[matcher]) { "Matcher \"$matcher\" not found." }
            .apply { if (this is ParametrizedMatcher) setParameter(param) }
            .matches(actual)

    private fun String.regexPattern(): String = substring(REGEX_PLACEHOLDER.length)
    private fun String.regex(): Boolean = startsWith(REGEX_PLACEHOLDER)

    enum class CheckType {
        NUMBER, STRING, BOOLEAN;

        fun suit(node: String): Boolean = when (this) {
            NUMBER -> isNum(node)
            BOOLEAN -> node.lowercase().let { "true" == it || "false" == it }
            STRING -> !isNum(node)
        }

        private fun isNum(node: String): Boolean = node.toIntOrNull() != null || node.toDoubleOrNull() != null
    }

    companion object {
        private const val ANY_NUMBER = "\${xml-unit.any-number}"
        private const val ANY_BOOLEAN = "\${xml-unit.any-boolean}"
        private const val ANY_STRING = "\${xml-unit.any-string}"
        private const val IGNORE_PLACEHOLDER = "\${xml-unit.ignore}"
        private const val REGEX_PLACEHOLDER = "\${xml-unit.regex}"
        private val MATCHER_PLACEHOLDER_PATTERN: Pattern = Pattern.compile("\\$\\{xml-unit.matches:(.+)\\}(.*)")
    }
}
