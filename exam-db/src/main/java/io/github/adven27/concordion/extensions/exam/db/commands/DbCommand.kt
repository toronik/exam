@file:Suppress("TooManyFunctions")

package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.VarsAttrs
import io.github.adven27.concordion.extensions.exam.core.html.CLASS
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.caption
import io.github.adven27.concordion.extensions.exam.core.html.italic
import io.github.adven27.concordion.extensions.exam.core.html.table
import io.github.adven27.concordion.extensions.exam.core.html.tbody
import io.github.adven27.concordion.extensions.exam.core.html.td
import io.github.adven27.concordion.extensions.exam.core.html.th
import io.github.adven27.concordion.extensions.exam.core.html.thead
import io.github.adven27.concordion.extensions.exam.core.html.tr
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.DS
import org.concordion.api.Evaluator
import org.dbunit.dataset.Column
import org.dbunit.dataset.ITable
import org.dbunit.dataset.filter.DefaultColumnFilter

abstract class DbCommand<M, R>(protected val dbTester: DbTester, attrs: Set<String> = setOf()) :
    ExamCommand<M, R>(setOf(DS, TABLE, CAPTION, WHERE, COLS, OPERATION) + attrs) {

    companion object {
        const val DS = "ds"
        const val TABLE = "table"
        const val CAPTION = "caption"
        const val WHERE = "where"
        const val COLS = "cols"
        const val OPERATION = "operation"
    }
}

fun ITable.tableName(): String = this.tableMetaData.tableName
fun ITable.columns(): Array<Column> = this.tableMetaData.columns
fun ITable.columnNames() = this.tableMetaData.columns.map { it.columnName }
fun ITable.columnNamesArray() = this.columnNames().toTypedArray()
fun ITable.columnsSortedBy(sort: (o1: String, o2: String) -> Int) = columnNames().sortedWith(Comparator(sort))

fun ITable.withColumnsAsIn(expected: ITable): ITable =
    DefaultColumnFilter.includedColumnsTable(this, expected.columns())

operator fun ITable.get(row: Int, col: String): Any? = this.getValue(row, col)
operator fun ITable.get(row: Int, col: Column): Any? = this[row, col.columnName]

fun <R> ITable.mapRows(transform: (Int) -> R): List<R> = (0 until this.rowCount).map(transform)

fun renderTable(
    t: ITable,
    valuePrinter: DbPlugin.ValuePrinter,
    caption: String? = null,
    remarks: Map<String, Int> = emptyMap()
): Html = renderTable(
    t,
    { td, row, col -> td()(Html(valuePrinter.wrap(t[row, col]))) },
    caption,
    { col: String -> if (remarks.containsKey(col)) "table-info" else "" },
    { col1, col2 -> -compareValues(remarks[col1], remarks[col2]) }
)

@Suppress("LongParameterList", "SpreadOperator")
fun renderTable(
    t: ITable,
    cell: (Html, Int, String) -> Html,
    caption: String? = null,
    styleCol: (String) -> String = { "" },
    sortCols: (col1: String, col2: String) -> Int = { _, _ -> 0 },
    ifEmpty: Html.() -> Unit = { }
): Html {
    val cols = t.columnsSortedBy(sortCols)
    return table()(
        tableCaption(caption, t.tableName()),
        if (t.empty()) thead()(tr()(th())) else thead()(tr()(cols.map { th(it, CLASS to styleCol(it)) })),
        tbody()(
            if (t.empty()) {
                listOf(tr()(td("EMPTY").attrs("colspan" to "${cols.size}").apply(ifEmpty)))
            } else {
                t.mapRows { row -> cols.map { cell(td(CLASS to styleCol(it)), row, it) } }
                    .map { tr()(*it.toTypedArray()) }
            }
        )
    )
}

private fun ITable.empty() = this.rowCount == 0

fun tableCaption(title: String?, def: String?): Html = caption()
    .style("width:max-content")(italic(" ", CLASS to "fa fa-database me-1"))
    .text("  ${if (!title.isNullOrBlank()) title else def}")

data class DatasetCommandAttrs(
    val datasets: DatasetsAttrs,
    val vars: VarsAttrs,
    val ds: String?
) {
    data class DatasetsAttrs(val datasets: String, val dir: String) {
        fun dataSets() = datasets.split(",").map { dir.trim() + it.trim() }
    }

    companion object {
        private const val DIR = "dir"
        private const val DATASETS = "datasets"

        fun from(root: Html, evaluator: Evaluator) = DatasetCommandAttrs(
            DatasetsAttrs(root.attrOrFail(DATASETS), root.getAttr(DIR, "")),
            VarsAttrs(root, evaluator),
            root.getAttr(DS)
        )
    }
}
