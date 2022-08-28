package io.github.adven27.concordion.extensions.exam.nosql.commands

import io.github.adven27.concordion.extensions.exam.core.commands.VarsAttrs
import io.github.adven27.concordion.extensions.exam.core.content
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.resolveForContentType
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import org.concordion.api.CommandCall
import org.concordion.api.Element
import org.concordion.api.Evaluator

class HtmlNoSqlParser {

    fun parse(command: CommandCall, eval: Evaluator): List<NoSqlDocument> =
        with(command.element) {
            childElements
                .filter { it.localName == "doc" }
                .map {
                    toDocument(it, eval)
                }.ifEmpty {
                    if (getAttributeValue("from") != null || text.isNotEmpty()) listOf(toDocument(this, eval))
                    else listOf()
                }
        }

    private fun toDocument(el: Element, eval: Evaluator): NoSqlDocument {
        val doc = Html(el)
        val from: String? = doc.attr("from")
        return NoSqlDocument(
            eval.resolveForContentType(
                VarsAttrs(doc, eval).let { doc.content(from, eval) }, "json"
            )
        )
    }
}
