package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.commands.ContentAttrs
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.headers
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.readFile
import io.github.adven27.concordion.extensions.exam.core.resolve
import io.github.adven27.concordion.extensions.exam.core.resolveNoType
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCommand.Companion.CONTENT_TYPE
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCommand.Companion.FROM
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCommand.Companion.HEADERS
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCommand.Companion.PARAMS
import io.github.adven27.concordion.extensions.exam.mq.commands.MqMdParser.RawMessage.Body
import org.concordion.api.Evaluator

interface MqParser {
    open class TypedMessage(val content: Content, headers: Map<String, String> = emptyMap()) :
        MqTester.Message(content.body, headers)

    open class ParametrizedTypedMessage(
        content: Content,
        headers: Map<String, String> = emptyMap(),
        val params: Map<String, String> = emptyMap()
    ) : TypedMessage(content, headers)

    fun messages(context: ExamCommand.Context): List<ParametrizedTypedMessage>
}

open class MqXhtmlParser : MqParser {
    override fun messages(context: ExamCommand.Context) = with(context.el) {
        childs()
            .filter { it.localName() == "message" }
            .map { toMessage(it, context.eval) }
            .ifEmpty {
                if (this[FROM] != null || text().isNotEmpty()) listOf(toMessage(context.el, context.eval)) else listOf()
            }
    }

    protected fun toMessage(item: Html, eval: Evaluator) = with(MessageAttrs(item, eval)) {
        MqParser.ParametrizedTypedMessage(
            content,
            headers.mapValues { eval.resolveNoType(it.value) },
            params.mapValues { eval.resolveNoType(it.value) }
        )
    }

    class MessageAttrs(el: Html, eval: Evaluator) {
        val content: Content = ContentAttrs(el, eval).content
        val headers: Map<String, String> = el[HEADERS].attrToMap()
        val params: Map<String, String> = el[PARAMS].attrToMap()

        private fun String?.attrToMap(): Map<String, String> = this?.headers()?.mapValues { it.value } ?: emptyMap()
    }
}

open class MqMdParser : MqParser {
    override fun messages(context: ExamCommand.Context) = elements(context.el).mapNotNull(toMessage(context.eval))

    protected fun toMessage(evaluator: Evaluator) = { item: Html -> parse(item)?.let { toMessage(it, evaluator) } }

    private fun parse(item: Html) = item.childs()
        .fold(RawMessage()) { acc, el -> rawMessage(el, acc) }
        .takeIf { it.body != null }

    private fun toMessage(raw: RawMessage, evaluator: Evaluator) = MqParser.ParametrizedTypedMessage(
        evaluator.resolve(
            Content(
                body = raw.body!!.let {
                    it.vars.onEach { (name, value) -> evaluator.setVariable("#$name", evaluator.resolveToObj(value)) }
                    it.template
                },
                type = raw.type
            )
        ),
        raw.headers.mapValues { evaluator.resolveNoType(it.value) },
        raw.params.mapValues { evaluator.resolveNoType(it.value) }
    )

    private fun rawMessage(item: Html, msg: RawMessage): RawMessage = when (item.localName()) {
        "pre" -> msg.copy(body = Body(item.text()))
        "a" -> msg.copy(body = parseBody(item))
        "code" -> parseOption(item).let { (name, value) -> if (name == CONTENT_TYPE) msg.copy(type = value) else msg }
        "em" -> msg.copy(headers = msg.headers + parseOption(item))
        "strong" -> msg.copy(params = msg.params + parseOption(item))
        "p" -> item.childs().fold(msg) { acc, el -> rawMessage(el, acc) }
        else -> msg
    }

    protected fun elements(html: Html) = root(html).let {
        when (it.localName()) {
            "dl" -> it.childs().filter { el -> el.localName() == "dd" }
            "ul" -> it.childs().drop(1)
            else -> throw UnsupportedOperationException("Unsupported tag ${it.localName()}")
        }
    }

    protected fun root(html: Html) = html.parent().parent()

    protected fun parseBody(link: Html) = Body(
        template = link.attr("href")!!.readFile(),
        vars = link.childs().filter { it.localName() == "code" }.associate { parseOption(it) }
    )

    protected fun parseOption(el: Html) = el.text().split("=", limit = 2).let { it[0] to it[1] }

    data class RawMessage(
        val type: String = "json",
        val headers: Map<String, String> = mapOf(),
        val params: Map<String, String> = mapOf(),
        val body: Body? = null
    ) {
        data class Body(val template: String, val vars: Map<String, String> = mapOf())
    }
}
