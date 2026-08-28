package io.github.adven27.concordion.extensions.exam.core.logger

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.spi.FilterReply.DENY
import ch.qos.logback.core.spi.FilterReply.NEUTRAL

/**
 * Keeps the console to what the run did, and leaves the forensics to the files.
 *
 * This half drops the start-up chatter of whatever the spec boots - Spring, Tomcat, Hikari,
 * Liquibase - which said nothing about the run. The stack traces, which were the bulk of it, are
 * dropped by `%nopex` in the console pattern instead: they mostly belong to verification mismatches,
 * which exam reports as a message anyway, and under `await` they repeated per poll attempt.
 *
 * Nothing is lost. Both apply to the console appender only; the per-example `.log` and the HTML log
 * viewer still receive every event with its trace, and a verdict also reaches the reader through the
 * command line printed by [CommandLog], the HTML report and `agent-report.md`. Set
 * `-Dexam.log=verbose` to put the console back the way it was.
 */
class ConsoleQuietFilter : Filter<ILoggingEvent>() {

    override fun decide(event: ILoggingEvent): FilterReply = when {
        verbose -> NEUTRAL
        event.loggerName.startsWith(EXAM) -> NEUTRAL
        // Start-up chatter of whatever the spec boots - Spring, Tomcat, Hikari, Liquibase. A
        // warning or worse still gets through.
        event.level.toInt() < Level.WARN.toInt() -> DENY
        else -> NEUTRAL
    }

    companion object {
        const val PROP = "exam.log"
        const val VERBOSE = "verbose"
        private const val EXAM = "io.github.adven27"

        private val verbose: Boolean get() = System.getProperty(PROP, "").equals(VERBOSE, ignoreCase = true)
    }
}
