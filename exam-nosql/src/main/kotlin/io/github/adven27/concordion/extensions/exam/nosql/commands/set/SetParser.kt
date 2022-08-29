package io.github.adven27.concordion.extensions.exam.nosql.commands.set

import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.nosql.commands.HtmlNoSqlParser
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator

class SetParser(private val parser: HtmlNoSqlParser) : CommandParser<SetCommand.Operation> {

    override fun parse(command: CommandCall, evaluator: Evaluator) = SetCommand.Operation(
        collection = collectionFrom(command, evaluator),
        documents = parser.parse(command, evaluator)
    )

    private fun collectionFrom(command: CommandCall, eval: Evaluator) = command.html().takeAwayAttr("collection", eval)
        ?: throw IllegalArgumentException("collection attribute is missing in set command")
}
