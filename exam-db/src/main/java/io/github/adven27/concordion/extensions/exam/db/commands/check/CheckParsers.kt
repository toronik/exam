package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.core.commands.awaitConfig
import io.github.adven27.concordion.extensions.exam.core.html.DbRowParser
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableExpectation
import io.github.adven27.concordion.extensions.exam.db.MarkedHasNoDefaultValue
import io.github.adven27.concordion.extensions.exam.db.TableData
import io.github.adven27.concordion.extensions.exam.db.builder.DataSetBuilder
import io.github.adven27.concordion.extensions.exam.db.builder.ExamTable
import io.github.adven27.concordion.extensions.exam.db.commands.ColParser
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.DS
import org.dbunit.dataset.Column
import org.dbunit.dataset.DefaultTable
import org.dbunit.dataset.ITable
import org.dbunit.dataset.datatype.DataType

abstract class CheckParser : DbCheckCommand.Parser {
    abstract fun table(context: Context): ITable
    abstract fun caption(html: Html): String?

    override fun parse(context: Context): DbCheckCommand.Model = DbCheckCommand.Model(
        caption = caption(context.el),
        expectation = TableExpectation(
            ds = context[DS],
            table = table(context),
            orderBy = context.el.getAttr("orderBy", context.eval)?.split(",")?.map { it.trim() }?.toSet() ?: setOf(),
            where = context.el.getAttr("where", context.eval) ?: "",
            await = context.el.awaitConfig()
        )
    )
}

class MdCheckParser : CheckParser() {
    override fun caption(html: Html) = html.text().ifBlank { null }
    private fun root(html: Html) = html.parent().parent()

    override fun table(context: Context): ITable {
        val builder = DataSetBuilder()
        val tableName = context.expression
        return root(context.el)
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
                        context.eval
                    )
                }
            }
    }

    private fun toColumns(cols: List<String>) = cols.map { Column(it, DataType.UNKNOWN) }.toTypedArray()

    private fun cols(it: Html) =
        it.el.getFirstChildElement("thead").getFirstChildElement("tr").childElements.map { it.text.trim() }

    private fun values(it: Html) = it.el().getFirstChildElement("tbody").childElements.map { tr ->
        tr.childElements.map { it.text.trim() }
    }
}

open class XhtmlCheckParser : CheckParser() {
    private val remarks = HashMap<String, Int>()
    private val colParser = ColParser()

    override fun caption(html: Html) = html.attr("caption")

    override fun table(context: Context): ITable = TableData.filled(
        context[DbCommand.TABLE]!!,
        DbRowParser(context.el, "row").parse(),
        parseCols(context.el),
        context.eval
    )

    protected fun parseCols(el: Html): Map<String, Any?> {
        val attr = el.getAttr("cols")
        return if (attr == null) {
            emptyMap()
        } else {
            val remarkAndVal = colParser.parse(attr)
            remarks += remarkAndVal.map { it.key to it.value.first }.filter { it.second > 0 }
            remarkAndVal.mapValues { if (it.value.second == null) MarkedHasNoDefaultValue() else it.value.second }
        }
    }
}
