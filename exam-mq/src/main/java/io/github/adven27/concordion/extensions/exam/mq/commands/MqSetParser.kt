package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.mq.MqTester.TypedMessage

open class MqSetParser : MqSetCommand.Parser, BaseParser() {
    override fun parse(context: Context) = MqSetCommand.Model(
        queue = context.expression,
        messages = elements(context.el).mapNotNull { item ->
            parse(item, context.eval)?.let { TypedMessage(requireNotNull(it.content), it.headers, it.params) }
        }
    )
}
