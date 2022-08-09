package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.ActualProvider
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.BeforeParseExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.commands.ExamAssertCommand
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.VerifyListener
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected

class CheckCommand(
    override val name: String = "nosql-check",
    private val dbTester: NoSqlDBTester,
    verifier: NoSqlVerifier = NoSqlVerifier(),
    actualProvider: ActualProvider<Expected, Pair<Boolean, Actual>> = NoSqlActualProvider(dbTester),
    commandParser: CommandParser<Expected> = CheckParser(),
    resultRenderer: VerifyListener<Expected, Actual> = HtmlCheckRenderer()
) : ExamAssertCommand<Expected, Actual>(commandParser, verifier, actualProvider, resultRenderer),
    NamedExamCommand,
    BeforeParseExamCommand {
    override val tag: String = "div"

    data class Actual(val documents: List<NoSqlDocument> = listOf())
    data class Expected(
        val collection: String,
        val documents: List<NoSqlDocument> = listOf(),
        val await: AwaitConfig?
    ) {
        override fun toString() = "Expected '$collection' has messages $documents. Await $await"
    }
}
