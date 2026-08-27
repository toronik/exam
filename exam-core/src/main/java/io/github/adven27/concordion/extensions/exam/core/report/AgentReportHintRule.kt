package io.github.adven27.concordion.extensions.exam.core.report

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A failed example throws `ConcordionAssertionError: Specification has failure(s). See output HTML
 * for details.` under twelve frames of Concordion internals. That message is the whole of what a CI
 * test-report UI shows, and it names neither the example, nor the failed check, nor where the
 * details are - so a reader who starts there learns nothing and has nowhere to go next.
 *
 * This rule rewrites it into [AgentReportHint.forFailure]. It works as a plain JUnit rule because
 * `ConcordionRunner.runChild` delegates to `BlockJUnit4ClassRunner.runChild`, whose `methodBlock`
 * applies rules around the statement that throws.
 */
class AgentReportHintRule(
    private val accumulator: AgentReportAccumulator = AgentReportExtension.ACCUMULATOR,
    private val currentExample: () -> CurrentExample.Snapshot? = AgentReportExtension.CURRENT_EXAMPLE::get
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (expected: Throwable) {
                throw rewrite(expected, description) ?: expected
            }
        }
    }

    private fun rewrite(t: Throwable, description: Description): Throwable? {
        if (!isSpecAssertion(t)) return null
        val report = accumulator.reportFile ?: return null
        val original = t.message ?: t.toString()
        return SpecFailure(AgentReportHint.forFailure(original, snapshotFor(description), report))
    }

    /**
     * Only the example JUnit is running right now. A specification that runs nested specs leaves the
     * last nested example recorded, and describing that one instead would name a failure the reader
     * did not ask about - the mistake this guard exists to prevent. A spec's unnamed outer part has
     * no example of its own, so it gets no digest, only the pointer to the report.
     */
    private fun snapshotFor(description: Description) =
        currentExample()?.takeIf { it.example.name == description.methodName }

    /**
     * Only Concordion's own verdict on the spec is rewritten. An assertion thrown by fixture code
     * is left alone: its message and stack trace are the useful part.
     */
    private fun isSpecAssertion(t: Throwable) =
        t is AssertionError && t.javaClass.name.startsWith("org.concordion.")
}

/**
 * Carries the rewritten message with no stack trace: every frame of the original was Concordion
 * plumbing, and dropping them keeps the instruction visible in report UIs that truncate. The
 * exception's own header line still lands in the JUnit XML body, so nothing renders empty.
 */
class SpecFailure(message: String) : AssertionError(message) {
    init {
        stackTrace = emptyArray()
    }
}
