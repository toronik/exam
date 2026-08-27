package io.github.adven27.concordion.extensions.exam.core.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AgentReportRendererTest {

    @Test
    fun `all passing specs`() {
        val results = listOf(
            SpecResult(
                "specs/Foo.adoc",
                Status.PASS,
                listOf(
                ExampleResult("ex1", Status.PASS, emptyList(), null)
            ),
                0
            ),
            SpecResult(
                "specs/Bar.adoc",
                Status.PASS,
                listOf(
                ExampleResult("ex1", Status.PASS, emptyList(), null)
            ),
                0
            )
        )
        val report = AgentReportRenderer.render(results)

        assertThat(report).contains("total: 2", "passed: 2", "failed: 0")
        assertThat(report).doesNotContain("# Failed")
        assertThat(report).contains("# Passed (2)")
    }

    @Test
    fun `failure with error message and html link`() {
        val results = listOf(
            SpecResult(
                "specs/Conflict.adoc",
                Status.FAIL,
                listOf(
                ExampleResult(
                    "resolve mismatch",
                    Status.FAIL,
                    listOf(
                    Failure(
                        "http",
                        "POST /conflicts/1/resolve",
                        "Status code mismatch\nExpected: <200>\nbut: was <404>",
                        "HTTP/1.1 200\n{\"result\":\"SUCCESS\"}",
                        "HTTP/1.1 404\n{\"code\":\"NOT_FOUND\"}",
                        "error-1234567890"
                    )
                ),
                    null
                )
            ),
                0
            )
        )
        val report = AgentReportRenderer.render(results)

        assertThat(report).contains("`http` POST /conflicts/1/resolve")
        assertThat(report).contains("**Error:**")
        assertThat(report).contains("Status code mismatch")
        assertThat(report).contains("**Details:** specs/Conflict.html#error-1234567890")
    }

    @Test
    fun `multiple failures in one example are numbered`() {
        val results = listOf(
            SpecResult(
                "specs/Multi.adoc",
                Status.FAIL,
                listOf(
                ExampleResult(
                    "two checks",
                    Status.FAIL,
                    listOf(
                    Failure("http", "", "first error", "", "", "error-1"),
                    Failure("http", "", "second error", "", "", "error-2")
                ),
                    null
                )
            ),
                0
            )
        )
        val report = AgentReportRenderer.render(results)

        assertThat(report).contains("`http` [1/2]")
        assertThat(report).contains("`http` [2/2]")
    }

    @Test
    fun `truncates long values`() {
        val results = listOf(
            SpecResult(
                "specs/Long.adoc",
                Status.FAIL,
                listOf(
                ExampleResult(
                    "big",
                    Status.FAIL,
                    listOf(
                    Failure("http", "", "err", "x".repeat(300), "y".repeat(50), "")
                ),
                    null
                )
            ),
                0
            )
        )
        val report = AgentReportRenderer.render(results)

        assertThat(report).contains("(300 chars)")
        assertThat(report).doesNotContain("x".repeat(300))
    }
}
