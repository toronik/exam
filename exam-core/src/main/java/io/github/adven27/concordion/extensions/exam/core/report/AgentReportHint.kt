package io.github.adven27.concordion.extensions.exam.core.report

import java.nio.file.Path

/**
 * Every wording that tells a reader that [AgentReportRenderer]'s file exists and how to read it.
 *
 * The same instruction has to show up on two surfaces, because a reader lands on one or the other
 * and never both: the console (a build log, greppable) and the failure message of a broken example
 * (all a CI test-report UI shows behind its "details" button). Keeping the text in one object is
 * the point - two copies drift, and a stale instruction is worse than none.
 */
object AgentReportHint {

    /**
     * Follows the machine-readable `[exam] Agent report: ...` line, which stays byte-identical
     * because tooling greps for it.
     */
    fun forConsole(report: Path, failed: Int, total: Int): String = buildString {
        appendLine("[exam] Agent report: $report ($failed failed / $total total)")
        appendLine("[exam] $RULE")
        howToRead(report).forEach { appendLine("[exam] $it") }
        appendLine("[exam] $RULE")
    }

    /**
     * Replaces Concordion's "See output HTML for details", which names neither what failed nor
     * where the details are. What this example got wrong is repeated here rather than left only in
     * the file, so that a reader with nothing but a test-report UI is not blocked on downloading an
     * artifact. An expected-to-fail example is the exception: it throws because it stopped failing,
     * so its recorded failures would explain the opposite of what happened.
     */
    fun forFailure(original: String, current: CurrentExample.Snapshot?, report: Path?): String {
        val useful = current?.takeUnless { it.expectedToFail }
        return buildString {
            appendLine(headline(useful) ?: original.trim())
            digestOf(useful).takeIf { it.isNotEmpty() }?.let { digest ->
                appendLine()
                digest.forEach { appendLine("    $it") }
            }
            if (report == null) return@buildString
            appendLine()
            howToRead(report).forEach { appendLine(it) }
        }.trimEnd()
    }

    private fun headline(current: CurrentExample.Snapshot?): String? {
        val example = current?.example?.takeIf { it.status != Status.PASS } ?: return null
        val what = if (example.status == Status.ERROR) "threw" else "failed ${example.failures.size} of its checks"
        return "${current.spec} | Example \"${example.name}\" | $what"
    }

    private fun digestOf(current: CurrentExample.Snapshot?): List<String> {
        val example = current?.example?.takeIf { it.status != Status.PASS } ?: return emptyList()
        val shown = example.failures.take(MAX_FAILURES).flatMap { lines(it) }
        val hidden = example.failures.size - MAX_FAILURES
        val more = if (hidden > 0) listOf("(and $hidden more check(s), all of them in the report)") else emptyList()
        val thrown = example.error?.lines()?.firstOrNull()?.let { listOf(it.trim()) } ?: emptyList()
        return shown + more + thrown
    }

    private fun lines(f: Failure): List<String> = buildList {
        add(listOf(f.command, f.context).filter { it.isNotBlank() }.joinToString(" "))
        f.message.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { add("  $it") }
        if (f.expected.isNotBlank() || f.actual.isNotBlank()) {
            add("  expected: ${cut(f.expected)}")
            add("  actual:   ${cut(f.actual)}")
        }
    }

    private fun cut(s: String): String {
        val one = s.trim().lines().joinToString(" ").trim()
        return when {
            one.isEmpty() -> "(empty)"
            one.length <= MAX_LEN -> one
            else -> "${one.take(MAX_LEN)}... (${one.length} chars, full value in the report)"
        }
    }

    private fun howToRead(report: Path): List<String> = buildList {
        add("Every failure of this run is in ONE markdown file. Read it instead of the HTML:")
        add("    $report")
        addAll(LAYOUT)
        ciArtifactPath(report)?.let {
            add("In CI this file is in the job's artifacts at:")
            add("    $it")
        }
    }

    private val LAYOUT = listOf(
        "Frontmatter (total/passed/failed/errors) tells you whether anything broke - if",
        "failed: 0 you are done. Then one \"# Failed\" section per example, each with",
        "Command, Error (the verdict from the checking library), Expected, Actual and",
        "Details. Details is an anchor into the HTML one directory deeper, e.g.",
        "specs/Foo.html#error-42 - open that only when the truncated values are not enough."
    )

    /**
     * The path a CI artifact browser or API expects: relative to the checkout, not to the module
     * the test JVM happened to run in. Absent outside CI, where the absolute path above is enough.
     */
    private fun ciArtifactPath(report: Path): String? =
        System.getenv("CI_PROJECT_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Path.of(it).toAbsolutePath().relativize(report.toAbsolutePath()) }.getOrNull() }
            ?.takeIf { !it.startsWith("..") }
            ?.toString()

    private const val MAX_LEN = 200
    private const val MAX_FAILURES = 3
    private const val RULE = "----------------------------------------------------------------------"
}
