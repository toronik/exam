package io.github.adven27.concordion.extensions.exam.core.logger

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.spi.FilterReply.DENY
import ch.qos.logback.core.spi.FilterReply.NEUTRAL
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class ConsoleQuietFilterTest {

    private val filter = ConsoleQuietFilter()

    @After
    fun tearDown() {
        System.clearProperty(ConsoleQuietFilter.PROP)
    }

    @Test
    fun `exam speaks, so it is always let through`() {
        assertThat(filter.decide(event("io.github.adven27.exam.Whatever", Level.INFO))).isEqualTo(NEUTRAL)
        assertThat(filter.decide(event("io.github.adven27.exam.Whatever", Level.DEBUG))).isEqualTo(NEUTRAL)
    }

    @Test
    fun `start-up chatter of whatever the spec boots is dropped`() {
        assertThat(filter.decide(event("org.springframework.boot.SpringApplication", Level.INFO))).isEqualTo(DENY)
        assertThat(filter.decide(event("com.zaxxer.hikari.HikariDataSource", Level.DEBUG))).isEqualTo(DENY)
    }

    @Test
    fun `a warning from anywhere still reaches the console`() {
        assertThat(filter.decide(event("org.dbunit.assertion.DbUnitAssertBase", Level.WARN))).isEqualTo(NEUTRAL)
        assertThat(filter.decide(event("org.dbunit.assertion.DbUnitAssertBase", Level.ERROR))).isEqualTo(NEUTRAL)
    }

    @Test
    fun `asking for verbose puts the console back the way it was`() {
        System.setProperty(ConsoleQuietFilter.PROP, ConsoleQuietFilter.VERBOSE)

        assertThat(filter.decide(event("org.springframework.boot.SpringApplication", Level.INFO))).isEqualTo(NEUTRAL)
    }

    private fun event(logger: String, level: Level): ILoggingEvent = LoggingEvent().apply {
        loggerName = logger
        this.level = level
        message = "whatever"
    }
}
