package io.github.adven27.concordion.extensions.exam.core.logger

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration

class CommandLogTest {

    @Test
    fun `a command that worked takes one line, in columns a reader can scan`() {
        val line = CommandLog.line("db-check", "product", "ok", Duration.ofMillis(12), attempts = 1)

        assertThat(line).isEqualTo("[exam] db-check  product                            ok    12ms")
    }

    @Test
    fun `the verdict sits in the same column whether it passed or failed`() {
        val ok = CommandLog.line("db-check", "product", "ok", Duration.ofMillis(12), attempts = 1)
        val failed = CommandLog.line("db-check", "product", "FAIL", Duration.ofMillis(12), attempts = 1)

        assertThat(ok.indexOf("ok") + "ok".length).isEqualTo(failed.indexOf("FAIL") + "FAIL".length)
    }

    @Test
    fun `a retried check says how many attempts it took, instead of one report per attempt`() {
        val line = CommandLog.line("mq-check", "myQueue", "FAIL", Duration.ofMillis(4100), attempts = 4)

        assertThat(line).endsWith("4 attempts")
        assertThat(line).contains("4.1s")
    }

    @Test
    fun `a single attempt is not worth mentioning`() {
        assertThat(CommandLog.line("eq", "#dtJson", "ok", Duration.ofMillis(3), attempts = 1))
            .doesNotContain("attempt")
    }

    @Test
    fun `sub-second durations stay in milliseconds, longer ones read as seconds`() {
        assertThat(CommandLog.line("eq", "x", "ok", Duration.ofMillis(999), attempts = 1)).contains("999ms")
        assertThat(CommandLog.line("eq", "x", "ok", Duration.ofMillis(1000), attempts = 1)).contains("1.0s")
    }

    @Test
    fun `an overlong target is cut rather than pushing the columns apart`() {
        val long = CommandLog.line("http", "POST /" + "x".repeat(80), "ok", Duration.ofMillis(1), attempts = 1)
        val short = CommandLog.line("http", "POST /x", "ok", Duration.ofMillis(1), attempts = 1)

        assertThat(long).hasSameSizeAs(short)
    }
}
