package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import nu.xom.Builder
import nu.xom.Document
import nu.xom.Element
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.concordion.internal.ConcordionBuilder.NAMESPACE_CONCORDION_2007 as CONCORDION_NS

/**
 * The dom here is the dom of a real run: `ConcordionPostprocessor` has already moved the block title
 * into `c:example` and the `[{rows}]` marker arrives as the `e:outline-rows` attribute of the table
 * (both verified by dumping the converted html of a spec). A hand-built fixture only ever proves the
 * author's idea of the markup, which is why the selectors are also covered by a live spec -
 * `core-outline.adoc` in the example module.
 */
class ExampleFanoutTest {

    @Test
    fun `an outline block becomes one example per data row`() {
        val doc = doc(outline(body = "<p>qty={{qty}}</p>", rows = rows(listOf("case", "qty"), listOf("rub", "100"), listOf("usd", "10"))))

        fanout(doc)

        val clones = doc.exampleBlocksMarked(EXAMPLE_BLOCK)
        assertThat(clones).hasSize(2)
        assertThat(clones.map { it.exampleName() }).containsExactly("Valuation — rub", "Valuation — usd")
        assertThat(clones.map { it.classes() }).allMatch { it == setOf(EXAMPLE_BLOCK) }
        assertThat(clones.map { it.getAttributeValue("status", CONCORDION_NS) }).allMatch { it == "ExpectedToPass" }
    }

    @Test
    fun `clones keep the order of the data rows`() {
        val doc = doc(outline("<p>{{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"), listOf("usd", "10"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).first().exampleName()).isEqualTo("Valuation — rub")
    }

    @Test
    fun `names that coincide are disambiguated, since concordion addresses examples by name`() {
        val doc = doc(outline("<p>{{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"), listOf("rub", "200"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).map { it.exampleName() })
            .containsExactly("Valuation — rub", "Valuation — rub #2")
    }

    @Test
    fun `naming is a seam`() {
        val doc = doc(outline("<p>{{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        ExampleFanout(Outline(nameBy = { "qty of ${it["qty"]}" })).beforeParsing(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).single().exampleName()).isEqualTo("Valuation — qty of 100")
    }

    @Test
    fun `row values are put into the body of the clone, and the matrix is cut out of it`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"), listOf("usd", "10"))))

        fanout(doc)

        val clones = doc.exampleBlocksMarked(EXAMPLE_BLOCK)
        assertThat(clones[0].value).contains("qty=100").doesNotContain("{{qty}}")
        assertThat(clones[1].value).contains("qty=10").doesNotContain("{{qty}}")
        assertThat(clones.map { it.tableMarked(OUTLINE_ROWS) }).containsOnlyNulls()
    }

    @Test
    fun `substitution is a single pass, so a value that looks like a placeholder is not substituted again`() {
        val body = Element("p").apply { appendChild("{{a}} and {{b}}") }

        body.substitute(Row(mapOf("a" to "{{b}}", "b" to "2")))

        assertThat(body.value).isEqualTo("{{b}} and 2")
    }

    @Test
    fun `row values are also set as spec variables, hidden so they do not show up as stray text`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        fanout(doc)

        val holder = doc.exampleBlocksMarked(EXAMPLE_BLOCK).single().getChildElements("div")[0]
        assertThat(holder.getAttributeValue(CLASS)).isEqualTo("hide")
        assertThat(holder.getChildElements("span").size()).isEqualTo(2)
        assertThat(holder.childElements.let { (0 until it.size()).map { i -> it[i].getAttributeValue("set", ExamExtension.NS) } })
            .containsExactly("case", "qty")
        assertThat(holder.value).isEqualTo("rub100")
    }

    @Test
    fun `a nil cell arrives as the NULL sentinel and is substituted as such`() {
        val doc = doc(outline("<p>rate={{rate}}</p>", rows(listOf("case", "rate"), listOf("rub", "{{NULL}}"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).single().value).contains("rate={{NULL}}")
    }

    @Test
    fun `a NULL sentinel in a cell is not taken for a column reference`() {
        val doc = doc(outline("<p>rate={{rate}}</p>", rows(listOf("case", "rate"), listOf("rub", "{{NULL}}"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK)).hasSize(1)
    }

    @Test
    fun `an exam helper in the body is not taken for a missing column`() {
        val doc = doc(outline("<p>id={{uuid}} at={{iso}} qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK)).hasSize(1)
    }

    @Test
    fun `a column named like an exam helper is used by the body, not warned about`() {
        val doc = doc(outline("<p>s={{string}}</p>", rows(listOf("case", "string"), listOf("rub", "abc"))))

        fanout(doc)

        assertThat(doc.notices()).isEmpty()
        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).single().value).contains("s=abc")
    }

    @Test
    fun `a cyrillic reference is seen, so a missing column is said out loud`() {
        val doc = doc(outline("<p>{{количество}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        fanout(doc)

        assertThat(doc.notices()).anySatisfy({ assertThat(it).contains("количество") })
    }

    @Test
    fun `a used cyrillic column is not warned as unused`() {
        val doc = doc(outline("<p>{{количество}}</p>", rows(listOf("case", "количество"), listOf("rub", "100"))))

        fanout(doc)

        assertThat(doc.notices()).isEmpty()
    }

    @Test
    fun `a column the body never reads is a warning in the report, not a failure`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty", "rate"), listOf("rub", "100", "90"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK)).hasSize(1)
        assertThat(doc.notices()).singleElement()
            .satisfies({ assertThat(it).contains("rate").contains("Valuation").contains("[{rows}]") })
    }

    @Test
    fun `columns that only name the case are not warned as unused`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        fanout(doc)

        assertThat(doc.notices()).isEmpty()
    }

    @Test
    fun `an outline without a rows table fails at parsing`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows = ""))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.NoRowsTable::class.java)
            .hasMessageContaining("[{outline}]")
            .hasMessageContaining("[{rows}]")
    }

    @Test
    fun `an outline whose rows table has no data fails at parsing`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"))))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.EmptyRows::class.java)
            .hasMessageContaining("[{rows}]")
    }

    @Test
    fun `an untitled outline block fails at parsing instead of quietly producing non-examples`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100")), name = null))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.MissingTitle::class.java)
            .hasMessageContaining("[{outline}]")
            .hasMessageContaining("qty=")
    }

    @Test
    fun `a placeholder with no column is left to the evaluator, and said out loud`() {
        val doc = doc(outline("<p>{{qty}} {{someVar}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).single().value).contains("{{someVar}}")
        assertThat(doc.notices()).singleElement()
            .satisfies({ assertThat(it).contains("someVar").contains("[{rows}]") })
    }

    @Test
    fun `two fanout markers on one block are refused, since each would fan out inside the other`() {
        val doc = doc(
            outline(
                "<p>qty={{qty}}</p>",
                rows(listOf("case", "qty"), listOf("rub", "100")),
                marker = "$OUTLINE $STABLE_UNDER duplicate"
            )
        )

        assertThatThrownBy { ExampleFanout(Outline(), Invariance()).beforeParsing(doc) }
            .isInstanceOf(FanoutError.ManyMarkers::class.java)
            .hasMessageContaining("[{outline}]")
            .hasMessageContaining("[{stable-under}]")
    }

    @Test
    fun `a nested outline is rejected`() {
        val inner = outline("<p>{{qty}}</p>", rows(listOf("case", "qty"), listOf("usd", "10")))
        val doc = doc(outline("<p>{{qty}}</p>$inner", rows(listOf("case", "qty"), listOf("rub", "100"))))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.Nested::class.java)
            .hasMessageContaining("[{outline}]")
    }

    @Test
    fun `a data row of the wrong width fails at parsing instead of silently losing data`() {
        val doc = doc(outline("<p>{{qty}}</p>", rows(listOf("case", "qty"), listOf("rub"))))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.RowSizeMismatch::class.java)
            .hasMessageContaining("[{rows}]")
            .hasMessageContaining("1 cell")
    }

    @Test
    fun `a blank column name fails at parsing`() {
        val doc = doc(outline("<p>{{qty}}</p>", rows(listOf("case", " "), listOf("rub", "100"))))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.BlankColumnName::class.java)
            .hasMessageContaining("[{rows}]")
    }

    @Test
    fun `duplicate column names fail at parsing instead of one silently winning`() {
        val doc = doc(outline("<p>{{qty}}</p>", rows(listOf("qty", "qty"), listOf("100", "200"))))

        assertThatThrownBy { fanout(doc) }
            .isInstanceOf(FanoutError.DuplicateColumnNames::class.java)
            .hasMessageContaining("[{rows}]")
            .hasMessageContaining("qty")
    }

    @Test
    fun `a table with a thead is read the same as a csv one, which has none`() {
        val doc = doc(
            outline(
                "<p>qty={{qty}}</p>",
                """<table e:outline-rows="" class="tableblock">
                     <thead><tr><th>case</th><th>qty</th></tr></thead>
                     <tbody><tr><td>rub</td><td>100</td></tr></tbody>
                   </table>"""
            )
        )

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK).single().exampleName()).isEqualTo("Valuation — rub")
    }

    @Test
    fun `the clones become the cases of one group, with the matrix in its header`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"), listOf("usd", "10"))))

        fanout(doc)

        val group = doc.marked(GROUP).single()
        assertThat(group.descendantsMarked(TAB).map { it.value }).containsExactly("rub", "usd")
        assertThat(group.descendantsMarked(PANE))
            .allMatch { pane -> EXAMPLE_BLOCK in pane.getChildElements("div")[0].classes() }
        assertThat(group.descendantsMarked(GROUP_HEADER).single().query(".//table").elements()).hasSize(1)
    }

    @Test
    fun `the matrix in the header is data, not another outline`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"))))

        fanout(doc)

        assertThat(doc.marked(GROUP_HEADER).single().tableMarked(OUTLINE_ROWS)).isNull()
    }

    @Test
    fun `the first case is active before anything has run, so a report without javascript still shows a body`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty"), listOf("rub", "100"), listOf("usd", "10"))))

        fanout(doc)

        assertThat(doc.marked(PANE).map { it.classes() }).containsExactly(setOf(PANE, ACTIVE), setOf(PANE))
        assertThat(doc.marked(TAB).map { it.classes() }).containsExactly(setOf(TAB, ACTIVE), setOf(TAB))
    }

    @Test
    fun `a warning about the markup is shown in the header of the group it is about`() {
        val doc = doc(outline("<p>qty={{qty}}</p>", rows(listOf("case", "qty", "rate"), listOf("rub", "100", "90"))))

        fanout(doc)

        val notice = doc.query("//div[contains(@class, 'card-header')]/div[@class='alert alert-warning']").elements()
        assertThat(notice).singleElement().satisfies({ assertThat(it.value).contains("rate") })
    }

    @Test
    fun `an unmarked example block is left alone`() {
        val doc = doc(outline("<p>plain</p>", rows = "", marker = ""))

        fanout(doc)

        assertThat(doc.exampleBlocksMarked(EXAMPLE_BLOCK)).hasSize(1)
    }

    private fun fanout(doc: Document) = ExampleFanout(Outline()).beforeParsing(doc)

    private fun Document.marked(cls: String) = rootElement.descendantsMarked(cls)

    private fun Document.notices() = query("//div[@class='alert alert-warning']").elements().map { it.value }

    private fun doc(vararg blocks: String): Document = Builder().build(
        """
        <html xmlns:c="$CONCORDION_NS" xmlns:e="${ExamExtension.NS}"><body>${blocks.joinToString("")}</body></html>
        """.trimIndent(),
        null
    )

    private fun outline(
        body: String,
        rows: String,
        name: String? = "Valuation",
        marker: String = OUTLINE,
        status: String = "ExpectedToPass"
    ) = """
        <div class="$EXAMPLE_BLOCK $marker"${name?.let { """ c:example="$it"""" } ?: ""} c:status="$status">
          <div class="content">$body</div>
          $rows
        </div>
    """.trimIndent()

    private fun rows(vararg rows: List<String>) = """
        <table e:outline-rows="" class="tableblock"><tbody>
        ${rows.joinToString("\n") { row -> "<tr>${row.joinToString("") { "<td>$it</td>" }}</tr>" }}
        </tbody></table>
    """.trimIndent()
}
