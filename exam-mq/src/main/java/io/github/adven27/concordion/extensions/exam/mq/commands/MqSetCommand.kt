package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.escapeHtml
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.paragraph
import io.github.adven27.concordion.extensions.exam.core.pretty
import io.github.adven27.concordion.extensions.exam.core.toHtml
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.commands.MqParser.ParametrizedTypedMessage
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder

open class MqSetCommand(
    testers: Map<String, MqTester>,
    private val parser: Parser = Parser.Suitable(),
    private val renderer: Renderer = Renderer.Suitable()
) : MqCommand<MqSetCommand.Model, Result<MqSetCommand.Model>>(testers) {

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Result<Model>) = renderer.render(commandCall, result)

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = runCatching {
        model.apply {
            mqTesters[queue]!!.let { tester ->
                messages.takeIf { it.isNotEmpty() }?.forEach { tester.send(it, it.params) } ?: tester.purge()
            }
        }
    }

    data class Model(val queue: String, val messages: List<ParametrizedTypedMessage> = listOf())

    interface Parser {
        fun parse(context: Context): Model

        open class Md : MqMdParser(), Parser {
            override fun parse(context: Context) = Model(
                queue = context[NAME] ?: context.expression,
                messages = messages(context)
            )
        }

        open class Xhtml : MqXhtmlParser(), Parser {
            override fun parse(context: Context) = Model(
                queue = context[NAME] ?: context.expression,
                messages = messages(context)
            )
        }

        open class Suitable : Parser {
            override fun parse(context: Context) =
                (if (context.el.localName() == "div") Xhtml() else Md()).parse(context)
        }
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Result<Model>)

        abstract class Base : Renderer {
            abstract fun root(html: Html): Html
            abstract fun caption(html: Html): Html?

            override fun render(commandCall: CommandCall, result: Result<Model>) {
                result.onSuccess {
                    with(root(commandCall.html())) {
                        below(template(it.queue, it.messages).toHtml())
                        caption(this)?.let { below(it) }
                        parent().remove(this)
                    }
                }
            }
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
            override fun render(commandCall: CommandCall, result: Result<Model>) {
                (if (commandCall.element.localName == "div") Xhtml() else Md()).render(commandCall, result)
            }
        }

        fun template(name: String, messages: List<ParametrizedTypedMessage>) = //language=html
            """
        <div class="mq-set">
            <table class="table table-sm caption-top">
                <caption><i class="fa fa-envelope me-1"> </i><span>$name</span></caption>
                <tbody> ${renderMessages(messages)} </tbody>
            </table>
        </div>
            """.trimIndent()

        //language=html
        private fun renderMessages(messages: List<ParametrizedTypedMessage>) = messages.joinToString("\n") {
            """
        ${renderHeaders(it.headers)}
        <tr><td class='exp-body'>${renderBody(it)}</td></tr>
            """.trimIndent()
        }.ifEmpty { """<tr><td class='exp-body'>EMPTY</td></tr>""" }

        // language=html
        private fun renderBody(msg: ParametrizedTypedMessage): String =
            """<div class="${msg.content.type}"></div>""".toHtml().text(msg.body.pretty(msg.content.type)).el.toXML()

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
        private fun toRows(headers: Map<String, String?>) = headers.entries.joinToString("\n") { (k, v) ->
            """<tr><td>$k</td><td>${v?.escapeHtml()}</td></tr>"""
        }
    }
}
