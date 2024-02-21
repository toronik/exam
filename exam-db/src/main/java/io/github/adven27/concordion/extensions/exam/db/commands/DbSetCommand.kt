package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableSeed
import io.github.adven27.concordion.extensions.exam.db.commands.DbSetCommand.Model
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder

open class DbSetCommand(
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    private val parser: Parser = DbSetTableParser(),
    private val renderer: Renderer = Renderer.Base(valuePrinter)
) : DbCommand<Model, Model>(dbTester) {

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Model) = renderer.render(commandCall, result)
    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = model.apply {
        dbTester.seed(model.seed, eval)
    }.copy(seed = dbTester.metaData(model.seed))

    data class Model(val seed: TableSeed, val caption: String?)

    interface Parser {
        fun parse(context: Context): Model
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Model)

        class Base(private val printer: DbPlugin.ValuePrinter) : Renderer {
            override fun render(commandCall: CommandCall, result: Model) = with(commandCall.html().el) {
                addAttribute("hidden", "true")
                appendSister(renderTable(result.seed.table, printer, result.caption).el)
            }
        }
    }
}
