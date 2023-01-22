package io.github.adven27.concordion.extensions.exam.nosql.commands.show

import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.html.html
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator

class ShowParser : CommandParser<ShowCommand.Attrs> {

    override fun parse(command: CommandCall, evaluator: Evaluator) =
        ShowCommand.Attrs(collectionFrom(command, evaluator))

    private fun collectionFrom(command: CommandCall, eval: Evaluator) =
        command.html().takeAwayAttr("collection", eval)
            ?: throw IllegalArgumentException("collection attribute is missing in set command")
}
