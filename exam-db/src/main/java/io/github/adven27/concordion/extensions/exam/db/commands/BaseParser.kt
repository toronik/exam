package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.builder.DataSetBuilder
import io.github.adven27.concordion.extensions.exam.db.builder.ExamDataSet
import io.github.adven27.concordion.extensions.exam.db.builder.ExamTable
import org.concordion.api.Evaluator
import org.dbunit.dataset.Column
import org.dbunit.dataset.CompositeDataSet
import org.dbunit.dataset.DefaultTable
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable
import org.dbunit.dataset.datatype.DataType

open class BaseParser {

    protected fun parseCols(html: Html) =
        html.el.getFirstChildElement("thead")?.getFirstChildElement("tr")?.childElements?.map { it.text.trim() }
            ?: listOf()

    protected fun parseValues(html: Html) =
        html.el.getFirstChildElement("tbody")?.childElements?.map { tr -> tr.childElements.map { it.text.trim() } }
            ?: listOf()

    protected fun parseTableName(el: Html) =
        requireNotNull(el.childOrNull("caption")?.text()) { "Table caption not set" }

    protected fun isTable(context: Context) = context.el.localName() == "table"
    protected fun isSource(c: Context) = c.el.parent().localName() == "pre"
    protected fun isBlock(c: Context) =
        c.el.parent().let { it.hasClass("openblock") || it.hasClass("listingblock") || it.localName() == "details" }

    protected fun table(tableName: String, el: Html, eval: Evaluator): ITable {
        val builder = DataSetBuilder()
        return el
            .let { parseCols(it) to parseValues(it) }
            .let { (cols, rows) ->
                rows.forEach { row -> builder.newRowTo(tableName).withFields(cols.zip(row).toMap()).add() }
                ExamTable(tableFrom(builder.build(), tableName, cols), eval)
            }
    }

    protected fun tableFrom(dataSet: IDataSet, tableName: String, cols: List<String>) =
        if (dataSet.tableNames.isEmpty()) DefaultTable(tableName, toColumns(cols)) else dataSet.getTable(tableName)

    private fun toColumns(cols: List<String>) = cols.map { Column(it, DataType.UNKNOWN) }.toTypedArray()

    protected fun buildDataSetFromBlock(dbTester: DbTester, el: Html, eval: Evaluator) = CompositeDataSet(
        el.childs()
            .mapNotNull { html ->
                when {
                    html.localName() == "table" ->
                        ExamDataSet(table(parseTableName(html), html, eval), eval)

                    html.hasClass("listingblock") -> html.child { it.hasClass("content") }
                        .child("pre")
                        .child("code")
                        .let {
                            dbTester.loadDataSet(
                                eval,
                                listOf(Content(body = it.text(), type = it.attr("data-lang") ?: "xml"))
                            )
                        }

                    else -> null
                }
            }
            .toTypedArray()
    )

    protected fun buildDataSetFromSource(dbTester: DbTester, el: Html, eval: Evaluator) =
        dbTester.loadDataSet(eval, listOf(Content(el.text(), el.attr("data-lang") ?: "xml")))
}
