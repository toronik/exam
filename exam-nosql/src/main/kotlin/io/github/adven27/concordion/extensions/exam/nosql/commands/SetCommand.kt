package io.github.adven27.concordion.extensions.exam.nosql.commands

import io.github.adven27.concordion.extensions.exam.core.commands.*
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
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