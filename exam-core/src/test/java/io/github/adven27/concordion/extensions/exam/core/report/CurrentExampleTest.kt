package io.github.adven27.concordion.extensions.exam.core.report

import org.assertj.core.api.Assertions.assertThat
import org.concordion.api.Element
import org.concordion.api.FixtureDeclarations
import org.concordion.api.ImplementationStatus
import org.concordion.api.ResultSummary
import org.concordion.api.listener.ExampleEvent
import org.junit.Test

class CurrentExampleTest {

    private val listener = CurrentExample()

    @Test
    fun `nothing is recorded before an example runs`() {
        assertThat(listener.get()).isNull()
    }

    @Test
    fun `a finished example is recorded with its failed check`() {
        listener.afterExample(event("Book max", failures = 1))

        val snapshot = listener.get()!!
        assertThat(snapshot.spec).isEqualTo("DbCheck.adoc")
        assertThat(snapshot.example.name).isEqualTo("Book max")
        assertThat(snapshot.example.status).isEqualTo(Status.FAIL)
        assertThat(snapshot.example.failures).singleElement()
            .satisfies({ assertThat(it.message).contains("Expected 3 rows, but was 2") })
        assertThat(snapshot.expectedToFail).isFalse()
    }

    @Test
    fun `an expected-to-fail example is recorded as such`() {
        listener.afterExample(event("Surplus rows", failures = 1, status = ImplementationStatus.EXPECTED_TO_FAIL))

        assertThat(listener.get()!!.expectedToFail).isTrue()
    }

    @Test
    fun `the next example starts from nothing, so no result is ever attributed to the wrong one`() {
        listener.afterExample(event("Book max", failures = 1))

        listener.beforeExample(event("Book min", failures = 0))

        assertThat(listener.get()).isNull()
    }

    private fun event(
        name: String,
        failures: Int,
        status: ImplementationStatus = ImplementationStatus.EXPECTED_TO_PASS
    ) = ExampleEvent(name, exampleElement(), summary(failures, status), null)

    private fun exampleElement() = Element("div").apply {
        val wrapper = Element("div").apply { addStyleClass("alert-warning") }
        val container = Element("div").apply {
            addAttribute("id", "error-7")
            val pre = Element("pre").apply { addStyleClass("failure") }
            pre.appendChild(Element("code").apply { appendText("Expected 3 rows, but was 2") })
            appendChild(pre)
        }
        wrapper.appendChild(container)
        appendChild(wrapper)
    }

    private fun summary(failures: Int, status: ImplementationStatus) = object : ResultSummary {
        override fun assertIsSatisfied(fixture: FixtureDeclarations?) = Unit
        override fun hasExceptions() = false
        override fun getSuccessCount() = 1L
        override fun getFailureCount() = failures.toLong()
        override fun getExceptionCount() = 0L
        override fun getIgnoredCount() = 0L
        override fun print(out: java.io.PrintStream?, fixture: FixtureDeclarations?) = Unit
        override fun printCountsToString(fixture: FixtureDeclarations?) = ""
        override fun getSpecificationDescription() = "/w/specs/DbCheck.html#Book max"
        override fun isForExample() = true
        override fun getImplementationStatus() = status
    }
}
