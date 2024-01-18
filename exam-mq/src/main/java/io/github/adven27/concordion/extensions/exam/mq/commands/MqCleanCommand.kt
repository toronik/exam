package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCleanCommand.Model
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder

open class MqCleanCommand(testers: Map<String, MqTester>) : MqCommand<Model, Model>(testers) {
    override fun model(context: Context) =
        Model(context.eval.evaluate(context.expression).toString().split(",").map(String::trim).toSet())

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) =
        model.apply { names.forEach { testers.getOrFail(it).purge() } }

    override fun render(commandCall: CommandCall, result: Model) = Unit

    data class Model(val names: Set<String>)
}
