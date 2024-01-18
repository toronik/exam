package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier.ExpectedContent
import io.github.adven27.concordion.extensions.exam.core.commands.VerifyFailureEvent
import io.github.adven27.concordion.extensions.exam.core.commands.VerifyListener
import io.github.adven27.concordion.extensions.exam.core.commands.VerifySuccessEvent
import io.github.adven27.concordion.extensions.exam.core.errorMessage
import io.github.adven27.concordion.extensions.exam.core.escapeHtml
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.core.toHtml
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.NoSqlVerifier.DocumentVerifyingError
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.NoSqlVerifier.SizeVerifyingError

class HtmlCheckRenderer : VerifyListener<Expected, Actual> {

    override fun successReported(event: VerifySuccessEvent<Expected, Actual>) = with(event.element) {
        appendSister(
            template(
                event.expected.collection,
                event.expected.documents.map {
                    Result.success(ExpectedContent("json", it.body))
                }
            ).toHtml().el
        )
        parentElement.removeChild(this)
    }

    override fun failureReported(event: VerifyFailureEvent<Expected>) =
        with(event.element) {
            appendSister(
                when (event.fail) {
                    is SizeVerifyingError -> renderSizeError(event)
                    is DocumentVerifyingError -> renderMessageContentError(event)
                    else -> div()(
                        pre(event.fail.toString()),
                        pre(event.expected.toString())
                    ).el
                }
            )
            parentElement.removeChild(this)
        }

    private fun renderSizeError(event: VerifyFailureEvent<Expected>) =
        errorMessage(
            message = "Size verifying error: " + event.fail.message,
            type = "json",
            html = div()(
                span("Expected:"),
                template(
                    event.expected.collection,
                    event.expected.documents.map {
                        Result.success(ExpectedContent("json", it.body))
                    }
                ).toHtml(),
                span("but was:"),
                template(
                    event.expected.collection,
                    (event.fail as SizeVerifyingError).actual.map {
                        Result.success(ExpectedContent("text", it.body))
                    }
                ).toHtml()
            )
        ).second.el

    private fun renderMessageContentError(event: VerifyFailureEvent<Expected>) =
        template(event.expected.collection, (event.fail as DocumentVerifyingError).expected).toHtml().el

    private fun template(name: String, docResults: List<Result<ExpectedContent>>) = //language=html
        """
    <div class="nosql-check">
        <table class="table table-sm caption-top">
            <caption><i class="fa fa-database me-1"> </i><span>$name</span></caption>
            <tbody> ${renderDocs(docResults)} </tbody>
        </table>
    </div>
        """.trimIndent()

    // language=html
    private fun renderDocs(docResults: List<Result<ExpectedContent>>) = docResults.joinToString("\n") { result ->
        """
    <tr><td class='exp-body'>${result.fold(::renderBody, ::renderBodyError)}</td></tr>
        """.trimIndent()
    }.ifEmpty { """<tr><td class='exp-body'>EMPTY</td></tr>""" }

    // language=html
    private fun renderBody(body: ExpectedContent) =
        """<div class="${body.type} success"></div>""".toHtml().text(body.pretty()).el.toXML()

    // language=html
    private fun renderBodyError(error: Throwable) = when (error) {
        is ContentVerifier.Fail -> errorMessage(
            message = error.details,
            type = "json",
            html = div("class" to "${error.type} failure")(
                Html("del", error.expected, "class" to "expected"),
                Html("ins", error.actual, "class" to "actual")
            )
        ).second.el.toXML()

        else -> """<div class="json exp-body failure">${error.message?.escapeHtml()}</div>"""
    }
}
