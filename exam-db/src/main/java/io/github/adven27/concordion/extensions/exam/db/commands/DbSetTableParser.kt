package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.builder.DataSetBuilder
import io.github.adven27.concordion.extensions.exam.db.builder.ExamTable
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy
import org.dbunit.dataset.DefaultTable
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable

open class DbSetTableParser : DbSetCommand.Parser {
    override fun parse(context: Context) = DbSetCommand.Model(
        seed = DbTester.TableSeed(
            ds = context[DbCommand.DS],
            table = table(context),
            strategy = context[DbCommand.OPERATION]?.let { SeedStrategy.valueOf(it.uppercase()) }
                ?: SeedStrategy.CLEAN_INSERT
        ),
        caption = context.el.firstOrNull("caption")?.text()
    )

    private fun table(context: Context): ITable {
        val builder = DataSetBuilder()
        val tableName = context.expression
        context.el.let { parseCols(it) to parseValues(it) }.let { (cols, rows) ->
            rows.forEach { row -> builder.newRowTo(tableName).withFields(cols.zip(row).toMap()).add() }
            return ExamTable(tableFrom(builder.build(), tableName), context.eval)
        }
    }

    private fun tableFrom(dataSet: IDataSet, tableName: String) =
        if (dataSet.tableNames.isEmpty()) DefaultTable(tableName) else dataSet.getTable(tableName)

    private fun parseCols(html: Html) =
        html.el.getFirstChildElement("thead")?.getFirstChildElement("tr")?.childElements?.map { it.text.trim() }
            ?: listOf()

    private fun parseValues(html: Html) =
        html.el.getFirstChildElement("tbody")?.childElements?.map { tr -> tr.childElements.map { it.text.trim() } }
            ?: listOf()
}
