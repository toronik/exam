package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.commands.awaitConfig
import io.github.adven27.concordion.extensions.exam.core.errorMessage
import io.github.adven27.concordion.extensions.exam.core.escapeHtml
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.divCollapse
import io.github.adven27.concordion.extensions.exam.core.html.generateId
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.paragraph
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.core.html.td
import io.github.adven27.concordion.extensions.exam.core.toHtml
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Expected
import io.github.adven27.concordion.extensions.exam.mq.commands.MqParser.ParametrizedTypedMessage
import io.github.adven27.concordion.extensions.exam.mq.commands.MqVerifier.MessageVerifyingError
import io.github.adven27.concordion.extensions.exam.mq.commands.MqVerifier.SizeVerifyingError
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder

class MqCheckActualProvider(private val mqTesters: Map<String, MqTester>) : (String) -> Actual {
    override fun invoke(queue: String) = mqTesters.getOrFail(queue).let { Actual( it.accumulateOnRetries(), it.receive()) }

    private fun Map<String, MqTester>.getOrFail(mqName: String?): MqTester = this[mqName]
        ?: throw IllegalArgumentException("MQ with name '$mqName' not registered in MqPlugin")
}

open class MqCheckCommand(
    testers: Map<String, MqTester>,
    private val verifier: MqVerifier = MqVerifier(),
    private val actualProvider: (String) -> Actual = MqCheckActualProvider(testers),
    private val parser: Parser = Parser.Suitable(),
    private val renderer: Renderer = Renderer.Suitable()
) : MqCommand<Expected, Verifier.Check<Expected, Actual>>(testers, setOf(CONTAINS)) {
    companion object {
        const val CONTAINS = "contains"
    }

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>) =
        renderer.render(commandCall, result)

    override fun process(model: Expected, eval: Evaluator, recorder: ResultRecorder) =
        verifier.verify(eval, model) { actualProvider(model.queue) }.also {
            recorder.record(if (it.fail == null) SUCCESS else FAILURE)
        }

    data class Expected(
        val queue: String,
        val messages: List<ParametrizedTypedMessage> = listOf(),
        val exact: Boolean = true,
        val await: AwaitConfig?
    ) {
        override fun toString() =
            "Expected '$queue' has messages $messages in ${if (exact) "exact" else "any"} order. Await $await"
    }

    data class Actual(val partial: Boolean, val messages: List<MqTester.Message> = listOf())

    interface Parser {
        fun parse(context: Context): Expected

        open class Suitable : Parser {
            override fun parse(context: Context) = Expected(
                queue = context[NAME] ?: context.expression,
                messages = suitableParser(context).messages(context),
                exact = context[CONTAINS]?.let { it.lowercase() == "exact" } ?: true,
                await = context.el.awaitConfig()
            )

            private fun suitableParser(context: Context) =
                if (context.el.localName() == "div") MqXhtmlParser() else MqMdParser()
        }
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>)

        abstract class Base : Renderer {
            abstract fun root(html: Html): Html
            abstract fun caption(html: Html): Html?

            override fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>) {
                when (result.fail) {
                    null -> with(root(commandCall.html())) {
                        below(
                            template(
                                result.expected.queue,
                                result.expected.messages.map {
                                    MqVerifier.MessageVerifyResult(
                                        Result.success(it.headers),
                                        Result.success(ContentVerifier.ExpectedContent(it.content.type, it.body))
                                    )
                                }
                            ).toHtml()
                        )
                        caption(this)?.let { below(it) }
                        parent().remove(this)
                    }

                    else -> with(root(commandCall.html())) {
                        below(renderError(result))
                        caption(this)?.let { below(it) }
                        parent().remove(this)
                    }
                }
            }

            private fun renderError(result: Verifier.Check<Expected, Actual>) = when (result.fail) {
                is SizeVerifyingError -> renderSizeError(result.fail as SizeVerifyingError)
                is MessageVerifyingError -> renderMessageContentError(result.fail as MessageVerifyingError)
                else -> div()(
                    pre(result.fail!!.message),
                    pre(result.expected.toString())
                )
            }

            private fun renderMessageContentError(fail: MessageVerifyingError) =
                template(fail.queue, fail.expected).toHtml()

            private fun renderSizeError(fail: SizeVerifyingError) =
                errorMessage(
                    message = "Size verifying error: " + fail.message,
                    type = "json",
                    html = div()(
                        span("Expected:"),
                        template(
                            fail.queue,
                            fail.expected.map {
                                MqVerifier.MessageVerifyResult(
                                    Result.success(it.headers),
                                    Result.success(ContentVerifier.ExpectedContent(it.content.type, it.body))
                                )
                            }
                        ).toHtml(),
                        span("but was:"),
                        template(
                            fail.queue,
                            fail.actual.map {
                                MqVerifier.MessageVerifyResult(
                                    Result.success(it.headers),
                                    Result.success(ContentVerifier.ExpectedContent("text", it.body))
                                )
                            }
                        ).toHtml()
                    )
                ).second
        }

        open class Md : Base() {
            override fun root(html: Html): Html = html.parent().parent()
            override fun caption(html: Html): Html? = paragraph(html.childs().first().text())
        }

        open class Xhtml : Base() {
            override fun root(html: Html): Html = html
            override fun caption(html: Html): Html? = null
        }

        open class Suitable : Renderer {
            override fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>) {
                (if (commandCall.element.localName == "div") Xhtml() else Md()).render(commandCall, result)
            }
        }

        fun template(name: String, messages: List<MqVerifier.MessageVerifyResult>) = //language=html
            """
            <div class="mq-check">
                <table class="table table-sm caption-top">
                    <caption><i class="fa fa-envelope-open me-1"> </i><span>$name</span></caption>
                    <tbody> ${renderMessages(messages)} </tbody>
                </table>
            </div>
            """.trimIndent()

        // language=html
        private fun renderMessages(messages: List<MqVerifier.MessageVerifyResult>) = messages.joinToString("\n") { r ->
            """
            ${r.headers.fold(::renderHeaders, ::renderHeadersError)}
            <tr><td class='exp-body'>${r.content.fold(::renderBody, ::renderBodyError)}</td></tr>
            """.trimIndent()
        }.ifEmpty { """<tr><td class='exp-body'>EMPTY</td></tr>""" }

        // language=html
        private fun renderBody(body: ContentVerifier.ExpectedContent) =
            """<div class="${body.type} rest-success"></div>""".toHtml().text(body.pretty()).el.toXML()

        // language=html
        private fun renderBodyError(error: Throwable) = when (error) {
            is ContentVerifier.Fail -> errorMessage(
                message = error.details,
                type = "json",
                html = div("class" to "${error.type} rest-failure")(
                    Html("del", error.expected, "class" to "expected"),
                    Html("ins", error.actual, "class" to "actual")
                )
            ).second.el.toXML()

            else -> """<div class="json exp-body rest-failure">${error.message?.escapeHtml()}</div>"""
        }

        // language=html
        private fun renderHeaders(headers: Map<String, String?>) = if (headers.isNotEmpty()) {
            """
            <tr><td>
                <table class="table table-sm caption-top">
                    <caption class="small">Headers</caption>
                    <tbody> ${toRows(headers)} </tbody>
                </table>
            </td></tr>
            """.trimIndent()
        } else {
            ""
        }

        // language=html
        private fun renderHeadersError(error: Throwable) = when (error) {
            is MqVerifier.HeadersSizeVerifyingError -> "<tr><td> ${expectedButWas(error)} </td></tr>"
            is MqVerifier.HeadersVerifyingError -> "<tr><td> ${templateHeaders { toRows(error.result) }} </td></tr>"
            else -> "<tr><td> <code>${error.message?.escapeHtml()}</code> </td></tr>"
        }

        // language=html
        private fun expectedButWas(error: MqVerifier.HeadersSizeVerifyingError) = errorMessage(
            message = error.message ?: "",
            type = "json",
            html = div()(
                span("Expected:"),
                templateHeaders { toRows(error.expected) }.toHtml(),
                span("but was:"),
                templateHeaders { toRows(error.actual) }.toHtml()
            )
        ).second.el.toXML()

        // language=html
        private fun templateHeaders(rows: () -> String) = """
        <table class="table table-sm caption-top">
            <caption class="small"> Headers </caption>
            <tbody> ${rows()} </tbody>
        </table>
        """.trimIndent()

        // language=html
        private fun toRows(headers: Map<String, String?>) = headers.entries.joinToString("\n") { (k, v) ->
            """<tr><td class="rest-success">$k</td><td class="rest-success">${v?.escapeHtml()}</td></tr>"""
        }

        // language=html
        private fun toRows(headers: List<MqVerifier.HeaderCheckResult>) = headers.joinToString("\n") {
            "<tr> ${tdResult(it.actualKey, it.header.first)} ${tdResult(it.actualValue, it.header.second)} </tr>"
        }

        // language=html
        private fun tdResult(actual: String?, expected: String?) = if (actual == null && expected != null) {
            """<td class="rest-success">${expected.escapeHtml()}</td>"""
        } else {
            """<td class="rest-failure"><del>${expected?.escapeHtml()}</del><ins>${actual?.escapeHtml()}</ins></td>"""
        }

        private fun container(text: String, type: String, collapsable: Boolean) =
            if (collapsable) collapsableContainer(text, type) else fixedContainer(text, type)

        private fun collapsed(container: Html) = td("class" to "exp-body")(
            div().style("position: relative")(
                divCollapse("", container.attr("id").toString()).css("default-collapsed"),
                container
            )
        )

        private fun fixedContainer(text: String, type: String) = td(text).css("$type exp-body")

        private fun collapsableContainer(text: String, type: String) =
            div(text, "id" to generateId()).css("$type file collapse show")
    }
}
