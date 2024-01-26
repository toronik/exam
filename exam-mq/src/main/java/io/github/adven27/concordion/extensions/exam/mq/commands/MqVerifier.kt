package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.contentVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier.Check
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.resolve
import io.github.adven27.concordion.extensions.exam.core.utils.MapContentMatchers.Companion.hasAllEntries
import io.github.adven27.concordion.extensions.exam.mq.MqTester.Message
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Expected
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckParser.ExpectedMessage
import mu.KLogging
import org.concordion.api.Evaluator
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals

@Suppress("TooManyFunctions")
open class MqVerifier : Verifier<Expected, Actual> {
    companion object : KLogging()

    override fun verify(eval: Evaluator, expected: Expected, actual: (Expected) -> Actual): Check<Expected, Actual> =
        awaitSize(expected, actual).fold(
            onSuccess = { toCheck(verify(expected, it, eval), expected, it) },
            onFailure = { Check(expected, Actual(partial = false, messages = (it as SizeVerifyingError).actual), it) }
        )

    protected fun verify(expected: Expected, actual: Actual, eval: Evaluator) =
        if (expected.exact) exactOrder(expected, actual, eval) else anyOrder(expected, actual, eval)

    protected fun anyOrder(expected: Expected, actual: Actual, eval: Evaluator): List<MessageVerifyResult> {
        val am = actual.messages.withIndex().asSequence()
        val found = mutableListOf<Int>()
        return expected.messages
            .map { it to am.not(found).find(it, eval)?.let { (i, r) -> r.also { found.add(i) } } }
            .partition { (_, r) -> r == null }
            .let { (notF, f) ->
                f.map { (_, r) -> r!! } +
                    notF.map { (e, _) -> e }
                        .zip(am.not(found).map { it.value }.toList())
                        .map { (e, a) -> VerifyPair(a, e) }
                        .map { verifyResult(it.actual, it.expected, eval) }
            }
    }

    private fun Sequence<IndexedValue<Message>>.not(found: List<Int>) = filter { it.index !in found }

    private fun Sequence<IndexedValue<Message>>.find(e: ExpectedMessage, eval: Evaluator) =
        map { (i, a) -> i to verifyResult(a, e, eval) }
            .firstOrNull { (_, r) -> r.headers.isSuccess && r.content.isSuccess }

    protected fun exactOrder(expected: Expected, actual: Actual, eval: Evaluator) =
        expected.messages.zip(actual.messages) { e, a -> VerifyPair(a, e) }.map {
            logger.debug("Verifying message:\n{}", it)
            verifyResult(it.actual, it.expected, eval)
        }

    private fun verifyResult(a: Message, e: ExpectedMessage, eval: Evaluator) = MessageVerifyResult(
        checkHeaders(act = a.headers, exp = e.headers, eval = eval),
        checkParams(act = a.params, exp = e.params, eval = eval),
        contentVerifier(e.verifier).verify(expected = e.body, actual = a.body, eval)
    )

    private fun toCheck(results: List<MessageVerifyResult>, expected: Expected, actual: Actual) = Check(
        expected = expected,
        actual = actual,
        fail = MessageVerifyingError(expected.queue, results)
            .takeIf { results.any { it.params.isFailure || it.headers.isFailure || it.content.isFailure } }
    )

    protected fun checkHeaders(act: Map<String, String?>, exp: Map<String, String?>, eval: Evaluator) =
        checkProps(act, exp, eval, "Headers mismatch")

    protected fun checkParams(act: Map<String, String?>, exp: Map<String, String?>, eval: Evaluator) =
        checkProps(act, exp, eval, "Params mismatch")

    private fun checkProps(act: Map<String, String?>, exp: Map<String, String?>, eval: Evaluator, message: String) =
        runCatching { verifyProps(message, act, exp.mapValues { (_, v) -> v?.let { eval.resolve(it) } }) }
            .map { exp }

    protected open fun verifyProps(message: String, actual: Map<String, String?>, expected: Map<String, String?>) =
        assertThat(message, actual, hasAllEntries(expected))

    protected fun awaitSize(expected: Expected, receive: (Expected) -> Actual): Result<Actual> {
        var prevActual = receive(expected).copy(partial = false)
        return runCatching {
            expected.await?.let {
                var currentActual: Actual? = null
                it.await("Await message queue ${expected.queue}").untilAsserted {
                    if (currentActual != null) {
                        currentActual = receive(expected).let { (partial, received) ->
                            Actual(
                                partial = false,
                                messages = if (partial) currentActual!!.messages + received else received
                            )
                        }
                        prevActual = currentActual!!
                    }
                    currentActual = prevActual
                    assertEquals(expected.messages.size, currentActual!!.messages.size)
                }
            } ?: assertEquals(expected.messages.size, prevActual.messages.size)
            prevActual
        }.recoverCatching {
            assertEquals(expected.messages.size, prevActual.messages.size)
            prevActual
        }.recoverCatching {
            throw SizeVerifyingError(
                queue = expected.queue,
                expected = expected.messages,
                actual = prevActual.messages,
                message = expected.await?.timeoutMessage(it) ?: it.rootCauseMessage(),
                exception = it
            )
        }
    }

    data class MessageVerifyResult(
        val headers: Result<Map<String, String?>>,
        val params: Result<Map<String, String?>>,
        val content: Result<Content>
    )

    class MessageVerifyingError(val queue: String, val expected: List<MessageVerifyResult>) : java.lang.AssertionError()

    class SizeVerifyingError(
        val queue: String,
        val expected: List<ExpectedMessage>,
        val actual: List<Message>,
        message: String,
        exception: Throwable
    ) : java.lang.AssertionError(message, exception)

    data class VerifyPair(val actual: Message, val expected: ExpectedMessage) {
        override fun toString() = "ACTUAL:\n\n$actual\n\nEXPECTED:\n\n$expected"
    }
}
