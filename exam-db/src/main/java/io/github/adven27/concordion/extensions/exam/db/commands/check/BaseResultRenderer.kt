package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.errorMessage
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.db.DbPlugin.ValuePrinter
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetExpectation
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetVerifier
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetVerifier.Failed
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableExpectation
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableVerifier.ContentMismatch
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableVerifier.SizeMismatch
import io.github.adven27.concordion.extensions.exam.db.commands.ExamMatchersAwareValueComparer.Companion.isMatcher
import io.github.adven27.concordion.extensions.exam.db.commands.get
import io.github.adven27.concordion.extensions.exam.db.commands.renderTable
import io.github.adven27.concordion.extensions.exam.db.commands.tableName
import org.awaitility.core.ConditionTimeoutException
import org.concordion.api.CommandCall
import org.dbunit.assertion.Difference
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable

@Suppress("TooManyFunctions")
open class BaseResultRenderer(
    private val printer: ValuePrinter,
    private val renderer: Renderer = Renderer(printer)
) : DbCheckCommand.Renderer {

    override fun render(commandCall: CommandCall, result: DbCheckCommand.Result) {
        when (result.check.expected) {
            is TableExpectation -> rend(commandCall, result.check as Verifier.Check<TableExpectation, IDataSet>)
            is DataSetExpectation -> renderer.render(
                commandCall.html(),
                result.check as Verifier.Check<DataSetExpectation, IDataSet>
            )
        }
    }

    private fun rend(commandCall: CommandCall, result: Verifier.Check<TableExpectation, IDataSet>) {
        commandCall.html().below(if (result.fail == null) renderSuccess(result) else renderFail(result)).remove()
    }

    private fun renderSuccess(result: Verifier.Check<TableExpectation, IDataSet>) =
        renderTable(
            result.expected.table,
            { td, row, col ->
                result.expected.table.let {
                    td.success(
                        it.tableName(),
                        col,
                        it[row, col],
                        result.actual!!.iterator().apply { next() }.table[row, col]
                    )
                }
            },
            ifEmpty = { css("table-success") }
        )

    private fun Html.success(table: String, col: String, expected: Any?, actual: Any?): Html = this(
        Html(printer.wrap(table, col, actual)).tooltip(printer.print(table, col, expected), expected.isMatcher())
    ).css("table-success")

    private fun renderFail(result: Verifier.Check<TableExpectation, IDataSet>): Html = with(result) {
        when (fail) {
            is SizeMismatch -> renderSizeMismatch(fail as SizeMismatch, expected, null)
            is ContentMismatch -> renderContentMismatch(fail as ContentMismatch, expected, null)
            is ConditionTimeoutException -> renderFail(copy(fail = (fail as ConditionTimeoutException).cause))

            else -> renderUnknownError(fail!!, expected, null)
        }
    }

    private fun renderSizeMismatch(fail: SizeMismatch, expected: TableExpectation, caption: String?) = errorTemplate(
        expected,
        fail,
        caption,
        butWas = renderTable(fail.actual, printer)
    )

    private fun renderContentMismatch(fail: ContentMismatch, expected: TableExpectation, caption: String?) =
        errorTemplate(
            expected = expected,
            fail = fail,
            caption = caption,
            expectedTable = renderTable(
                expected.table,
                cellFailure(expected.table, fail.diff.first().actualTable, fail.diff),
                caption
            )
        )

    private fun renderUnknownError(fail: Throwable, expected: TableExpectation, caption: String?) =
        errorTemplate(expected, fail, caption)

    private fun errorTemplate(
        expected: TableExpectation,
        fail: Throwable,
        caption: String?,
        expectedTable: Html = renderTable(expected.table, printer, caption),
        butWas: Html? = null
    ) = errorMessage(
        message = expected.await?.timeoutMessage(fail) ?: fail.rootCauseMessage(),
        type = "json",
        html = div()(
            span("Expected:"),
            expectedTable,
            span("but was:").takeIf { butWas != null },
            butWas
        )
    ).second

    private fun cellFailure(expected: ITable, actual: ITable, diff: List<Difference>): (Html, Int, String) -> Html =
        { td, row, col ->
            diff[row, col]?.let { td.diff(it) }
                ?: td.success(expected.tableName(), col, expected[row, col], actual[row, col])
        }

    private fun Html.diff(d: Difference): Html {
        val act = d.actualValue
        val exp = if (act is Content) Content(body = d.expectedValue.toString(), type = act.type) else d.expectedValue
        return this(
            Html("del", printer.print(d.actualTable.tableName(), d.columnName, exp)).css("expected"),
            Html("ins", printer.print(d.actualTable.tableName(), d.columnName, act)).css("actual")
        ).css("${if (act is Content) act.type else ""} failure").tooltip(d.failMessage)
    }

    private operator fun List<Difference>.get(row: Int, col: String) =
        singleOrNull { it.rowIndex == row && it.columnName.equals(col, ignoreCase = true) }
}

open class Renderer(private val printer: ValuePrinter) {

    fun render(html: Html, result: Verifier.Check<DataSetExpectation, IDataSet>) {
        html
            .apply { el.addAttribute("hidden", "true") }
            .parent()
            .apply {
                result.expected.dataset.tables.zip(result.actual!!.tables).map { (e, a) ->
                    when (result.fail) {
                        null -> renderSuccess(e, a)
                        is Failed -> (result.fail as Failed).fails
                            .filter { it.expected.tableName().equals(e.tableName(), ignoreCase = true) }
                            .also { check(it.size < 2) }
                            .singleOrNull()
                            ?.let {
                                when (it) {
                                    is DataSetVerifier.ContentMismatch -> renderFail(it)
                                    is DataSetVerifier.SizeMismatch -> renderFail(it)
                                }
                            } ?: renderSuccess(e, a)

                        else -> throw result.fail!!
                    }
                }
            }
    }

    private fun Html.renderFail(fail: DataSetVerifier.SizeMismatch) {
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

    private fun Html.renderFail(diff: DataSetVerifier.ContentMismatch) = this(
        renderTable(
            t = diff.expected,
            cell = markAsSuccessOrFail(diff),
            ifEmpty = { el.addStyleClass("table-success").appendNonBreakingSpaceIfBlank() }
        )
    )

    private fun markAsSuccessOrFail(fail: DataSetVerifier.ContentMismatch): (Html, Int, String) -> Html =
        { td, row, col ->
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
