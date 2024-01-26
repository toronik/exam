package io.github.adven27.concordion.extensions.exam.mq.commands

import com.github.jknack.handlebars.internal.text.StringEscapeUtils.escapeJava
import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier.Fail
import io.github.adven27.concordion.extensions.exam.core.commands.EqCommand.Companion.objectToString
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.errorMessage
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.core.html.toHtml
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Expected
import io.github.adven27.concordion.extensions.exam.mq.commands.MqVerifier.MessageVerifyResult
import io.github.adven27.concordion.extensions.exam.mq.commands.MqVerifier.MessageVerifyingError
import io.github.adven27.concordion.extensions.exam.mq.commands.MqVerifier.SizeVerifyingError
import org.concordion.api.CommandCall

@Suppress("TooManyFunctions")
interface MqCheckRenderer {
    fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>)

    open class Base : MqCheckRenderer {
        override fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>) {
            when (result.fail) {
                null -> with(commandCall.html()) {
                    below(
                        template(
                            caption(this)?.takeIf(String::isNotBlank) ?: result.expected.queue,
                            result.expected.messages.map {
                                MessageVerifyResult(
                                    headers = Result.success(it.headers),
                                    params = Result.success(it.params),
                                    content = Result.success(Content(body = it.body, type = it.content.type))
                                )
                            },
                            "success"
                        ).toHtml()
                    )
                    parent().remove(this)
                }

                else -> with(commandCall.html()) {
                    below(renderError(result))
                    parent().remove(this)
                }
            }
        }

        private fun caption(html: Html) = html.childOrNull("caption")?.text()

        private fun renderError(result: Verifier.Check<Expected, Actual>) = when (result.fail) {
            is SizeVerifyingError -> renderSizeError(result.fail as SizeVerifyingError)
            is MessageVerifyingError -> renderMessageContentError(result.fail as MessageVerifyingError)
            else -> div()(
                pre(result.fail!!.message),
                pre(result.expected.toString())
            )
        }

        private fun renderMessageContentError(fail: MessageVerifyingError) =
            template(fail.queue, fail.expected, "failure").toHtml()

        private fun renderSizeError(fail: SizeVerifyingError) =
            errorMessage(
                message = "Size verifying error: " + fail.message,
                type = "json",
                html = div()(
                    span("Expected:"),
                    template(
                        fail.queue,
                        fail.expected.map {
                            MessageVerifyResult(
                                headers = Result.success(it.headers),
                                params = Result.success(it.params),
                                content = Result.success(Content(body = it.body, type = it.content.type))
                            )
                        },
                        "success"
                    ).toHtml(),
                    span("but was:"),
                    template(
                        fail.queue,
                        fail.actual.map {
                            MessageVerifyResult(
                                headers = Result.success(it.headers),
                                params = Result.success(it.params),
                                content = Result.success(Content.Text(it.body))
                            )
                        },
                        "failure"
                    ).toHtml()
                )
            ).second
    }

    fun template(name: String, messages: List<MessageVerifyResult>, style: String) = //language=html
        """
        <div class="mq-check">
            <table class="tableblock frame-ends grid-rows stretch">
                <caption><i class="fa fa-envelope-open me-1"> </i><span>$name</span></caption>
                <tbody> ${renderMessages(messages, style)} </tbody>
            </table>
        </div>
        """.trimIndent()

    // language=html
    private fun renderMessages(messages: List<MessageVerifyResult>, style: String) = messages.joinToString("\n") { r ->
        """
        <tr><td class='exp-body'>
        ${r.params.fold({ renderProps("Params", it, style) }, ::renderError)}
        ${r.headers.fold({ renderProps("Headers", it, style) }, ::renderError)}
        ${r.content.fold({ renderContent(it, style) }, ::renderContentError)}
        </td></tr>
        """.trimIndent()
    }.ifEmpty { """<tr><td class='exp-body $style'>EMPTY</td></tr>""" }

    // language=html
    private fun renderContent(content: Content, style: String) =
        """<div class="${content.type} $style"></div>""".toHtml().text(content.pretty()).el.toXML()

    // language=html
    private fun renderContentError(error: Throwable) = when (error) {
        is Fail -> errorMessage(
            message = error.details,
            type = "json",
            html = div("class" to "${error.type} failure")(
                Html("del", error.expected, "class" to "expected"),
                Html("ins", error.actual, "class" to "actual")
            )
        ).second.el.toXML()

        else -> """<div class="json exp-body failure"><pre>${error.message}</pre></div>"""
    }

    // language=html
    private fun renderProps(caption: String, props: Map<String, String?>, style: String) =
        props.takeIf { it.isNotEmpty() }?.let {
            """
            <table class="table table-sm caption-top">
                <caption class="small">$caption</caption>
                <tbody> ${toRows(props, style)} </tbody>
            </table>
            """.trimIndent()
        } ?: ""

    private fun renderError(error: Throwable) = errorMessage(message = error.rootCauseMessage()).second.el.toXML()

    // language=html
    private fun toRows(headers: Map<String, String?>, style: String) = headers.entries.joinToString("\n") { (k, v) ->
        "<tr><td class='$style'>$k</td><td class='$style'>${renderValue(v)}</td></tr>"
    }

    fun renderValue(v: String?): String = runCatching { pre(objectToString(v)).el.toXML() }
        .recoverCatching { pre(escapeJava(objectToString(v))).el.toXML() }
        .getOrElse { pre(it.rootCauseMessage()).el.toXML() }
}
