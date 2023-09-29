package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ContentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.ExamResultRenderer
import io.github.adven27.concordion.extensions.exam.core.TextContentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.content
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.core.resolve
import io.github.adven27.concordion.extensions.exam.core.resolveNoType
import io.github.adven27.concordion.extensions.exam.core.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.vars
import mu.KLogging
import nu.xom.Attribute
import org.awaitility.Awaitility
import org.awaitility.core.ConditionFactory
import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.ExecuteEvent
import org.concordion.api.listener.ExecuteListener
import org.concordion.internal.CatchAllExpectationChecker.normalize
import org.concordion.internal.command.AssertEqualsCommand
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

interface BeforeParseExamCommand {
    val tag: String

    fun beforeParse(elem: nu.xom.Element) {
        val attr = Attribute(elem.localName, "")
        attr.setNamespace("e", ExamExtension.NS)
        elem.addAttribute(attr)

        elem.namespacePrefix = ""
        elem.namespaceURI = null
        elem.localName = tag
    }
}

abstract class ExamCommand<M, R> @JvmOverloads constructor(
    private val attrs: Set<String> = emptySet(),
    override val tag: String = "div"
) : BeforeParseExamCommand, AbstractCommand() {
    private val listener = ExecuteListener {}

    override fun execute(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        commandCall.children.processSequentially(evaluator, resultRecorder, fixture)
        val el = commandCall.html()
        render(
            commandCall = commandCall,
            result = process(
                model = model(
                    Context(
                        attrs = attrs.associateWith { el.getAttr(it, evaluator) },
                        el = el,
                        expression = commandCall.expression,
                        eval = evaluator
                    )
                ),
                eval = evaluator,
                recorder = resultRecorder
            )
        )
        listener.executeCompleted(ExecuteEvent(commandCall.element))
    }

    data class Context(val attrs: Map<String, String?>, val el: Html, val expression: String, val eval: Evaluator) {
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

abstract class RenderCommand : SimpleCommand() {
    override fun model(context: Context) = Unit
    override fun process(model: Unit, eval: Evaluator, recorder: ResultRecorder) = Unit
    override fun render(commandCall: CommandCall, result: Unit) = render(commandCall)
    abstract fun render(commandCall: CommandCall)
}

open class ExamAssertEqualsCommand(
    val config: ContentTypeConfig = TextContentTypeConfig(),
    val content: (text: String) -> String = { it }
) : AssertEqualsCommand(
    Comparator { actual, expected ->
        config.verifier.verify(normalize(expected), normalize(actual)).fold({ 0 }, { -1 })
    }
) {
    init {
        addAssertEqualsListener(ExamResultRenderer())
    }

    override fun verify(command: CommandCall, evaluator: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        super.verify(command.apply { resolve(evaluator) }, evaluator, resultRecorder, fixture)
    }

    protected fun CommandCall.resolve(eval: Evaluator) =
        swapText(config.resolver.resolve(content(normalize(element.text)), eval))

    private fun CommandCall.swapText(value: String) {
        element = container(Html(element), config.printer.style())
            .text(config.printer.print(value))
            .el
    }

    companion object : KLogging()
}

class MatchesCommand : AssertEqualsCommand(
    Comparator { act, exp ->
        try {
            if (checkAndSet(EVAL, act.toString(), exp.toString())) 0 else 1
        } catch (expected: Throwable) {
            logger.error("Matcher failure: actual = {}, expected = {}", act, exp, expected)
            -1
        }
    }
) {
    companion object : KLogging() {
        lateinit var EVAL: Evaluator
    }

    init {
        addAssertEqualsListener(ExamResultRenderer())
    }

    override fun verify(command: CommandCall, evaluator: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        EVAL = evaluator
        super.verify(command.apply { resolve(evaluator) }, evaluator, resultRecorder, fixture)
    }

    private fun CommandCall.resolve(eval: Evaluator) {
        Html(element.localName)(pre(eval.resolveNoType(normalize(element.text)))).el.also {
            element.appendSister(it)
            element.parentElement.removeChild(element)
            element = it
        }
    }
}

fun CommandCall?.awaitConfig(): AwaitConfig? = html().awaitConfig()

fun Html.awaitConfig(prefix: String = "await") = AwaitConfig.build(
    getAttr("${prefix}AtMostSec".decap())?.toLong(),
    getAttr("${prefix}PollDelayMillis".decap())?.toLong(),
    getAttr("${prefix}PollIntervalMillis".decap())?.toLong()
)

fun String.decap() = replaceFirstChar { it.lowercase() }

data class AwaitConfig(
    val atMostSec: Long = DEFAULT_AT_MOST_SEC,
    val pollDelay: Long = DEFAULT_POLL_DELAY,
    val pollInterval: Long = DEFAULT_POLL_INTERVAL
) {
    fun timeoutMessage(e: Throwable) = "Check didn't complete within $atMostSec seconds " +
        "(poll delay $pollDelay ms, interval $pollInterval ms) because ${e.rootCauseMessage()}"

    fun await(desc: String? = null): ConditionFactory = Awaitility.await(desc)
        .atMost(atMostSec, SECONDS)
        .pollDelay(pollDelay, MILLISECONDS)
        .pollInterval(pollInterval, MILLISECONDS)

    companion object {
        var DEFAULT_AT_MOST_SEC = 4L
        var DEFAULT_POLL_DELAY = 0L
        var DEFAULT_POLL_INTERVAL = 1000L

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

class ContentAttrs(root: Html, evaluator: Evaluator) {
    private val from: String? = root.attr("from")
    val content: Content = evaluator.resolve(
        Content(
            body = VarsAttrs(root, evaluator).let { root.content(from, evaluator) },
            type = root.attr("contentType") ?: from?.substringAfterLast(".", "json") ?: "json"
        )
    )
}
