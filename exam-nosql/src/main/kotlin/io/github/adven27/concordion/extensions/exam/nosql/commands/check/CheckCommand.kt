package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.ActualProvider
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.BeforeParseExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.commands.ExamAssertCommand
import io.github.adven27.concordion.extensions.exam.core.commands.VerifyListener
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import io.github.adven27.concordion.extensions.exam.nosql.commands.HtmlNoSqlParser
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected

class CheckCommand(
    private val dbTesters: Map<String, NoSqlDBTester>,
    verifier: NoSqlVerifier = NoSqlVerifier(),
    actualProvider: ActualProvider<Expected, Pair<Boolean, Actual>> = NoSqlActualProvider(dbTesters),
    commandParser: CommandParser<Expected> = CheckParser(HtmlNoSqlParser()),
    resultRenderer: VerifyListener<Expected, Actual> = HtmlCheckRenderer()
) : ExamAssertCommand<Expected, Actual>(commandParser, verifier, actualProvider, resultRenderer),
    NamedExamCommand,
    BeforeParseExamCommand {
    override val tag: String = "div"

    data class Actual(val documents: List<NoSqlDocument> = listOf())
    data class Expected(
        val dsName: String,
        val collection: String,
        val documents: List<NoSqlDocument> = listOf(),
        val await: AwaitConfig?
    ) {
        override fun toString() = "Expected '$collection' has messages $documents. Await $await"
    }
}
