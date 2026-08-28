package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.logger.CommandLog
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.DurationStyle.Companion.detectAndParse
import org.awaitility.Awaitility
import org.awaitility.core.ConditionFactory
import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.api.ResultSummary
import org.concordion.api.listener.ExecuteEvent
import org.concordion.api.listener.ExecuteListener
import org.concordion.internal.FailFastException
import java.time.Duration

abstract class ExamCommand<M, R>(
    private val attrs: Set<String> = setOf(),
    private val listener: ExecuteListener = ExecuteListener {}
) : AbstractCommand() {

    /**
     * The name this command is registered under, set once by `ExamExtension` while wiring the
     * registry. A command has no other way to know what a spec called it, and the log is only
     * useful if it prints the name the author wrote.
     */
    var registeredName: String = ""

    override fun execute(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        val started = System.nanoTime()
        val failuresBefore = (resultRecorder as? ResultSummary)?.let { it.failureCount + it.exceptionCount } ?: 0
        CommandLog.startCommand()
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
        }.onSuccess {
            val recorded = (resultRecorder as? ResultSummary)?.let { r -> r.failureCount + r.exceptionCount } ?: 0
            if (recorded > failuresBefore) {
                CommandLog.failed(registeredName, target(commandCall), elapsed(started), null)
            } else {
                CommandLog.succeeded(registeredName, target(commandCall), elapsed(started))
            }
        }.onFailure {
            CommandLog.failed(registeredName, target(commandCall), elapsed(started), it)
            if (inBeforeExample(commandCall)) throw FailFastException("Failed before example", it) else throw it
        }
    }

    private fun elapsed(startedNanos: Long) = Duration.ofNanos(System.nanoTime() - startedNanos)

    /**
     * What the command acted on, as the spec wrote it: the value of the attribute that named the
     * command (`e:db-check="product"`, `e:http="POST /orders"`), falling back to the expression for
     * commands whose subject is the expression itself (`e:eq="#dtJson"`).
     */
    private fun target(commandCall: CommandCall): String {
        val el = commandCall.element
        return commandCall.expression.trim().ifBlank {
            // A db command names its table in the table's caption, an http one writes its request
            // line in the body. Both are what the spec's author wrote and would recognise.
            el.getFirstChildElement("caption")?.text?.trim()
                ?: el.text.lines().map { it.trim() }.firstOrNull { l -> HTTP_METHODS.any(l::startsWith) }
                ?: ""
        }
    }

    private companion object {
        val HTTP_METHODS = listOf("GET ", "POST ", "PUT ", "DELETE ", "PATCH ")
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
        // Counted here so that one line can say a check took three attempts, instead of three
        // libraries each printing a stack trace about the two that had not settled yet.
        .conditionEvaluationListener(CommandLog.attemptsOfThisCommand().let { c -> { _: Any? -> c.incrementAndGet() } })

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
