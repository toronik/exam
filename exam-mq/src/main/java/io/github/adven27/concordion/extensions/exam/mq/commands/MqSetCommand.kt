package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.MqTester.TypedMessage
import mu.KLogging
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder

open class MqSetCommand(
    testers: Map<String, MqTester>,
    private val parser: Parser = MqSetParser(),
    private val renderer: Renderer = Renderer.Empty()
) : MqCommand<MqSetCommand.Model, Result<MqSetCommand.Model>>(testers) {
    companion object : KLogging()

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Result<Model>) = renderer.render(commandCall, result)

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = runCatching {
        model.apply {
            testers.getOrFail(queue).let { t -> messages.takeIf { it.isNotEmpty() }?.forEach(t::send) ?: t.purge() }
        }
    }

    data class Model(val queue: String, val messages: List<TypedMessage> = listOf())

    interface Parser {
        fun parse(context: Context): Model
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Result<Model>)

        open class Empty : Renderer {
            override fun render(commandCall: CommandCall, result: Result<Model>) = Unit
        }
    }
}
