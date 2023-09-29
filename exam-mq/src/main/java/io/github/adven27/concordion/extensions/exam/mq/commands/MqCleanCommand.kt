package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCleanCommand.Model
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder

class MqCleanCommand(testers: Map<String, MqTester>) : MqCommand<Model, Model>(testers, setOf(NAMES)) {
    companion object {
        const val NAMES = "names"
    }

    override fun model(context: Context) = Model((context[NAMES] ?: context.expression).split(",").toSet())
    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) =
        model.apply { names.forEach { mqTesters[it]!!.purge() } }

    override fun render(commandCall: CommandCall, result: Model) =
        commandCall.element.swapText(result.names.joinToString())

    data class Model(val names: Set<String>)
}
