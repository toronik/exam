package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.Companion.detectAndParse
import org.awaitility.Awaitility
import org.awaitility.core.ConditionFactory
import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.ExecuteEvent
import org.concordion.api.listener.ExecuteListener
import org.concordion.internal.FailFastException
import java.time.Duration

abstract class ExamCommand<M, R>(
    private val attrs: Set<String> = setOf(),
    private val listener: ExecuteListener = ExecuteListener {}
) : AbstractCommand() {

    override fun execute(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        runCatching {
            commandCall.children.processSequentially(evaluator, resultRecorder, fixture)
            val el = commandCall.html()
            val result = process(
                model = model(
                    Context(
                        attrs = attrs.associateWith {
                            el.getAttr(it, evaluator)
                                ?: evaluator.resolveToObj(commandCall.getParameter(it.removePrefix("e:")))?.toString()
                        },
                        el = el,
                        expression = commandCall.expression.trim(),
                        eval = evaluator,
                        awaitConfig = AwaitConfig.build(commandCall)
                    )
                ),
                eval = evaluator,
                recorder = resultRecorder
            )
            if (el["rendered"] == null) {
                render(commandCall = commandCall, result = result)
                el.attr("rendered", "true")
            }
            listener.executeCompleted(ExecuteEvent(commandCall.element))
        }.onFailure {
            if (inBeforeExample(commandCall)) {
                throw FailFastException("Failed before example", it)
            } else throw it
        }
    }

    private fun inBeforeExample(commandCall: CommandCall) = commandCall.parent.getParameter("example") == "before"

    data class Context(
        val attrs: Map<String, String?>,
        val el: Html,
        val expression: String,
        val eval: Evaluator,
        val awaitConfig: AwaitConfig?
    ) {
        operator fun get(name: String) = attrs[name]
    }

    abstract fun model(context: Context): M
    abstract fun process(model: M, eval: Evaluator, recorder: ResultRecorder): R
    abstract fun render(commandCall: CommandCall, result: R)
}

open class SimpleCommand : ExamCommand<Unit, Unit>() {
    override fun model(context: Context) = Unit
    override fun process(model: Unit, eval: Evaluator, recorder: ResultRecorder) = Unit
    override fun render(commandCall: CommandCall, result: Unit) = Unit
}

data class AwaitConfig(
    val atMost: Duration = DEFAULT_AT_MOST,
    val pollDelay: Duration = DEFAULT_POLL_DELAY,
    val pollInterval: Duration = DEFAULT_POLL_INTERVAL
) {
    fun timeoutMessage(e: Throwable?) = "Check didn't complete within ${atMost.toMillis()}ms " +
        "(poll delay ${pollDelay.toMillis()}ms, interval ${pollInterval.toMillis()}ms) " +
        "because:\n ${e?.rootCauseMessage() ?: ""}"

    fun await(desc: String? = null): ConditionFactory = Awaitility.await(desc)
        .atMost(atMost)
        .pollDelay(pollDelay)
        .pollInterval(pollInterval)

    companion object {
        var DEFAULT_AT_MOST: Duration = Duration.ofSeconds(4)
        var DEFAULT_POLL_DELAY: Duration = Duration.ofMillis(0)
        var DEFAULT_POLL_INTERVAL: Duration = Duration.ofMillis(1000)

        const val AWAIT = "await"

        fun build(command: CommandCall) = command.getParameter(AWAIT)
            ?.let(::parseDurations)
            ?.let {
                AwaitConfig(
                    atMost = it.getOrNull(0) ?: DEFAULT_AT_MOST,
                    pollDelay = it.getOrNull(1) ?: DEFAULT_POLL_DELAY,
                    pollInterval = it.getOrNull(2) ?: DEFAULT_POLL_INTERVAL
                )
            }

        private fun parseDurations(s: String) = s.split(",")
            .map { d -> d.trim().takeIf { it.isNotBlank() }?.let { detectAndParse(it.trim()) as Duration } }
    }
}
