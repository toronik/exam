package report

import nu.xom.Builder
import nu.xom.Element
import nu.xom.Nodes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

/**
 * Reads what the run of `Specs.adoc` actually published.
 *
 * Every other test of fanout builds its own dom, which is why none of them could see a group being
 * parsed as an example by the agent report: the parser was never in the fixture. This one owns no
 * markup at all - it asserts about the two artifacts a reader is sent to.
 */
class ReportGuardTest {

    private val dir = File("build/reports/specs")
    private val agentReport = File(dir, "agent-report.md")
    private val spec = File(dir, "specs/Specs.html")

    @Test
    fun `the artifacts a reader is sent to exist, or this test guards nothing`() {
        assertThat(agentReport).exists()
        assertThat(spec).exists()
    }

    @Test
    fun `nothing in the report is nameless`() {
        assertThat(agentReport.readText()).doesNotContain("unnamed")
    }

    @Test
    fun `every failure is attributed to one example, once`() {
        val named = Regex("""^### Example: "(.+)"$""", RegexOption.MULTILINE)
            .findAll(agentReport.readText())
            .map { it.groupValues[1] }
            .toList()

        assertThat(named).doesNotHaveDuplicates()
    }

    @Test
    fun `only examples wear the attributes the agent report enumerates examples by`() {
        val summarized = html().query("//*[@data-summary-success]").elements()

        assertThat(summarized).isNotEmpty()
        assertThat(summarized.map { it.getAttributeValue("data-type") }).allMatch { it == "example" }
    }

    @Test
    fun `every example is in the published spec once`() {
        val ids = html().query("//*[@data-type='example']").elements().map { it.getAttributeValue("id") }

        assertThat(ids).isNotEmpty().doesNotContainNull().doesNotHaveDuplicates()
    }

    @Test
    fun `no two examples share a log file, which is where a diagnosis starts`() {
        val logs = html().query("//a[contains(@href, 'LogViewer.html')]").elements()
            .map { it.getAttributeValue("href") }

        assertThat(logs).isNotEmpty().doesNotHaveDuplicates()
        logs.forEach { assertThat(File(dir, "specs/$it")).exists() }
    }

    @Test
    fun `a group has as many tabs as it has cases, and says so`() {
        val groups = groups()

        assertThat(groups).isNotEmpty()
        groups.forEach { group ->
            val tabs = group.own("ul", "nav-tabs").flatMap { it.query(".//button").elements() }
            val panes = group.own("div", "tab-content").flatMap { it.own("div", "tab-pane") }

            assertThat(tabs).isNotEmpty()
            assertThat(panes.size).describedAs("panes of ${group.getAttributeValue("id")}").isEqualTo(tabs.size)
            assertThat(group.getAttributeValue("data-fanout-cases")).isEqualTo(tabs.size.toString())
        }
    }

    @Test
    fun `an outline has as many cases as its matrix has data rows`() {
        val outlines = groups().mapNotNull { group ->
            group.own("div", "card-header").flatMap { it.own("div", "exam-fanout-header") }
                .flatMap { it.query(".//table").elements() }
                .singleOrNull()
                ?.let { group to it.query(".//tr").size() - 1 }
        }

        assertThat(outlines).isNotEmpty()
        outlines.forEach { (group, rows) ->
            assertThat(group.getAttributeValue("data-fanout-cases"))
                .describedAs("cases of ${group.getAttributeValue("id")}")
                .isEqualTo(rows.toString())
        }
    }

    private fun html() = Builder().build(spec)

    private fun groups() = html().query("//div[@data-fanout-cases]").elements()

    private fun Element.own(tag: String, cssClass: String) = getChildElements(tag)
        .let { children -> (0 until children.size()).map { children[it] } }
        .filter { cssClass in it.getAttributeValue("class").orEmpty().split(" ") }

    private fun Nodes.elements() = (0 until size()).map { this[it] as Element }
}
