package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.core.html.DbRowParser
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.db.MarkedHasNoDefaultValue
import io.github.adven27.concordion.extensions.exam.db.TableData
import io.github.adven27.concordion.extensions.exam.db.builder.DataSetBuilder
import io.github.adven27.concordion.extensions.exam.db.builder.ExamTable
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.TABLE
import org.dbunit.dataset.DefaultTable
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable

class MdTableParser : DbSetCommand.Parser.Base() {
    override fun table(context: Context): ITable {
        val builder = DataSetBuilder()
        val tableName = context.expression
        root(context.el).let { parseCols(it) to parseValues(it) }.let { (cols, rows) ->
            rows.forEach { row -> builder.newRowTo(tableName).withFields(cols.zip(row).toMap()).add() }
            return ExamTable(tableFrom(builder.build(), tableName), context.eval)
        }
    }

    private fun root(html: Html) = html.parent().parent()

    private fun tableFrom(dataSet: IDataSet, tableName: String) =
        if (dataSet.tableNames.isEmpty()) DefaultTable(tableName) else dataSet.getTable(tableName)

    private fun parseCols(html: Html) =
        html.el.getFirstChildElement("thead").getFirstChildElement("tr").childElements.map { it.text.trim() }

    private fun parseValues(html: Html) =
        html.el.getFirstChildElement("tbody").childElements.map { tr -> tr.childElements.map { it.text.trim() } }
}

class HtmlTableParser(
    private val remarks: MutableMap<String, Int> = mutableMapOf(),
    private val colParser: ColParser = ColParser()
) : DbSetCommand.Parser.Base() {

    override fun table(context: Context) = TableData.filled(
        context[TABLE]!!,
        DbRowParser(context.el, "row", null, null).parse(),
        parseCols(context.el),
        context.eval
    )

    private fun parseCols(el: Html): Map<String, Any?> {
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

class ColParser {
    fun parse(attr: String): Map<String, Pair<Int, String?>> = attr.split(",")
        .map {
            val (r, n, v) = ("""(\**)([^=]+)=?(.*)""".toRegex()).matchEntire(it.trim())!!.destructured
            mapOf(n to (r.length to (if (v.isBlank()) null else v)))
        }
        .reduce { acc, next -> acc + next }
}
