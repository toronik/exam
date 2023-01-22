package io.github.adven27.concordion.extensions.exam.nosql.commands.clean

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder

class CleanCommand(name: String, tag: String, private val dbTester: NoSqlDBTester) : ExamCommand(name, tag) {

    override fun setUp(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        val el = cmd.html()
        val collections = el.takeAwayAttr("collections", eval)?.also { cmd.swapText(it) } ?: eval.evaluate(cmd.expression).toString()
        dbTester.clean(collections.split(",").map { it.trim() })
    }
}
