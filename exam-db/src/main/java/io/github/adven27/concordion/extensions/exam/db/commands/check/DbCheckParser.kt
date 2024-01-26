package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableExpectation
import io.github.adven27.concordion.extensions.exam.db.builder.DataSetBuilder
import io.github.adven27.concordion.extensions.exam.db.builder.ExamTable
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.DS
import org.concordion.api.Evaluator
import org.dbunit.dataset.Column
import org.dbunit.dataset.DefaultTable
import org.dbunit.dataset.ITable
import org.dbunit.dataset.datatype.DataType

class DbCheckParser : DbCheckCommand.Parser {
    private fun table(tableName: String, el: Html, evaluator: Evaluator): ITable {
        val builder = DataSetBuilder()
        return el
            .let { cols(it) to values(it) }
            .let { (cols, rows) ->
                rows.forEach { row -> builder.newRowTo(tableName).withFields(cols.zip(row).toMap()).add() }
                builder.build().let {
                    ExamTable(
                        if (it.tableNames.isEmpty()) {
                            DefaultTable(tableName, toColumns(cols))
                        } else {
                            it.getTable(tableName)
                        },
                        evaluator
                    )
                }
            }
    }

    override fun parse(context: Context): DbCheckCommand.Model = DbCheckCommand.Model(
        caption = context.el.firstOrNull("caption")?.text(),
        expectation = TableExpectation(
            ds = context[DS],
            table = table(context.expression, context.el, context.eval),
            orderBy = context.el.getAttr("orderBy", context.eval)?.split(",")?.map { it.trim() }?.toSet() ?: setOf(),
            await = context.awaitConfig
        )
    )

    private fun toColumns(cols: List<String>) = cols.map { Column(it, DataType.UNKNOWN) }.toTypedArray()

    private fun cols(html: Html) =
        html.el.getFirstChildElement("thead")?.getFirstChildElement("tr")?.childElements?.map { it.text.trim() }
            ?: listOf()

    private fun values(html: Html) =
        html.el.getFirstChildElement("tbody")?.childElements?.map { tr -> tr.childElements.map { it.text.trim() } }
            ?: listOf()
}
