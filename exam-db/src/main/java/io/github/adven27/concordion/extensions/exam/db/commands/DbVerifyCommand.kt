package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetExpectation
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetFilesExpectation
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetVerifier.ContentMismatch
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetVerifier.Failed
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetVerifier.SizeMismatch
import io.github.adven27.concordion.extensions.exam.db.commands.ExamMatchersAwareValueComparer.Companion.isMatcher
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.dbunit.assertion.Difference
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable

open class DbVerifyCommand(
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    private val renderer: Renderer = Renderer(valuePrinter)
) : DbCommand<DataSetFilesExpectation, Verifier.Check<DataSetExpectation, IDataSet>>(dbTester, setOf(ORDER_BY)) {
    companion object {
        private const val ORDER_BY = "orderBy"
    }

    override fun model(context: Context) = DataSetFilesExpectation(
        ds = context.el.getAttr(DS),
        datasets = context.expression.split(",").map { context.el.getAttr("dir") + it.trim() },
        await = context.awaitConfig,
        orderBy = (context[ORDER_BY] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    )

    override fun process(model: DataSetFilesExpectation, eval: Evaluator, recorder: ResultRecorder) =
        dbTester.test(model, eval).apply {
            val tables = expected.dataset.tableNames.toList()
            when (fail) {
                null -> tables to listOf()
                is Failed -> tables.partition { it !in (fail as Failed).tables }
                else -> listOf<ITable>() to tables
            }.let { (passed, failed) ->
                passed.onEach { recorder.record(SUCCESS) }
                failed.onEach { recorder.record(FAILURE) }
            }
        }

    override fun render(commandCall: CommandCall, result: Verifier.Check<DataSetExpectation, IDataSet>) =
        renderer.render(commandCall.html(), result)

    open class Renderer(private val printer: DbPlugin.ValuePrinter) {

        fun render(html: Html, result: Verifier.Check<DataSetExpectation, IDataSet>) {
            html.apply {
                result.expected.dataset.tables.zip(result.actual!!.tables).map { (e, a) ->
                    when (result.fail) {
                        null -> renderSuccess(e, a)
                        is Failed -> (result.fail as Failed).fails
                            .filter { it.expected.tableName() == e.tableName() }
                            .also { check(it.size < 2) }
                            .singleOrNull()
                            ?.let {
                                when (it) {
                                    is ContentMismatch -> renderFail(it)
                                    is SizeMismatch -> renderFail(it)
                                }
                            } ?: renderSuccess(e, a)

                        else -> throw result.fail!!
                    }
                }
            }
        }

        private fun Html.renderFail(fail: SizeMismatch) {
            this(
                div().css("failure bd-callout bd-callout-danger")(div(fail.rowsMismatch.message))(
                    span("Expected: "),
                    render(fail.expected),
                    span("but was: "),
                    render(fail.actual)
                )
            )
        }

        private fun render(table: ITable): Html =
            renderTable(table, { td, row, col -> td()(Html(printer.wrap(table.tableName(), col, table[row, col]))) })

        private fun Html.renderSuccess(expected: ITable, actual: ITable) = this(
            renderTable(
                t = expected,
                cell = { td, row, col ->
                    td.markAsSuccess(expected.tableName(), col, expected[row, col], actual[row, col])
                },
                ifEmpty = { el.addStyleClass("table-success").appendNonBreakingSpaceIfBlank() }
            )
        )

        private fun Html.renderFail(diff: ContentMismatch) = this(
            renderTable(
                t = diff.expected,
                cell = markAsSuccessOrFail(diff),
                ifEmpty = { el.addStyleClass("table-success").appendNonBreakingSpaceIfBlank() }
            )
        )

        private fun markAsSuccessOrFail(fail: ContentMismatch): (Html, Int, String) -> Html = { td, row, col ->
            fail.diff(row, col)
                ?.let { td.markAsFailure(it) }
                ?: td.markAsSuccess(fail.expected.tableName(), col, fail.expected[row, col], fail.actual[row, col])
        }

        private fun Html.markAsFailure(d: Difference) = this(
            Html("del", printer.print(d.expectedTable.tableName(), d.columnName, d.expectedValue), "class" to "me-1"),
            Html("ins", printer.print(d.actualTable.tableName(), d.columnName, d.actualValue))
        ).css("table-danger").tooltip(d.failMessage)

        private fun Html.markAsSuccess(table: String, col: String, expected: Any?, actual: Any?): Html = this(
            Html(printer.wrap(table, col, actual)).tooltip(printer.print(table, col, expected), expected.isMatcher())
        ).css("table-success")
    }
}
