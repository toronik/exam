package io.github.adven27.concordion.extensions.exam.core.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path

class AgentReportHintTest {

    private val report = Path.of("/w/build/reports/specs/agent-report.md")

    @Test
    fun `failure message names the example and what it got wrong`() {
        val message = AgentReportHint.forFailure(CONCORDION_SAYS, failing(), report)

        assertThat(message).contains("DbCheck.adoc | Example \"Book max\" | failed 1 of its checks")
        assertThat(message).contains("db-check TABLE books")
        assertThat(message).contains("Expected 3 rows, but was 2")
        assertThat(message).contains("expected: 3")
        assertThat(message).contains("actual:   2")
    }

    @Test
    fun `failure message says where the whole run is and how to read it`() {
        val message = AgentReportHint.forFailure(CONCORDION_SAYS, failing(), report)

        assertThat(message).contains("/w/build/reports/specs/agent-report.md")
        assertThat(message).contains("ONE markdown file")
        assertThat(message).contains("failed: 0")
        assertThat(message).contains("specs/Foo.html#error-42")
    }

    @Test
    fun `long values are cut and point at the report for the rest`() {
        val long = "x".repeat(300)
        val spec = specWith(Failure("http", "POST /books", "boom", long, "y", "error-1"))

        val message = AgentReportHint.forFailure(CONCORDION_SAYS, spec, report)

        assertThat(message).doesNotContain(long)
        assertThat(message).contains("(300 chars, full value in the report)")
    }

    @Test
    fun `only the first checks are inlined, the count of the rest is stated`() {
        val many = (1..5).map { Failure("http", "POST /books/$it", "boom $it", "", "", "error-$it") }
        val spec = CurrentExample.Snapshot("specs/Many.adoc", ExampleResult("many", Status.FAIL, many, null), false)

        val message = AgentReportHint.forFailure(CONCORDION_SAYS, spec, report)

        assertThat(message).contains("boom 1", "boom 2", "boom 3")
        assertThat(message).doesNotContain("boom 4")
        assertThat(message).contains("(and 2 more check(s), all of them in the report)")
    }

    @Test
    fun `an example that threw is reported as thrown, with the first line of the exception`() {
        val spec = CurrentExample.Snapshot(
            "specs/Boom.adoc",
            ExampleResult("boom", Status.ERROR, emptyList(), "java.lang.IllegalStateException: no db\n\tat x.y(Z.kt:1)"),
            false
        )

        val message = AgentReportHint.forFailure(CONCORDION_SAYS, spec, report)

        assertThat(message).contains("Example \"boom\" | threw")
        assertThat(message).contains("java.lang.IllegalStateException: no db")
        assertThat(message).doesNotContain("at x.y(Z.kt:1)")
    }

    @Test
    fun `an expected-to-fail example keeps the original verdict - it threw for the opposite reason`() {
        val message = AgentReportHint.forFailure(
            CONCORDION_EXPECTED_TO_PASS,
            snapshot(ExampleResult("Book max", Status.FAIL, emptyList(), null), expectedToFail = true),
            report
        )

        assertThat(message).startsWith(CONCORDION_EXPECTED_TO_PASS)
        assertThat(message).doesNotContain("Book max")
        assertThat(message).contains("ONE markdown file")
    }

    @Test
    fun `with no parsed result the original verdict is kept and the instruction still given`() {
        val message = AgentReportHint.forFailure(CONCORDION_SAYS, null, report)

        assertThat(message).startsWith(CONCORDION_SAYS)
        assertThat(message).contains("ONE markdown file")
    }

    @Test
    fun `with no report there is nothing to point at, so the message is left alone`() {
        val message = AgentReportHint.forFailure(CONCORDION_SAYS, failing(), null)

        assertThat(message).doesNotContain("markdown file")
        assertThat(message).contains("Example \"Book max\"")
    }

    @Test
    fun `the console line tooling greps for is unchanged`() {
        val console = AgentReportHint.forConsole(report, 3, 58)

        assertThat(console).startsWith("[exam] Agent report: /w/build/reports/specs/agent-report.md (3 failed / 58 total)\n")
        assertThat(console).contains("[exam] Every failure of this run is in ONE markdown file.")
    }

    private fun failing() = specWith(
        Failure("db-check", "TABLE books", "Expected 3 rows, but was 2", "3", "2", "error-7")
    )

    private fun specWith(failure: Failure) = snapshot(
        ExampleResult("Book max", Status.FAIL, listOf(failure), null)
    )

    private fun snapshot(example: ExampleResult, expectedToFail: Boolean = false) =
        CurrentExample.Snapshot("DbCheck.adoc", example, expectedToFail)

    private companion object {
        const val CONCORDION_SAYS = "Specification has failure(s). See output HTML for details."
        const val CONCORDION_EXPECTED_TO_PASS =
            "Specification is expected to fail but has neither failures nor exceptions"
    }
}
