package io.github.adven27.concordion.extensions.exam.nosql.commands.clean

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder

class CleanCommand(
    name: String,
    tag: String,
    private val dbTesters: Map<String, NoSqlDBTester>
) : ExamCommand(name, tag) {

    override fun setUp(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        val el = cmd.html()
        val collections =
            el.takeAwayAttr("collections", eval)?.also { cmd.element.swapText(it) } ?: eval.evaluate(cmd.expression).toString()
        val dsName = dataSourceNameFrom(el, eval)
        dbTesters[dsName]?.clean(collections.split(",").map { it.trim() })
            ?: throw IllegalArgumentException("NoSqlDBTester with name $dsName is not registered")
    }

    private fun dataSourceNameFrom(el: Html, eval: Evaluator) =
        el.takeAwayAttr("ds", eval) ?: NoSqlDBTester.DEFAULT_DATASOURCE
}
