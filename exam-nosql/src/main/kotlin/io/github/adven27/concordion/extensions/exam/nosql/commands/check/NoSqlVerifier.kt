package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.ContentVerifier.ExpectedContent
import io.github.adven27.concordion.extensions.exam.core.JsonVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected
import mu.KLogging
import org.concordion.api.Evaluator
import org.junit.Assert.assertEquals

class NoSqlVerifier : AwaitVerifier<Expected, Actual> {

    companion object : KLogging()

    override fun verify(eval: Evaluator, expected: Expected, actual: Actual) =
        verify(eval, expected) { false to actual }

    @Suppress("NestedBlockDepth")
    override fun verify(
        eval: Evaluator,
        expected: Expected,
        getActual: () -> Pair<Boolean, Actual>
    ): Result<Verifier.Success<Expected, Actual>> {
        try {
            return awaitSize(expected, getActual).let { actual ->
                expected.documents.sortedBy { it.body }
                    .zip(actual.documents.sortedBy { it.body }) { e, a -> Pair(e, a) }
                    .map {
                        logger.info("Verifying {}", it)
                        JsonVerifier().verify(it.first.body, it.second.body)
                    }.let { results ->
                        if (results.any { it.isFailure }) {
                            Result.failure(DocumentVerifyingError(results))
                        } else {
                            Result.success(Verifier.Success(expected, actual))
                        }
                    }
            }
        } catch (e: java.lang.AssertionError) {
            return Result.failure(e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun awaitSize(expected: Expected, receive: () -> Pair<Boolean, Actual>): Actual {
        var prevActual = receive().let { (_, actual) -> actual }
        try {
            expected.await?.let {
                var currentActual: Actual? = null
                it
                    .await("Await documents in collection ${expected.collection}")
                    .untilAsserted {
                        if (currentActual != null) {
                            currentActual = receive().let { (_, actual) -> actual }
                            prevActual = currentActual!!
                        }
                        currentActual = prevActual
                        assertEquals(expected.documents.size, currentActual!!.documents.size)
                    }
            } ?: assertEquals(expected.documents.size, prevActual.documents.size)
            return prevActual
        } catch (ignore: Throwable) {
            throw SizeVerifyingError(
                expected.documents,
                prevActual.documents,
                expected.await?.timeoutMessage(ignore) ?: ignore.rootCauseMessage(),
                ignore
            )
        }
    }

    class DocumentVerifyingError(val expected: List<Result<ExpectedContent>>) : java.lang.AssertionError()
    class SizeVerifyingError(
        val expected: List<NoSqlDocument>,
        val actual: List<NoSqlDocument>,
        message: String,
        exception: Throwable
    ) : java.lang.AssertionError(message, exception)
}
