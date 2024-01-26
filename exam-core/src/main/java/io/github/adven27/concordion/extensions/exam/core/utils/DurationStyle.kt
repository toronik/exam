package io.github.adven27.concordion.extensions.exam.core.utils

import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.TimeUnit.Companion.fromChronoUnit
import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.TimeUnit.Companion.fromSuffix
import java.time.Duration
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Duration format styles.
 */
enum class DurationStyle(pattern: String) {
    /**
     * Simple formatting, for example '1s'.
     */
    SIMPLE("^([+-]?\\d+)([a-zA-Z]{0,2})$") {
        override fun parse(value: String, unit: ChronoUnit?): TemporalAmount = try {
            val matcher = matcher(value)
            require(matcher.matches()) { "Does not match simple duration pattern" }
            val suffix = matcher.group(2)
            (if (suffix.isNotBlank()) fromSuffix(suffix) else fromChronoUnit(unit)).parse(matcher.group(1))
        } catch (expected: Exception) {
            throw IllegalArgumentException("'$value' is not a valid simple duration", expected)
        }
    },

    /**
     * ISO-8601 formatting.
     */
    ISO8601("^[+-]?[pP].*$") {
        override fun parse(value: String, unit: ChronoUnit?): TemporalAmount = try {
            Duration.parse(value)
        } catch (expected: Exception) {
            throw IllegalArgumentException("'$value' is not a valid ISO-8601 duration", expected)
        }
    };

    private val pattern: Pattern = Pattern.compile(pattern)

    protected fun matches(value: String): Boolean = pattern.matcher(value).matches()
    protected fun matcher(value: String): Matcher = pattern.matcher(value)

    /**
     * Parse the given value to a duration.
     * @param value the value to parse
     * @return a duration
     */
    fun parse(value: String): TemporalAmount = parse(value, null)

    /**
     * Parse the given value to a duration.
     * @param value the value to parse
     * @param unit the duration unit to use if the value doesn't specify one (`null`
     * will default to ms)
     * @return a duration
     */
    abstract fun parse(value: String, unit: ChronoUnit?): TemporalAmount

    internal enum class TimeUnit(private val chronoUnit: ChronoUnit, private val suffix: String) {
        NANOS(ChronoUnit.NANOS, "ns"),
        MICROS(ChronoUnit.MICROS, "us"),
        MILLIS(ChronoUnit.MILLIS, "ms"),
        SECONDS(ChronoUnit.SECONDS, "s"),
        MINUTES(ChronoUnit.MINUTES, "m"),
        HOURS(ChronoUnit.HOURS, "h"),
        DAYS(ChronoUnit.DAYS, "d"),
        WEEKS(ChronoUnit.WEEKS, "w"),
        MONTHS(ChronoUnit.MONTHS, "M"),
        YEARS(ChronoUnit.YEARS, "y");

        fun parse(value: String): TemporalAmount = when (chronoUnit) {
            ChronoUnit.WEEKS -> Period.ofWeeks(value.toInt())
            ChronoUnit.MONTHS -> Period.ofMonths(value.toInt())
            ChronoUnit.YEARS -> Period.ofYears(value.toInt())
            else -> Duration.of(value.toLong(), chronoUnit)
        }

        companion object {
            fun fromChronoUnit(unit: ChronoUnit?): TimeUnit = unit
                ?.let { entries.firstOrNull { it.chronoUnit == unit } ?: error("Unknown unit $unit") }
                ?: MILLIS

            fun fromSuffix(suffix: String): TimeUnit =
                requireNotNull(entries.firstOrNull { it.suffix == suffix }) { "Unknown unit '$suffix'" }
        }
    }

    companion object {
        /**
         * Detect the style then parse the value to return a duration.
         * @param value the value to parse
         * @param unit the duration unit to use if the value doesn't specify one (`null`
         * will default to ms)
         * @return the parsed duration
         * @throws IllegalArgumentException if the value is not a known style or cannot be
         * parsed
         */
        @JvmOverloads
        fun detectAndParse(value: String, unit: ChronoUnit? = null) = detect(value).parse(value, unit)

        /**
         * Detect the style from the given source value.
         * @param value the source value
         * @return the duration style
         * @throws IllegalArgumentException if the value is not a known style
         */
        fun detect(value: String): DurationStyle =
            requireNotNull(entries.firstOrNull { it.matches(value) }) { "'$value' is not a valid duration" }
    }
}
