package io.github.adven27.concordion.extensions.exam.core.report

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME

object AgentReportRenderer {

    fun render(results: List<SpecResult>): String {
        val failed = results.filter { it.status == Status.FAIL }
        val errors = results.filter { it.status == Status.ERROR }
        val passed = results.filter { it.status == Status.PASS }

        return buildString {
            appendFrontmatter(results.size, passed.size, failed.size, errors.size)
            if (failed.isNotEmpty() || errors.isNotEmpty()) {
                appendLine("# Failed")
                appendLine()
                (failed + errors).forEach { appendFailedSpec(it) }
            }
            appendLine("# Passed (${passed.size})")
            appendLine()
            passed.forEach { appendLine("${it.resource} | ${it.examples.size} examples") }
        }.trimEnd() + "\n"
    }

    private fun StringBuilder.appendFrontmatter(total: Int, passed: Int, failed: Int, errors: Int) {
        appendLine("---")
        appendLine("timestamp: ${LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)}")
        appendLine("total: $total")
        appendLine("passed: $passed")
        appendLine("failed: $failed")
        appendLine("errors: $errors")
        appendLine("---")
        appendLine()
    }

    private fun StringBuilder.appendFailedSpec(spec: SpecResult) {
        val htmlFile = spec.resource.replace(".adoc", ".html")
        val failedExamples = spec.examples.count { it.status != Status.PASS }
        appendLine("## ${spec.resource} — ${spec.status} ($failedExamples/${spec.examples.size} examples)")
        appendLine()
        spec.examples.filter { it.status != Status.PASS }.forEach { appendFailedExample(it, htmlFile) }
    }

    private fun StringBuilder.appendFailedExample(example: ExampleResult, htmlFile: String) {
        appendLine("### Example: \"${example.name}\"")
        example.failures.forEachIndexed { i, f -> appendFailure(f, i, example.failures.size, htmlFile) }
        example.error?.let { appendException(it) }
        appendLine()
    }

    private fun StringBuilder.appendFailure(f: Failure, index: Int, total: Int, htmlFile: String) {
        val idx = if (total > 1) " [${index + 1}/$total]" else ""
        val ctx = if (f.context.isNotBlank()) " ${f.context}" else ""
        appendLine("- **Command:** `${f.command}`$ctx$idx")
        if (f.message.isNotBlank()) appendLine("- **Error:** ${formatMessage(f.message)}")
        if (f.expected.isNotBlank() || f.actual.isNotBlank()) {
            appendLine("- **Expected:** ${truncate(f.expected)}")
            appendLine("- **Actual:** ${truncate(f.actual)}")
        }
        if (f.htmlAnchor.isNotBlank()) appendLine("- **Details:** $htmlFile#${f.htmlAnchor}")
    }

    private fun StringBuilder.appendException(error: String) {
        appendLine("- **Error:** `${error.lines().first()}`")
        error.lines().drop(1).take(3).filter { it.trimStart().startsWith("at ") }.forEach {
            appendLine("  - `${it.trim()}`")
        }
    }

    private fun formatMessage(msg: String): String {
        val lines = msg.lines().map { it.trim() }.filter { it.isNotBlank() }
        return if (lines.size == 1) "`${lines[0]}`" else "\n${lines.joinToString("\n") { "  `$it`" }}"
    }

    private fun truncate(s: String): String {
        val trimmed = s.trim()
        if (trimmed.isBlank()) return "`(empty)`"
        return if (trimmed.length <= MAX_LEN) "`$trimmed`" else "`${trimmed.take(MAX_LEN)}…` (${trimmed.length} chars)"
    }

    private const val MAX_LEN = 200
}
