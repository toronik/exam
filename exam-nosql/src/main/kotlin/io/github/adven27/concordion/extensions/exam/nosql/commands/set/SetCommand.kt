package io.github.adven27.concordion.extensions.exam.nosql.commands.set

import io.github.adven27.concordion.extensions.exam.core.commands.BeforeParseExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.commands.ExamSetUpCommand
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.SetUpListener
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import org.concordion.api.Evaluator

class SetCommand(
    override val name: String = "nosql-set",
    val dbTester: NoSqlDBTester,
    commandParser: CommandParser<Operation> = SetParser(),
    listener: SetUpListener<Operation> = HtmlSetRenderer()
) : ExamSetUpCommand<SetCommand.Operation>(commandParser, listener), NamedExamCommand, BeforeParseExamCommand {

    override val tag = "div"

    override fun setUp(target: Operation, eval: Evaluator) {
        target.executeSet(dbTester)
    }

    class Operation(
        val collection: String,
        val documents: List<NoSqlDocument>
    ) {
        fun executeSet(dbTester: NoSqlDBTester) {
            dbTester.set(collection, documents)
        }
    }
}
