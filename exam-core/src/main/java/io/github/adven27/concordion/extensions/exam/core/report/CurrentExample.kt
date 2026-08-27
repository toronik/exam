package io.github.adven27.concordion.extensions.exam.core.report

import org.concordion.api.ImplementationStatus.EXPECTED_TO_FAIL
import org.concordion.api.listener.ExampleEvent
import org.concordion.api.listener.ExampleListener
import java.io.File

/**
 * What the example that is about to throw got wrong.
 *
 * The spec-level listener cannot answer this: a specification that runs nested specs records theirs
 * before the verdict is thrown and its own only after, so "the last thing recorded" is some other
 * spec - in the framework's own example suite, an expected-to-fail one. This listener is fired at
 * the end of each example, on the thread running it, which makes the answer unambiguous.
 */
class CurrentExample : ExampleListener {

    private val parser = ExampleParser()
    private val current = ThreadLocal<Snapshot?>()

    /** Null outside an example, and after one whose result nothing asked about. */
    fun get(): Snapshot? = current.get()

    override fun beforeExample(event: ExampleEvent) = current.remove()

    override fun afterExample(event: ExampleEvent) {
        val summary = event.resultSummary
        current.set(
            Snapshot(
                spec = specOf(summary.specificationDescription),
                example = parser.exampleInProgress(
                    el = event.element,
                    name = event.exampleName ?: summary.specificationDescription,
                    failures = summary.failureCount.toInt(),
                    exceptions = summary.exceptionCount.toInt()
                ),
                // An expected-to-fail example that throws does so for the opposite reason - it
                // stopped failing - and its recorded failures would explain nothing.
                expectedToFail = summary.implementationStatus == EXPECTED_TO_FAIL
            )
        )
    }

    /** `/w/specs/Specs.html#Example 1. Dummy` -> `Specs.adoc`, the name the report files it under. */
    private fun specOf(description: String) = description
        .substringBefore('#')
        .substringAfterLast(File.separator)
        .replace(".html", ".adoc")
        .ifBlank { description }

    data class Snapshot(val spec: String, val example: ExampleResult, val expectedToFail: Boolean)
}
