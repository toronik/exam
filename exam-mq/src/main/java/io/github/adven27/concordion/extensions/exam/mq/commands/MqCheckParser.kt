package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.mq.MqTester.TypedMessage

open class MqCheckParser : MqCheckCommand.Parser, BaseParser() {
    override fun parse(context: Context) = MqCheckCommand.Expected(
        queue = context.expression,
        messages = elements(context.el).mapNotNull { parse(it, context.eval)?.toCheckMessage() },
        exact = context[MqCheckCommand.CONTAINS]?.let { it.lowercase() == "exact" } ?: true,
        await = context.awaitConfig
    )

    private fun ParsedMessage.toCheckMessage() =
        ExpectedMessage(requireNotNull(content), verifier ?: content.type, headers, params)

    data class ExpectedMessage(
        override val content: Content,
        val verifier: String = content.type,
        override val headers: Map<String, String> = emptyMap(),
        override val params: Map<String, String> = emptyMap()
    ) : TypedMessage(content, headers, params)
}
