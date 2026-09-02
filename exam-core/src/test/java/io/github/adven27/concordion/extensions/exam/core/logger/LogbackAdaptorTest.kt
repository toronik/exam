package io.github.adven27.concordion.extensions.exam.core.logger

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.slf4j.MDC

class LogbackAdaptorTest {

    private val adaptor = LogbackAdaptor()

    @Test
    fun `a name that fits is the name of the log file`() {
        assertThat(pathOf("Example 1. Dummy")).endsWith("[Example 1. Dummy]")
    }

    /**
     * Abbreviating to initials makes "… - a person" and "… - a crowd" the same file, which fanning an
     * example out turns from a curiosity into the normal case: the two cases of one scenario would
     * write into one log, and the log of a failure is where its diagnosis starts.
     */
    @Test
    fun `two long names that abbreviate alike still get a log file each`() {
        val one = pathOf("Example 4. Outline values in included content — a person")
        val other = pathOf("Example 4. Outline values in included content — a crowd")

        assertThat(one).isNotEqualTo(other)
    }

    @Test
    fun `the same name always gets the same log file`() {
        assertThat(pathOf("Example 4. Outline values in included content — a person"))
            .isEqualTo(pathOf("Example 4. Outline values in included content — a person"))
    }

    private fun pathOf(exampleName: String): String {
        adaptor.startExampleLogFile("/specs/Specs.adoc", exampleName)
        return MDC.get("testname").also { adaptor.stopLogFile() }
    }
}
