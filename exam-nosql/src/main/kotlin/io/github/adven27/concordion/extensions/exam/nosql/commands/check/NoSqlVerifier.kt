package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.AwaitVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected
import org.concordion.api.Evaluator

class NoSqlVerifier : AwaitVerifier<Expected, Actual> {
    override fun verify(
        eval: Evaluator,
        expected: Expected,
        actual: Actual
    ): Result<Verifier.Success<Expected, Actual>> {
        TODO("Not yet implemented")
    }

    override fun verify(
        eval: Evaluator,
        expected: Expected,
        getActual: () -> Pair<Boolean, Actual>
    ): Result<Verifier.Success<Expected, Actual>> {
        TODO("Not yet implemented")
    }
}
