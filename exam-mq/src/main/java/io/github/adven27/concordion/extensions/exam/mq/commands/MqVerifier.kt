package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.ContentVerifier.ExpectedContent
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier.Check
import io.github.adven27.concordion.extensions.exam.core.commands.checkAndSet
import io.github.adven27.concordion.extensions.exam.core.resolveNoType
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.mq.MqTester.Message
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand.Expected
import io.github.adven27.concordion.extensions.exam.mq.commands.MqParser.TypedMessage
import mu.KLogging
import org.concordion.api.Evaluator
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
        // TODO идти по порядку как указаны экспектед брать верхнее и искать в актуал и тд (вверху лучше указывать конкретные внизу абстрактные экспектедв)
        expected.messages.sortedTyped(expected.exact)
            .zip(actual.messages.sorted(expected.exact)) { e, a -> VerifyPair(a, e) }
            .map {
                logger.debug("Verifying message:\n{}", it)
                val typeConfig = ExamExtension.contentTypeConfig(it.expected.content.type)
                MessageVerifyResult(
                    checkHeaders(it.actual.headers, it.expected.headers, eval),
                    typeConfig.let { (_, verifier, _) -> verifier.verify(it.expected.body, it.actual.body) }
                )
            }

    private fun toCheck(results: List<MessageVerifyResult>, expected: Expected, actual: Actual) = Check(
        expected = expected,
        actual = actual,
        fail = MessageVerifyingError(expected.queue, results)
            .takeIf { results.any { it.headers.isFailure || it.content.isFailure } }
    )

    @Suppress("SpreadOperator", "NestedBlockDepth")
    protected fun checkHeaders(actual: Map<String, String?>, expected: Map<String, String?>, eval: Evaluator) =
        if (expected.isEmpty()) {
            Result.success(emptyMap())
        } else {
            try {
                assertEquals("Different headers size", expected.size, actual.size)
                expected.toList().partition { actual[it.first] != null }.let { (matched, absentInActual) ->
                    (
                        matched.toMap().map { (it.key to it.value) to (it.key to actual[it.key]) } +
                            absentInActual.zip(absentInExpected(actual, matched.toMap()))
                        ).map { (expected, actual) -> headerCheckResult(expected, actual, eval) }
                        .let { results ->
                            if (results.any { it.actualValue != null || it.actualKey != null }) {
                                Result.failure(HeadersVerifyingError(results))
                            } else {
                                Result.success(results.associate { it.header })
                            }
                        }
                }
            } catch (e: AssertionError) {
                Result.failure(HeadersSizeVerifyingError(expected, actual, e.message!!, e))
            }
        }

    private fun headerCheckResult(expected: Pair<String, String?>, actual: Pair<String, String?>, eval: Evaluator) =
        if (expected.first == actual.first) {
            if (checkAndSet(eval, evalActual(eval, actual), evalExpected(expected, eval))) {
                HeaderCheckResult(expected)
            } else {
                HeaderCheckResult(expected, actualValue = actual.second)
            }
        } else {
            HeaderCheckResult(expected, actualKey = actual.first)
        }

    private fun evalExpected(expected: Pair<String, String?>, eval: Evaluator) =
        expected.second?.let { eval.resolveNoType(it) }

    private fun evalActual(eval: Evaluator, actual: Pair<String, String?>) = eval.resolveToObj(actual.second)

    data class HeaderCheckResult(
        val header: Pair<String, String?>,
        val actualKey: String? = null,
        val actualValue: String? = null
    )

    private fun absentInExpected(actual: Map<String, String?>, matched: Map<String, String?>) =
        actual.filterNot { matched.containsKey(it.key) }.toList()

    private fun List<Message>.sorted(exactMatch: Boolean) = if (!exactMatch) sortedBy { it.body } else this
    private fun List<TypedMessage>.sortedTyped(exactMatch: Boolean) =
        if (!exactMatch) sortedBy { it.body } else this

    @Suppress("TooGenericExceptionCaught")
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

    data class MessageVerifyResult(val headers: Result<Map<String, String?>>, val content: Result<ExpectedContent>)
    class MessageVerifyingError(val queue: String, val expected: List<MessageVerifyResult>) : java.lang.AssertionError()

    class SizeVerifyingError(
        val queue: String,
        val expected: List<TypedMessage>,
        val actual: List<Message>,
        message: String,
        exception: Throwable
    ) : java.lang.AssertionError(message, exception)

    class HeadersSizeVerifyingError(
        val expected: Map<String, String?>,
        val actual: Map<String, String?>,
        message: String,
        exception: Throwable
    ) : java.lang.AssertionError(message, exception)

    class HeadersVerifyingError(val result: List<HeaderCheckResult>) : java.lang.AssertionError()

    data class VerifyPair(val actual: Message, val expected: TypedMessage) {
        override fun toString() = "ACTUAL:\n\n$actual\n\nEXPECTED:\n\n$expected"
    }
}
