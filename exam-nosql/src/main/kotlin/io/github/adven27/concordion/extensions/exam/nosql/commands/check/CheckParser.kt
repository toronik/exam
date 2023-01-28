package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.commands.awaitConfig
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.commands.HtmlNoSqlParser
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator

class CheckParser(private val parser: HtmlNoSqlParser) : CommandParser<Expected> {

    override fun parse(command: CommandCall, evaluator: Evaluator) =
        Expected(
            dataSourceNameFrom(command, evaluator),
            collectionFrom(command, evaluator),
            parser.parse(command, evaluator),
            command.awaitConfig()
        )

    private fun collectionFrom(command: CommandCall, eval: Evaluator) =
        command.html().takeAwayAttr("collection", eval)
            ?: throw IllegalArgumentException("collection attribute is missing in set command")

    private fun dataSourceNameFrom(command: CommandCall, eval: Evaluator) =
        command.html().takeAwayAttr("ds", eval) ?: NoSqlDBTester.DEFAULT_DATASOURCE
}
