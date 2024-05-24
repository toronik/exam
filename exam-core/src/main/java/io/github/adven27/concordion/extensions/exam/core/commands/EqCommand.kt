package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier.Fail
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.contentVerifier
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.errorMessage
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.resolve
import mu.KLogging
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.concordion.internal.command.AssertEqualsCommand
import org.concordion.internal.util.Check

open class EqCommand(val verifier: ContentVerifier) : AssertEqualsCommand() {
    companion object : KLogging() {
        fun objectToString(any: Any?): String = any?.toString() ?: "(null)"
    }

    override fun verify(command: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        Check.isFalse(command.hasChildCommands(), "Nesting commands inside an 'assertEquals' is not supported")
        val verifier = verifier(command)
        val await = AwaitConfig.build(command)
        val html = command.html()
        val expected = eval.resolve(html.deepestChild().text())
        var lastFailed: Result<Content>? = null
        val result = await?.let { c ->
            runCatching {
                c.await("Await equals").until(
                    { verifier.verify(expected, actual(eval, command), eval) },
                    { r -> r.isSuccess.also { if (!it || lastFailed == null) lastFailed = r } }
                )
            }.getOrElse { lastFailed!! }
        } ?: verifier.verify(expected, actual(eval, command), eval)
        result
            .onSuccess {
                resultRecorder.record(SUCCESS)
                html.css("success").swapText(it.body)
            }.onFailure { error ->
                resultRecorder.record(FAILURE)
                when (error) {
                    is Fail -> if (html.parent().localName() == "pre") {
                        html.parent().below(renderError(error, await)).remove()
                    } else {
                        html.removeChildren()(renderError(error, await))
                    }

                    else -> throw error
                }
            }
    }

    protected fun actual(evaluator: Evaluator, command: CommandCall): String =
        objectToString(evaluator.evaluate(command.expression))

    protected fun renderError(error: Fail, await: AwaitConfig?) = errorMessage(
        message = await?.timeoutMessage(error) ?: error.details,
        html = div("class" to "${error.type} failure")(
            Html("del", error.expected, "class" to "expected"),
            Html("ins", error.actual, "class" to "actual")
        )
    ).second

    private fun verifier(command: CommandCall) =
        command.getParameter("verifier")?.let { contentVerifier(it) } ?: verifier
}
