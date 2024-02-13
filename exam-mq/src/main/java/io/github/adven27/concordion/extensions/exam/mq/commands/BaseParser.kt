package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.resolve
import org.concordion.api.Evaluator

open class BaseParser {
    private fun parseHeaders(el: Html, eval: Evaluator) = el.childOrNull("thead")
        ?.child("tr")
        ?.childs()
        ?.map(Html::text)
        ?.zip(el.child("tbody", "tr").childs().map { it.resolveAndSwap(eval) })?.toMap()
        ?: el.child("tbody").childs().associate { it.child("th").text() to it.child("td").resolveAndSwap(eval) }

    private fun Html.resolveAndSwap(eval: Evaluator) = eval.resolve(text()).also { el.swapText(it) }

    protected fun elements(html: Html): List<Html> = when {
        html.parent().localName() == "pre" -> listOf(html.parent())
        html.parent().let { it.hasClass("openblock") || it.hasClass("listingblock") } -> listOf(html)
        html.localName() == "table" ->
            html.childOrNull("tbody")?.childs("tr")?.mapNotNull { it.childs("td").firstOrNull()?.childOrNull("div") }
                ?: listOf()

        else -> throw UnsupportedOperationException("Unsupported markup $html")
    }

    private fun parseMessage(item: Html, msg: ParsedMessage, eval: Evaluator): ParsedMessage = when {
        item.localName() == "table" && item.hasClass("headers") -> msg.copy(headers = parseHeaders(item, eval))
        item.localName() == "table" && item.hasClass("params") -> msg.copy(params = parseHeaders(item, eval))
        item.localName() == "pre" -> parseContent(msg, item, eval)
        item.localName() == "code" -> parseContent(msg, item.parent(), eval)
        item.hasClass("listingblock") -> parseContent(msg, item.child { it.hasClass("content") }.child("pre"), eval)

        else -> msg
    }

    private fun parseContent(msg: ParsedMessage, item: Html, eval: Evaluator): ParsedMessage = msg.copy(
        content = Content(item.resolveAndSwap(eval), item.child("code").attr("data-lang") ?: "text"),
        verifier = item.child("code").el.getAttributeValue("verifier", ExamExtension.NS)
    )

    protected fun parse(item: Html, eval: Evaluator) = item.childs()
        .fold(ParsedMessage()) { acc, el -> parseMessage(el, acc, eval) }
        .takeIf { it.content != null }

    data class ParsedMessage(
        val content: Content? = null,
        val verifier: String? = null,
        val headers: Map<String, String> = mapOf(),
        val params: Map<String, String> = mapOf()
    )
}
