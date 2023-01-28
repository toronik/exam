package io.github.adven27.concordion.extensions.exam.nosql.commands.set

import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.commands.HtmlNoSqlParser
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator

class SetParser(private val parser: HtmlNoSqlParser) : CommandParser<SetCommand.Operation> {

    override fun parse(command: CommandCall, evaluator: Evaluator) = SetCommand.Operation(
        dsName = dataSourceNameFrom(command, evaluator),
        collection = collectionFrom(command, evaluator),
        documents = parser.parse(command, evaluator),
        appendMode = appendFrom(command, evaluator)
    )

    private fun dataSourceNameFrom(command: CommandCall, eval: Evaluator) =
        command.html().takeAwayAttr("ds", eval) ?: NoSqlDBTester.DEFAULT_DATASOURCE

    private fun collectionFrom(command: CommandCall, eval: Evaluator) =
        command.html().takeAwayAttr("collection", eval)
            ?: throw IllegalArgumentException("collection attribute is missing in set command")

    private fun appendFrom(command: CommandCall, eval: Evaluator) =
        command.html().takeAwayAttr("append", eval).toBoolean()
}
