package io.github.adven27.concordion.extensions.exam.core.fanout

import nu.xom.Builder
import org.assertj.core.api.Assertions.assertThat
import org.concordion.api.Resource
import org.concordion.api.listener.SpecificationProcessingEvent
import org.junit.Test
import org.concordion.api.Element as ConcordionElement

/**
 * The shape here is the shape Exam leaves behind: `ExamExampleListener` has wrapped every clone into
 * a card of its own, with the counts of its checks in `data-summary-*` and its body in a collapse.
 */
class VariantGroupListenerTest {

    @Test
    fun `the case that needs attention is the one opened`() {
        val group = group(case("rub", failures = 0), case("usd", failures = 1))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(active(group, PANE)).containsExactly("usd")
        assertThat(active(group, TAB)).containsExactly("usd")
    }

    @Test
    fun `with nothing to answer for, the first case is opened`() {
        val group = group(case("rub", failures = 0), case("usd", failures = 0))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(active(group, PANE)).containsExactly("rub")
    }

    @Test
    fun `an expected-to-fail case that failed is doing what the spec says, one that passed is not`() {
        val group = group(
            case("rub", failures = 1, status = EXPECTED_TO_FAIL),
            case("usd", failures = 0, status = EXPECTED_TO_FAIL)
        )

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(active(group, PANE)).containsExactly("usd")
    }

    @Test
    fun `every tab carries the verdict of its case`() {
        val group = group(case("rub", failures = 0), case("usd", failures = 1))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(marked(group, TAB).map { it.text.trim() }).containsExactly("rub✓", "usd!")
    }

    @Test
    fun `the group states the outcome of the scenario as a whole, which no single clone can`() {
        val group = group(case("rub", failures = 0, successes = 2), case("usd", failures = 1, successes = 1))

        VariantGroupListener().afterProcessingSpecification(event(group))

        val card = marked(group, GROUP).single()
        assertThat(card.getAttributeValue("data-fanout-cases")).isEqualTo("2")
        assertThat(card.getAttributeValue("data-fanout-success")).isEqualTo("3")
        assertThat(card.getAttributeValue("data-fanout-failure")).isEqualTo("1")
        assertThat(marked(group, "card-header").single().text).contains("1 of 2 cases failed")
    }

    @Test
    fun `a group wears no attribute the agent report enumerates examples by`() {
        val group = group(case("rub", failures = 0), case("usd", failures = 1))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(marked(group, GROUP).single().getAttributeValue("data-summary-success")).isNull()
    }

    @Test
    fun `a group counts its own cases, not the cases of a group nested in one of them`() {
        val group = group(case("rub", failures = 0, nested = group(case("inner", failures = 1)).toXML()))

        VariantGroupListener().afterProcessingSpecification(event(group))

        val outer = marked(group, GROUP).first()
        assertThat(outer.getAttributeValue("data-fanout-cases")).isEqualTo("1")
        assertThat(outer.getAttributeValue("data-fanout-failure")).isEqualTo("0")
    }

    @Test
    fun `a case that failed as it was expected to is not called a pass`() {
        val group = group(case("rub", failures = 1, status = EXPECTED_TO_FAIL))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(marked(group, "card-header").single().text).contains("1 case failed as expected")
    }

    @Test
    fun `a status that is not the plain one is said out loud on the tab, as on an example card`() {
        val group = group(case("rub", failures = 0, status = "Ignored"))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(marked(group, TAB).single().text.trim()).isEqualTo("rubIgnored")
    }

    @Test
    fun `the collapse of a case is left without an affordance, since the tab is the one that shows it`() {
        val group = group(case("rub", failures = 0))

        VariantGroupListener().afterProcessingSpecification(event(group))

        assertThat(marked(group, "title").map { it.getAttributeValue("data-bs-toggle") }).containsOnlyNulls()
    }

    private fun event(root: ConcordionElement) = SpecificationProcessingEvent(Resource("/spec.html"), root)

    private fun marked(group: ConcordionElement, cls: String) =
        group.getDescendantElements("*").filter { cls in it.getAttributeValue("class").orEmpty().split(" ") }

    private fun active(group: ConcordionElement, cls: String) = marked(group, cls)
        .filter { ACTIVE in it.getAttributeValue("class").orEmpty().split(" ") }
        .map { it.getAttributeValue("id")?.removePrefix("pane-") ?: it.text.trim().take(3) }

    private fun case(
        name: String,
        failures: Int,
        successes: Int = 1,
        status: String = "ExpectedToPass",
        nested: String = ""
    ) = """
        <div class="$PANE" id="pane-$name">
          <div class="$EXAMPLE_BLOCK exam-example mb-3" data-type="example" data-summary-success="$successes"
               data-summary-ignore="0" data-summary-failure="$failures" data-summary-exception="0"
               data-summary-status="$status">
            <div class="title" data-bs-toggle="collapse"><a class="bd-example-title">$name</a></div>
            <div class="$EXAMPLE_BLOCK collapse"><p>body of $name</p>$nested</div>
          </div>
        </div>
    """.trimIndent() to """
        <li class="nav-item"><button class="$TAB" data-bs-toggle="tab" data-bs-target="#pane-$name">$name</button></li>
    """.trimIndent()

    private fun group(vararg cases: Pair<String, String>) = ConcordionElement(
        Builder().build(
            """
            <body>
            <div class="$GROUP card mb-3">
              <div class="card-header"><span class="$GROUP_NAME">Valuation</span></div>
              <ul class="nav nav-tabs">${cases.joinToString("") { it.second }}</ul>
              <div class="tab-content">${cases.joinToString("") { it.first }}</div>
            </div>
            </body>
            """.trimIndent(),
            null
        ).rootElement
    )
}
