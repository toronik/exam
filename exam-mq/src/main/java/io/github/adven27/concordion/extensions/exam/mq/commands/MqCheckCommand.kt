package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Expected
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckParser.ExpectedMessage
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCommand.Companion.getOrFail
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder

class MqCheckActualProvider(private val mqTesters: Map<String, MqTester>) : (String) -> Actual {
    override fun invoke(queue: String) =
        mqTesters.getOrFail(queue).let { Actual(it.accumulateOnRetries(), it.receive()) }
}

open class MqCheckCommand(
    testers: Map<String, MqTester>,
    private val verifier: MqVerifier = MqVerifier(),
    private val actualProvider: (String) -> Actual = MqCheckActualProvider(testers),
    private val parser: Parser = MqCheckParser(),
    private val renderer: MqCheckRenderer = MqCheckRenderer.Base()
) : MqCommand<Expected, Verifier.Check<Expected, Actual>>(testers, setOf(CONTAINS)) {
    companion object {
        const val CONTAINS = "contains"
    }

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Verifier.Check<Expected, Actual>) =
        renderer.render(commandCall, result)

    override fun process(model: Expected, eval: Evaluator, recorder: ResultRecorder) =
        verifier.verify(eval, model) { actualProvider(model.queue) }.also {
            recorder.record(if (it.fail == null) SUCCESS else FAILURE)
        }

    data class Expected(
        val queue: String,
        val messages: List<ExpectedMessage> = listOf(),
        val exact: Boolean = true,
        val await: AwaitConfig?
    ) {
        override fun toString() =
            "Expected '$queue' has messages $messages in ${if (exact) "exact" else "any"} order. Await $await"
    }

    data class Actual(val partial: Boolean, val messages: List<MqTester.Message> = listOf())

    interface Parser {
        fun parse(context: Context): Expected
    }
}
