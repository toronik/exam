package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator

class CheckParser : CommandParser<Expected> {

    override fun parse(command: CommandCall, evaluator: Evaluator): Expected {
        TODO("Not yet implemented")
    }
}
