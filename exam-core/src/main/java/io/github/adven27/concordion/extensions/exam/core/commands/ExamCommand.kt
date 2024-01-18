package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.vars
import org.awaitility.Awaitility
import org.awaitility.core.ConditionFactory
import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.ExecuteEvent
import org.concordion.api.listener.ExecuteListener
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

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
    }

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
    val atMostSec: Long = DEFAULT_AT_MOST_SEC,
    val pollDelay: Long = DEFAULT_POLL_DELAY,
    val pollInterval: Long = DEFAULT_POLL_INTERVAL
) {
    fun timeoutMessage(e: Throwable?) = "Check didn't complete within $atMostSec seconds " +
        "(poll delay $pollDelay ms, interval $pollInterval ms) because :\n ${e?.rootCauseMessage() ?: ""}"

    fun await(desc: String? = null): ConditionFactory = Awaitility.await(desc)
        .atMost(atMostSec, SECONDS)
        .pollDelay(pollDelay, MILLISECONDS)
        .pollInterval(pollInterval, MILLISECONDS)

    companion object {
        var DEFAULT_AT_MOST_SEC = 4L
        var DEFAULT_POLL_DELAY = 0L
        var DEFAULT_POLL_INTERVAL = 1000L

        const val AWAIT_AT_MOST_SEC_SPINAL = "await-at-most-sec"
        const val AWAIT_POLL_DELAY_MILLIS_SPINAL = "await-poll-delay-millis"
        const val AWAIT_POLL_INTERVAL_MILLIS_SPINAL = "await-poll-interval-millis"
        const val AWAIT_AT_MOST_SEC_CAMEL = "awaitAtMostSec"
        const val AWAIT_POLL_DELAY_MILLIS_CAMEL = "awaitPollDelayMillis"
        const val AWAIT_POLL_INTERVAL_MILLIS_CAMEL = "awaitPollIntervalMillis"

        fun build(command: CommandCall) = build(
            command.getParameter(AWAIT_AT_MOST_SEC_CAMEL, AWAIT_AT_MOST_SEC_SPINAL)?.toLong(),
            command.getParameter(AWAIT_POLL_DELAY_MILLIS_CAMEL, AWAIT_POLL_DELAY_MILLIS_SPINAL)?.toLong(),
            command.getParameter(AWAIT_POLL_INTERVAL_MILLIS_CAMEL, AWAIT_POLL_INTERVAL_MILLIS_SPINAL)?.toLong()
        )

        fun build(atMostSec: Long?, pollDelay: Long?, pollInterval: Long?): AwaitConfig? =
            if (enabled(atMostSec, pollDelay, pollInterval)) {
                AwaitConfig(
                    atMostSec ?: DEFAULT_AT_MOST_SEC,
                    pollDelay ?: DEFAULT_POLL_DELAY,
                    pollInterval ?: DEFAULT_POLL_INTERVAL
                )
            } else {
                null
            }

        private fun enabled(atMostSec: Long?, pollDelay: Long?, pollInterval: Long?) =
            !(atMostSec == null && pollDelay == null && pollInterval == null)
    }
}

class VarsAttrs(root: Html, evaluator: Evaluator) {
    val vars: String? = root.getAttr(VARS)
    val varsSeparator: String = root.getAttr(VARS_SEPARATOR, ",")

    init {
        setVarsToContext(evaluator)
    }

    private fun setVarsToContext(evaluator: Evaluator) {
        vars.vars(evaluator, true, varsSeparator)
    }

    companion object {
        private const val VARS = "vars"
        private const val VARS_SEPARATOR = "varsSeparator"
    }
}
