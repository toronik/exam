package io.github.adven27.concordion.extensions.exam.core.utils

import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.TimeUnit.Companion.fromChronoUnit
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.function.Function
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
        override fun parse(value: String, unit: ChronoUnit?): Duration = try {
            val matcher = matcher(value)
            require(matcher.matches()) { "Does not match simple duration pattern" }
            val suffix = matcher.group(2)
            (if (suffix.isNotBlank()) TimeUnit.fromSuffix(suffix) else fromChronoUnit(unit)).parse(matcher.group(1))
        } catch (expected: Exception) {
            throw IllegalArgumentException("'$value' is not a valid simple duration", expected)
        }

        override fun print(value: Duration, unit: ChronoUnit?) = fromChronoUnit(unit).print(value)
    },

    /**
     * ISO-8601 formatting.
     */
    ISO8601("^[+-]?[pP].*$") {
        override fun parse(value: String, unit: ChronoUnit?): Duration = try {
            Duration.parse(value)
        } catch (expected: Exception) {
            throw IllegalArgumentException("'$value' is not a valid ISO-8601 duration", expected)
        }

        override fun print(value: Duration, unit: ChronoUnit?) = value.toString()
    };

    private val pattern: Pattern

    init {
        this.pattern = Pattern.compile(pattern)
    }

    protected fun matches(value: String): Boolean = pattern.matcher(value).matches()
    protected fun matcher(value: String): Matcher = pattern.matcher(value)

    /**
     * Parse the given value to a duration.
     * @param value the value to parse
     * @return a duration
     */
    fun parse(value: String): Duration = parse(value, null)

    /**
     * Parse the given value to a duration.
     * @param value the value to parse
     * @param unit the duration unit to use if the value doesn't specify one (`null`
     * will default to ms)
     * @return a duration
     */
    abstract fun parse(value: String, unit: ChronoUnit?): Duration

    /**
     * Print the specified duration.
     * @param value the value to print
     * @return the printed result
     */
    fun print(value: Duration): String = print(value, null)

    /**
     * Print the specified duration using the given unit.
     * @param value the value to print
     * @param unit the value to use for printing
     * @return the printed result
     */
    abstract fun print(value: Duration, unit: ChronoUnit?): String

    /**
     * Units that we support.
     */
    internal enum class TimeUnit(
        private val chronoUnit: ChronoUnit,
        private val suffix: String,
        private val longValue: Function<Duration, Long>
    ) {
        /**
         * Nanoseconds.
         */
        NANOS(ChronoUnit.NANOS, "ns", Function { obj: Duration -> obj.toNanos() }),

        /**
         * Microseconds.
         */
        MICROS(ChronoUnit.MICROS, "us", Function { duration: Duration -> duration.toNanos() / MICRO_SEC }),

        /**
         * Milliseconds.
         */
        MILLIS(ChronoUnit.MILLIS, "ms", Function { obj: Duration -> obj.toMillis() }),

        /**
         * Seconds.
         */
        SECONDS(ChronoUnit.SECONDS, "s", Function { obj: Duration -> obj.seconds }),

        /**
         * Minutes.
         */
        MINUTES(ChronoUnit.MINUTES, "m", Function { obj: Duration -> obj.toMinutes() }),

        /**
         * Hours.
         */
        HOURS(ChronoUnit.HOURS, "h", Function { obj: Duration -> obj.toHours() }),

        /**
         * Days.
         */
        DAYS(ChronoUnit.DAYS, "d", Function { obj: Duration -> obj.toDays() });

        fun parse(value: String) = Duration.of(value.toLong(), chronoUnit)
        fun print(value: Duration) = longValue(value).toString() + suffix
        fun longValue(value: Duration) = longValue.apply(value)

        companion object {

            fun fromChronoUnit(chronoUnit: ChronoUnit?): TimeUnit {
                if (chronoUnit == null) {
                    return MILLIS
                }
                for (candidate in values()) {
                    if (candidate.chronoUnit == chronoUnit) {
                        return candidate
                    }
                }
                throw IllegalArgumentException("Unknown unit $chronoUnit")
            }

            fun fromSuffix(suffix: String): TimeUnit {
                for (candidate in values()) {
                    if (candidate.suffix.equals(suffix, ignoreCase = true)) {
                        return candidate
                    }
                }
                throw IllegalArgumentException("Unknown unit '$suffix'")
            }
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
        fun detect(value: String): DurationStyle {
            for (candidate in values()) {
                if (candidate.matches(value)) {
                    return candidate
                }
            }
            throw IllegalArgumentException("'$value' is not a valid duration")
        }
    }
}

private const val MICRO_SEC = 1000L
