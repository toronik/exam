package io.github.adven27.concordion.extensions.exam.core.report

import org.assertj.core.api.Assertions.assertThat
import org.concordion.internal.ConcordionAssertionError
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class AgentReportHintRuleTest {

    @Test
    fun `a passing example is untouched`() {
        var ran = false
        rule().apply(statement { ran = true }, DESCRIPTION).evaluate()

        assertThat(ran).isTrue()
    }

    @Test
    fun `Concordion's verdict is replaced by the failure and the instruction, with no stack trace`() {
        val thrown = catching(ConcordionAssertionError(SPEC_HAS_FAILURES, null))

        assertThat(thrown).isInstanceOf(SpecFailure::class.java)
        assertThat(thrown.message).contains("Example \"Example 1. Book max\"", "Expected 3 rows, but was 2")
        assertThat(thrown.message).contains("agent-report.md")
        assertThat(thrown.stackTrace).isEmpty()
        assertThat(thrown.cause).isNull()
    }

    @Test
    fun `an assertion from fixture code keeps its own message and trace`() {
        val original = AssertionError("expected true but was false")

        val thrown = catching(original)

        assertThat(thrown).isSameAs(original)
        assertThat(thrown.stackTrace).isNotEmpty()
    }

    @Test
    fun `a failure other than an assertion is rethrown as it is`() {
        val original = IllegalStateException("db is gone")

        assertThat(catching(original)).isSameAs(original)
    }

    @Test
    fun `with the report switched off the verdict is left alone`() {
        val verdict = ConcordionAssertionError(SPEC_HAS_FAILURES, null)

        val thrown = catching(verdict, AgentReportHintRule(AgentReportAccumulator()) { SNAPSHOT })

        assertThat(thrown).isSameAs(verdict)
    }

    @Test
    fun `a result recorded for another example is not described as this one's`() {
        val nested = CurrentExample.Snapshot(
            spec = "MqCheckFailures.adoc",
            example = ExampleResult("Example 7. Big message", Status.FAIL, emptyList(), null),
            expectedToFail = false
        )

        val thrown = catching(ConcordionAssertionError(SPEC_HAS_FAILURES, null), rule { nested })

        assertThat(thrown.message).startsWith(SPEC_HAS_FAILURES)
        assertThat(thrown.message).doesNotContain("Big message", "MqCheckFailures")
        assertThat(thrown.message).contains("agent-report.md")
    }

    @Test
    fun `without a recorded example the verdict is still pointed at the report`() {
        val thrown = catching(ConcordionAssertionError(SPEC_HAS_FAILURES, null), rule { null })

        assertThat(thrown).isInstanceOf(SpecFailure::class.java)
        assertThat(thrown.message).startsWith(SPEC_HAS_FAILURES)
        assertThat(thrown.message).contains("agent-report.md")
    }

    private fun catching(t: Throwable, rule: AgentReportHintRule = rule()): Throwable =
        runCatching { rule.apply(statement { throw t }, DESCRIPTION).evaluate() }.exceptionOrNull()!!

    private fun rule(current: () -> CurrentExample.Snapshot? = { SNAPSHOT }) =
        AgentReportHintRule(configured, current)

    private fun statement(body: () -> Unit) = object : Statement() {
        override fun evaluate() = body()
    }

    private companion object {
        const val SPEC_HAS_FAILURES = "Specification has failure(s). See output HTML for details."

        /**
         * Pointed at a real directory: [AgentReportAccumulator.configure] arms a shutdown hook that
         * writes there, and one per test would arm several.
         */
        val configured = AgentReportAccumulator().apply {
            configure(createTempDirectory("exam-agent-report-test") as Path)
        }

        val DESCRIPTION: Description = Description.createTestDescription("specs.Specs", "Example 1. Book max")

        val SNAPSHOT = CurrentExample.Snapshot(
            spec = "DbCheck.adoc",
            example = ExampleResult(
                "Example 1. Book max",
                Status.FAIL,
                listOf(Failure("db-check", "TABLE books", "Expected 3 rows, but was 2", "3", "2", "error-7")),
                null
            ),
            expectedToFail = false
        )
    }
}
