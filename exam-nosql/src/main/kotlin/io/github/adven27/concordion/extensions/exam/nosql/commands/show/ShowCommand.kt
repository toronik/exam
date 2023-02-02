package io.github.adven27.concordion.extensions.exam.nosql.commands.show

import io.github.adven27.concordion.extensions.exam.core.commands.BeforeParseExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.pretty
import io.github.adven27.concordion.extensions.exam.core.toHtml
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder

class ShowCommand(
    override val name: String,
    private val dbTesters: Map<String, NoSqlDBTester>,
    private val commandParser: CommandParser<Attrs> = ShowParser()
) : AbstractCommand(), NamedExamCommand, BeforeParseExamCommand {
    override val tag = "div"

    data class Attrs(
        val dsName: String,
        val collection: String
    )

    override fun setUp(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder?, fixture: Fixture) {
        with(commandParser.parse(cmd, eval)) {
            val el = cmd.html()
            val docs = dbTesters[dsName]?.read(collection)
                ?: throw IllegalArgumentException("NoSqlDBTester with name $dsName is not registered")
            el(
                renderCollection(collection, docs).toHtml()
            )
        }
    }

    private fun renderCollection(collection: String, docs: List<NoSqlDocument>) = //language=html
        """
    <div class="nosql-show">
        <table class="table table-sm caption-top">
            <caption><i class="fa fa-database me-1"> </i><span>$collection</span></caption>
            <tbody> ${renderDocs(docs)} </tbody>
        </table>
    </div>
        """.trimIndent()

    private fun renderDocs(docResults: List<NoSqlDocument>) = //language=html
        docResults.joinToString("\n") { doc ->
            """
                <tr>
                    <td class='exp-body'>${renderBody(doc)}</td>
                </tr>
            """.trimIndent()
        }.ifEmpty {
            """<tr><td class='exp-body'>EMPTY</td></tr>"""
        }

    // language=html
    private fun renderBody(doc: NoSqlDocument) =
        """<div class="json"></div>""".toHtml().text(doc.body.pretty("json")).el.toXML()
}
