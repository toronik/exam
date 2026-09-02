package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import nu.xom.Builder
import nu.xom.Document
import nu.xom.Element
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.concordion.internal.ConcordionBuilder.NAMESPACE_CONCORDION_2007 as CONCORDION_NS

class InvarianceTest {

    @Test
    fun `one case per perturbation named in the marker, in the order they are named`() {
        val doc = doc(example("duplicate reorder", "${send("first")}${send("second")}"))

        fanout(doc)

        assertThat(doc.cases().map { it.exampleName() })
            .containsExactly("Ingest — duplicate", "Ingest — reorder")
        assertThat(doc.header()).contains("stable under: duplicate, reorder")
    }

    @Test
    fun `duplication performs every delivery twice`() {
        val doc = doc(example("duplicate", "${send("first")}${send("second")}"))

        fanout(doc)

        assertThat(doc.cases().single().sends()).containsExactly("first", "first", "second", "second")
    }

    @Test
    fun `what is set up and what is checked is left alone - the delivery is what is in question`() {
        val doc = doc(example("duplicate", """<table e:db-set="person"><tr><td>a</td></tr></table>${send("m")}<em e:eq="#x">1</em>"""))

        fanout(doc)

        val case = doc.cases().single()
        assertThat(case.query(".//table[@e:db-set]", context()).size()).isEqualTo(1)
        assertThat(case.query(".//em[@e:eq]", context()).size()).isEqualTo(1)
        assertThat(case.sends()).containsExactly("m", "m")
    }

    @Test
    fun `reordering performs the deliveries in the opposite order`() {
        val doc = doc(example("reorder", "${send("first")}${send("second")}${send("third")}"))

        fanout(doc)

        assertThat(doc.cases().single().sends()).containsExactly("third", "second", "first")
    }

    @Test
    fun `a delivery marked stable-except is left as it is, since at-least-once is sometimes honest`() {
        val doc = doc(example("duplicate", send("counted") + send("metric", exempt = true)))

        fanout(doc)

        assertThat(doc.cases().single().sends()).containsExactly("counted", "counted", "metric")
    }

    @Test
    fun `a perturbation nobody implements fails at parsing, with the known ones listed`() {
        val doc = doc(example("outage", send("m")))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.UnknownPerturbation::class.java)
            .hasMessageContaining("outage")
            .hasMessageContaining("duplicate")
    }

    @Test
    fun `a status in the marker is a status, not a perturbation`() {
        val doc = doc(example("duplicate ExpectedToFail", send("m"), status = "ExpectedToFail"))

        fanout(doc)

        assertThat(doc.cases().map { it.exampleName() }).containsExactly("Ingest — duplicate")
    }

    @Test
    fun `a case that could not be perturbed is refused, instead of passing for a verified property`() {
        val doc = doc(example("reorder", send("only one")))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.NothingToPerturb::class.java)
            .hasMessageContaining("1 delivery")
    }

    @Test
    fun `a styling role is not mistaken for a perturbation`() {
        val doc = doc(example("duplicate mh-100", send("m")))

        fanout(doc)

        assertThat(doc.cases()).hasSize(1)
        assertThat(doc.cases().single().sends()).containsExactly("m", "m")
    }

    @Test
    fun `perturbation names do not leak into the classes of the case`() {
        val doc = doc(example("duplicate reorder", "${send("first")}${send("second")}"))

        fanout(doc)

        assertThat(doc.cases().map { it.classes() }).allMatch { it == setOf(EXAMPLE_BLOCK) }
    }

    @Test
    fun `what a system has to be indifferent to is a seam`() {
        val doc = doc(example("outage", send("m")))

        ExampleFanout(Invariance(listOf(Outage))).beforeParsing(doc)

        assertThat(doc.cases().single().sends()).containsExactly("m", "m", "m")
    }

    @Test
    fun `a case says what was done to it, since that is what a failure is blamed on`() {
        val doc = doc(example("duplicate", send("m")))

        fanout(doc)

        assertThat(doc.cases().single().getChildElements("div")[0].value).isEqualTo(Duplicate.effect)
    }

    private object Outage : Perturbation {
        override val name = "outage"
        override val effect = "thrice, why not"
        override fun perturb(deliveries: List<Element>) = deliveries.forEach {
            val parent = it.parent as Element
            parent.insertChild(it.deepCopy(), parent.indexOf(it) + 1)
            parent.insertChild(it.deepCopy(), parent.indexOf(it) + 1)
        }
    }

    private fun fanout(doc: Document) = ExampleFanout(Invariance()).beforeParsing(doc)

    private fun Document.cases() = rootElement.descendantsMarked(EXAMPLE_BLOCK)

    private fun Document.header() = rootElement.descendantsMarked(GROUP_HEADER).single().value

    private fun Document.notices() =
        query("//div[@class='alert alert-warning']").elements().map { it.value }

    private fun Element.sends() = query(".//table[@e:mq-set]", context()).elements().map { it.value.trim() }

    private fun context() = nu.xom.XPathContext("e", ExamExtension.NS)

    private fun send(message: String, exempt: Boolean = false) =
        """<table e:mq-set="myQueue"${if (exempt) """ class="$STABLE_EXCEPT"""" else ""}><tr><td>$message</td></tr></table>"""

    private fun doc(vararg blocks: String): Document = Builder().build(
        """<html xmlns:c="$CONCORDION_NS" xmlns:e="${ExamExtension.NS}"><body>${blocks.joinToString("")}</body></html>""",
        null
    )

    private fun example(marker: String, body: String, status: String = "ExpectedToPass") = """
        <div class="$EXAMPLE_BLOCK $STABLE_UNDER $marker" c:example="Ingest" c:status="$status">
          <div class="content">$body</div>
        </div>
    """.trimIndent()
}
