package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.Verifier.Check
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetVerifier.Failed
import io.github.adven27.concordion.extensions.exam.db.DbTester.Expectation
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand
import io.github.adven27.concordion.extensions.exam.db.commands.check.DbCheckCommand.Model
import io.github.adven27.concordion.extensions.exam.db.commands.check.DbCheckCommand.Result
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable

open class DbCheckCommand(
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    private val parser: Parser = DbCheckParser(dbTester),
    private val renderer: Renderer = BaseResultRenderer(valuePrinter)
) : DbCommand<Model, Result>(dbTester) {

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Result) =
        renderer.render(commandCall, result)

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = Result(
        caption = model.caption,
        check = dbTester.test(model.expectation, eval).apply(recordResults(recorder))
    )

    private fun recordResults(recorder: ResultRecorder): Check<out Expectation, IDataSet>.() -> Unit = {
        val tables = expected.dataset.tableNames.toList()
        when (fail) {
            null -> tables to listOf()
            is Failed -> tables.partition { it.lowercase() !in (fail as Failed).tables.map(String::lowercase) }
            else -> listOf<ITable>() to tables
        }.let { (passed, failed) ->
            passed.onEach { recorder.record(SUCCESS) }
            failed.onEach { recorder.record(FAILURE) }
        }
    }

    data class Model(val caption: String?, val expectation: Expectation)
    data class Result(val caption: String?, val check: Check<out Expectation, IDataSet>)

    interface Parser {
        fun parse(context: Context): Model
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Result)
    }
}
