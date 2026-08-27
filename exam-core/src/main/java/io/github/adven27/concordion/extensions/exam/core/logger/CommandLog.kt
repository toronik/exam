package io.github.adven27.concordion.extensions.exam.core.logger

import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import mu.KLogging
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * One line per command executed, and nothing else.
 *
 * A run used to say almost nothing about what it did and a great deal about what went wrong inside
 * libraries: on a green run of the framework's own example suite, some 2000 of 2620 console lines
 * were stack traces, and under `await` each one repeated per poll attempt. What it did not say was
 * which commands actually ran - so a spec whose instrumentation was quietly not picked up looked
 * exactly like a spec that passed.
 *
 * This is the other half of that: the log becomes a list of what ran and how it went, which a reader
 * can compare against the spec they wrote. The stack traces are not lost - they stay in the
 * per-example `.log` and the HTML log viewer, where someone who wants them knows to look.
 */
object CommandLog : KLogging() {

    private val attempts = ThreadLocal.withInitial { AtomicInteger() }

    /**
     * Handed to awaitility while the condition is being built, which happens on the test thread -
     * awaitility then polls on one of its own, so a thread-local read from there would count nothing.
     */
    fun attemptsOfThisCommand(): AtomicInteger = attempts.get()

    fun startCommand() = attempts.set(AtomicInteger())

    fun succeeded(name: String, target: String, took: Duration) =
        logger.info { line(name, target, "ok", took, attempts.get().get()) }

    fun failed(name: String, target: String, took: Duration, cause: Throwable?) {
        logger.info { line(name, target, "FAIL", took, attempts.get().get()) }
        // Only a thrown failure carries its own message here; a mismatch recorded through the
        // ResultRecorder renders into the HTML and the report instead, so there is nothing to add.
        cause?.rootCauseMessage()?.takeIf { it.isNotBlank() }?.let { message ->
            message.lines().take(MAX_MESSAGE_LINES).forEach { logger.info { "$PREFIX${" ".repeat(INDENT)}${it.trim()}" } }
        }
    }

    internal fun line(name: String, target: String, verdict: String, took: Duration, attempts: Int): String {
        val retried = attempts.takeIf { it > 1 }?.let { "  $it attempts" } ?: ""
        return PREFIX + name.padEnd(NAME) + target.take(TARGET).padEnd(TARGET) +
            verdict.padStart(VERDICT) + took.human().padStart(TIME) + retried
    }

    private fun Duration.human() = when {
        toMillis() < MILLIS_IN_SECOND -> "${toMillis()}ms"
        else -> "%.1fs".format(toMillis() / MILLIS_IN_SECOND.toDouble())
    }

    private const val PREFIX = "[exam] "
    private const val NAME = 10
    private const val TARGET = 32
    private const val VERDICT = 5
    private const val TIME = 8
    private const val INDENT = NAME + 2
    private const val MAX_MESSAGE_LINES = 4
    private const val MILLIS_IN_SECOND = 1000
}
