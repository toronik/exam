package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.Verifier.Check
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableExpectation
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand
import io.github.adven27.concordion.extensions.exam.db.commands.check.DbCheckCommand.Model
import io.github.adven27.concordion.extensions.exam.db.commands.check.DbCheckCommand.Result
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.dbunit.dataset.ITable

@Suppress("LongParameterList")
open class DbCheckCommand(
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    val parser: Parser = Parser.Suitable(),
    val renderer: Renderer = Renderer.Suitable(valuePrinter)
) : DbCommand<Model, Result>(dbTester) {
    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Result) =
        renderer.render(commandCall, result)

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = Result(
        model.caption,
        dbTester.test(model.expectation, eval).also { recorder.record(if (it.fail == null) SUCCESS else FAILURE) }
    )

    data class Model(val caption: String?, val expectation: TableExpectation)
    data class Result(val caption: String?, val check: Check<TableExpectation, ITable>)

    interface Parser {
        fun parse(context: Context): Model

        open class Suitable : Parser {
            override fun parse(context: Context) =
                (if (context.el.localName() == "div") XhtmlCheckParser() else MdCheckParser()).parse(context)
        }
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Result)

        class Md(printer: DbPlugin.ValuePrinter) : BaseResultRenderer(printer) {
            override fun root(html: Html) = html.parent().parent()
        }

        class Xhtml(printer: DbPlugin.ValuePrinter) : BaseResultRenderer(printer) {
            override fun root(html: Html) = html
        }

        open class Suitable(private val valuePrinter: DbPlugin.ValuePrinter) : Renderer {
            override fun render(commandCall: CommandCall, result: Result) =
                suitableRenderer(commandCall).render(commandCall, result)

            private fun suitableRenderer(commandCall: CommandCall) =
                if (commandCall.element.localName == "div") Xhtml(valuePrinter) else Md(valuePrinter)
        }
    }
}
