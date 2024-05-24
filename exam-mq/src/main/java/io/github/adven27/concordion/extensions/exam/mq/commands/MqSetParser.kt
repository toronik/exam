package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.resolve
import io.github.adven27.concordion.extensions.exam.mq.MqTester.TypedMessage

open class MqSetParser : MqSetCommand.Parser, BaseParser() {
    override fun parse(context: Context) = MqSetCommand.Model(
        queue = parseQueue(context),
        messages = elements(context.el).mapNotNull { item ->
            parse(item, context.eval)?.let { TypedMessage(requireNotNull(it.content), it.headers, it.params) }
        }
    )

    private fun parseQueue(context: Context) = context.eval.resolve(
        context.expression.takeUnless { it.isBlank() }
            ?: requireNotNull(parseTitle(context.el)) { "Absent queue name" }
    )

    private fun parseTitle(html: Html) = with(html) {
        when {
            localName() == "table" -> childOrNull("caption")?.text()
            localName() == "code" -> parent().parent().parent().childOrNull { it.hasClass("title") }?.text()
            else -> parent().childOrNull { it.hasClass("title") }?.text()
        }
    }
}
