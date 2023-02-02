package io.github.adven27.concordion.extensions.exam.nosql.commands.set

import io.github.adven27.concordion.extensions.exam.core.commands.BeforeParseExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.commands.ExamSetUpCommand
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.SetUpListener
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import io.github.adven27.concordion.extensions.exam.nosql.commands.HtmlNoSqlParser
import org.concordion.api.Evaluator

class SetCommand(
    override val name: String = "nosql-set",
    private val dbTesters: Map<String, NoSqlDBTester>,
    commandParser: CommandParser<Operation> = SetParser(HtmlNoSqlParser()),
    listener: SetUpListener<Operation> = HtmlSetRenderer()
) : ExamSetUpCommand<SetCommand.Operation>(commandParser, listener), NamedExamCommand, BeforeParseExamCommand {

    override val tag = "div"

    override fun setUp(target: Operation, eval: Evaluator) {
        target.executeSet(
            dbTesters[target.dsName]
                ?: throw IllegalArgumentException("NoSqlDBTester with name ${target.dsName} is not registered")
        )
    }

    class Operation(
        val collection: String,
        val dsName: String,
        val documents: List<NoSqlDocument>,
        val appendMode: Boolean
    ) {
        fun executeSet(dbTester: NoSqlDBTester) {
            if (appendMode) {
                dbTester.setWithAppend(collection, documents)
            } else {
                dbTester.set(collection, documents)
            }
        }
    }
}
