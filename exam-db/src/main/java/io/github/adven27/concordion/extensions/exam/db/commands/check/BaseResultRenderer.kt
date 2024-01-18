package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.errorMessage
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.db.DbPlugin.ValuePrinter
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableExpectation
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableVerifier.ContentMismatch
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableVerifier.SizeMismatch
import io.github.adven27.concordion.extensions.exam.db.commands.ExamMatchersAwareValueComparer.Companion.isDbMatcher
import io.github.adven27.concordion.extensions.exam.db.commands.get
import io.github.adven27.concordion.extensions.exam.db.commands.renderTable
import org.concordion.api.CommandCall
import org.dbunit.assertion.Difference
import org.dbunit.dataset.ITable

@Suppress("TooManyFunctions")
open class BaseResultRenderer(private val printer: ValuePrinter) : DbCheckCommand.Renderer {

    override fun render(commandCall: CommandCall, result: DbCheckCommand.Result) {
        commandCall.html().below(if (result.check.fail == null) renderSuccess(result) else renderFail(result)).remove()
    }

    private fun renderSuccess(result: DbCheckCommand.Result) =
        renderTable(
            result.check.expected.table,
            { td, row, col -> td.success(result.check.expected.table[row, col], result.check.actual!![row, col]) },
            caption = result.caption,
            ifEmpty = { css("table-success") }
        )

    private fun Html.success(expected: Any?, actual: Any?): Html = this(
        Html(printer.wrap(actual)).tooltip(printer.print(expected), expected.isDbMatcher())
    ).css("table-success")

    private fun renderFail(result: DbCheckCommand.Result) = when (result.check.fail) {
        is SizeMismatch -> renderSizeMismatch(result.check.fail as SizeMismatch, result.check.expected, result.caption)
        is ContentMismatch -> renderContentMismatch(
            result.check.fail as ContentMismatch,
            result.check.expected,
            result.caption
        )

        else -> renderUnknownError(result.check.fail!!, result.check.expected, result.caption)
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
        { td, row, col -> diff[row, col]?.let { td.diff(it) } ?: td.success(expected[row, col], actual[row, col]) }

    private fun Html.diff(it: Difference) = this(
        Html("del", printer.print(it.expectedValue), "class" to "me-1"),
        Html("ins", printer.print(it.actualValue))
    ).css("table-danger").tooltip(it.failMessage)

    private operator fun List<Difference>.get(row: Int, col: String) =
        singleOrNull { it.rowIndex == row && it.columnName.equals(col, ignoreCase = true) }
}
