package io.github.adven27.concordion.extensions.exam.core.utils

import mu.KLogging
import net.javacrumbs.jsonunit.core.ParametrizedMatcher
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.hasEntry
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.matchesRegex
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.AllOf.allOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount
import java.util.*
import javax.xml.datatype.DatatypeFactory

open class DateFormatMatcher(var pattern: String? = null) : BaseMatcher<Any>(), ParametrizedMatcher {
    override fun matches(item: Any): Boolean = date(item, pattern).map { true }.getOrDefault(false)

    override fun describeTo(description: Description) {
        description.appendValue(pattern)
    }

    override fun describeMismatch(item: Any, description: Description) {
        description.appendText("The date is not properly formatted ").appendValue(pattern)
    }

    override fun setParameter(parameter: String) {
        if (parameter.isNotBlank()) {
            this.pattern = parameter
        }
    }
}

class DateFormattedAndWithinNow : DateFormattedAndWithin()
class DateFormattedAndWithinDate : DateFormattedAndWithin(false)

open class DateFormattedAndWithin(
    private val now: Boolean = true,
    var pattern: String? = null
) : BaseMatcher<Any>(), ParametrizedMatcher {
    private lateinit var period: TemporalAmount
    private lateinit var expected: ZonedDateTime
    private var parseError = false

    override fun matches(item: Any) = date(item, pattern)
        .map { isBetweenInclusive(expected.minus(period), expected.plus(period), it) }
        .getOrElse {
            logger.warn("Parsing error: $item, expected to match pattern '$pattern'", it)
            parseError = true
            false
        }

    private fun isBetweenInclusive(start: ZonedDateTime, end: ZonedDateTime, target: ZonedDateTime): Boolean =
        !target.isBefore(start) && !target.isAfter(end)

    override fun describeTo(description: Description) {
        description.appendValue(period)
    }

    override fun describeMismatch(item: Any, description: Description) {
        if (parseError) {
            description.appendText("The date is not properly formatted ").appendValue(pattern)
        } else {
            description.appendText("The date is not within ").appendValue(expected.minus(period)..expected.plus(period))
        }
    }

    override fun setParameter(p: String) {
        val params = p.split(PARAMS_SEPARATOR)
        if (params[0].isNotBlank()) {
            this.pattern = params[0]
        }
        this.period = parsePeriod(params[1])
        this.expected = if (now) ZonedDateTime.now() else params[2].parseDate().toZonedDateTime()
    }

    companion object : KLogging() {
        internal const val PARAMS_SEPARATOR = "|$|"
    }
}

open class DateWithin : BaseMatcher<Any?>(), ParametrizedMatcher {
    private lateinit var period: TemporalAmount
    private lateinit var expected: LocalDateTime

    override fun matches(item: Any?) = isBetweenInclusive(
        expected.minus(period),
        expected.plus(period),
        when (item) {
            is Date -> item.toLocalDateTime()
            is LocalDate -> item.atStartOfDay()
            is LocalDateTime -> item
            is ZonedDateTime -> item.toLocalDateTime()
            else -> null
        }
    )

    private fun isBetweenInclusive(start: LocalDateTime, end: LocalDateTime, target: LocalDateTime?): Boolean =
        target != null && !target.isBefore(start) && !target.isAfter(end)

    override fun describeTo(description: Description) {
        description.appendValue(period)
    }

    override fun describeMismatch(item: Any, description: Description) {
        description.appendText("The date is not within ").appendValue(expected.minus(period)..expected.plus(period))
    }

    override fun setParameter(p: String) {
        val params = p.split(PARAMS_SEPARATOR)
        if (params[0].isNotBlank()) {
            period = parsePeriod(params[0])
        }
        expected = if (params.size == 2) params[1].parseDate().toLocalDateTime() else LocalDateTime.now()
    }

    companion object : KLogging() {
        internal const val PARAMS_SEPARATOR = "|$|"
    }
}

class XMLDateWithin : BaseMatcher<Any>(), ParametrizedMatcher {
    private lateinit var period: TemporalAmount

    override fun matches(item: Any): Boolean {
        val xmlGregorianCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(item as String)
        val actual = xmlGregorianCal.toGregorianCalendar().toZonedDateTime()
        val expected = ZonedDateTime.now()
        return isBetweenInclusive(expected.minus(period), expected.plus(period), actual)
    }

    private fun isBetweenInclusive(start: ZonedDateTime, end: ZonedDateTime, target: ZonedDateTime): Boolean =
        !target.isBefore(start) && !target.isAfter(end)

    override fun describeTo(description: Description) {
        description.appendValue(period)
    }

    override fun describeMismatch(item: Any, description: Description) {
        description.appendText("The date should be within ").appendValue(period)
    }

    override fun setParameter(within: String) {
        period = parsePeriod(within)
    }
}

class After : ExpectedDateMatcher("The date should be after ", { expected, actual -> actual.isAfter(expected) })
class Before : ExpectedDateMatcher("The date should be before ", { expected, actual -> actual.isBefore(expected) })

abstract class ExpectedDateMatcher(
    private val mismatchDesc: String,
    val check: (expected: ZonedDateTime, actual: ZonedDateTime) -> Boolean
) : BaseMatcher<Any?>(), ParametrizedMatcher {
    protected lateinit var expected: ZonedDateTime

    override fun matches(item: Any?) = date(item).map { check(expected, it) }.getOrDefault(false)

    override fun describeTo(description: Description) {
        description.appendValue(expected)
    }

    override fun describeMismatch(item: Any, description: Description) {
        description.appendText(mismatchDesc).appendValue(expected)
    }

    override fun setParameter(date: String) {
        expected = date(date).getOrThrow()
    }
}

class IsUuid : BaseMatcher<Any?>() {
    override fun matches(a: Any?) = try {
        a is String && UUID.fromString(a) != null
    } catch (ignore: Exception) {
        false
    }

    override fun describeTo(description: Description) {
        description.appendText("UUID value")
    }
}

class IsBool : BaseMatcher<Any?>() {
    override fun matches(a: Any?) = when (a) {
        is Boolean -> true
        is String -> a.toBooleanStrictOrNull()?.let { true } ?: false
        is Number -> a == 1 || a == 0
        else -> false
    }

    override fun describeTo(description: Description) {
        description.appendText("boolean value")
    }
}

class IsNumber : BaseMatcher<Any?>() {
    override fun matches(a: Any?) = when (a) {
        is Number -> true
        is String -> matchesRegex("^\\d+\$").matches(a)
        else -> false
    }

    override fun describeTo(description: Description) {
        description.appendText("number value")
    }
}

class Ignore : BaseMatcher<Any?>() {
    override fun matches(a: Any?) = true

    override fun describeTo(description: Description) {
        description.appendText("ignored value")
    }
}

class NotNull : BaseMatcher<Any?>() {
    override fun matches(a: Any?) = a != null

    override fun describeTo(description: Description) {
        description.appendText("not null value")
    }
}

class MapContentMatchers<T, V>(
    private val key: T,
    private val valueMatcher: Matcher<V>
) : TypeSafeMatcher<Map<in T, V>>() {

    override fun matchesSafely(item: Map<in T, V>): Boolean = item.containsKey(key) && valueMatcher.matches(item[key])

    override fun describeTo(description: Description) {
        description.appendText("an entry with key ").appendValue(key)
            .appendText(" and value matching ").appendDescriptionOf(valueMatcher)
    }

    companion object {
        fun <T, V> hasAllEntries(entries: Map<T, V>): Matcher<Map<T, V>> = allOf(
            entries.map { (k, v) -> if (v is Matcher<*>) hasEntry(`is`(k), v as Matcher<V>) else hasEntry(k, v) }
        )
    }
}

fun within(date: String) = DateWithin().apply { setParameter(date) }
fun uuid() = IsUuid()
fun bool() = IsBool()
fun number() = IsNumber()
fun string(): Matcher<String> = matchesRegex("^[a-zA-Z0-9_ ]*\$")
fun ignore() = Ignore()
fun notNull() = NotNull()
fun after(date: String) = After().apply { setParameter(date) }
fun before(date: String) = Before().apply { setParameter(date) }
