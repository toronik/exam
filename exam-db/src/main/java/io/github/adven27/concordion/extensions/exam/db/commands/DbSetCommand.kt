package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableSeed
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy.CLEAN_INSERT
import io.github.adven27.concordion.extensions.exam.db.commands.DbSetCommand.Model
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder
import org.dbunit.dataset.ITable

open class DbSetCommand(
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    private val parser: Parser = Parser.Suitable(),
    private val renderer: Renderer = Renderer.Suitable(valuePrinter)
) : DbCommand<Model, Model>(dbTester) {

    override fun model(context: Context) = parser.parse(context)
    override fun render(commandCall: CommandCall, result: Model) = renderer.render(commandCall, result)
    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = model.apply {
        dbTester.seed(model.seed, eval)
    }

    data class Model(val seed: TableSeed, val caption: String?)

    interface Parser {
        fun parse(context: Context): Model

        abstract class Base : Parser {
            abstract fun table(context: Context): ITable

            override fun parse(context: Context) = Model(
                seed = TableSeed(
                    ds = context[DS],
                    table = table(context),
                    strategy = context[OPERATION]?.let { SeedStrategy.valueOf(it.uppercase()) } ?: CLEAN_INSERT
                ),
                caption = context[CAPTION] ?: context.el.text().ifBlank { null }
            )
        }

        open class Suitable : Parser {
            override fun parse(context: Context) = suitableParser(context).parse(context)

            private fun suitableParser(context: Context) =
                if (context.el.localName() == "div") HtmlTableParser() else MdTableParser()
        }
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: Model)

        open class Suitable(private val valuePrinter: DbPlugin.ValuePrinter) : Renderer {
            override fun render(commandCall: CommandCall, result: Model) =
                suitableRenderer(commandCall).render(commandCall, result)

            private fun suitableRenderer(commandCall: CommandCall) =
                if (commandCall.element.localName == "div") Xhtml(valuePrinter) else Md(valuePrinter)
        }

        class Md(printer: DbPlugin.ValuePrinter) : BaseSetRenderer(printer) {
            override fun root(html: Html) = html.parent().parent()
        }

        class Xhtml(printer: DbPlugin.ValuePrinter) : BaseSetRenderer(printer) {
            override fun root(html: Html) = html
        }

        abstract class BaseSetRenderer(private val printer: DbPlugin.ValuePrinter) : Renderer {
            override fun render(commandCall: CommandCall, result: Model) = with(root(commandCall.html()).el) {
                addAttribute("hidden", "true")
                val rendered = getAttributeValue("rendered") != null
                if (!rendered) addAttribute("rendered", "true")
                appendSister(render(result, rendered))
            }

            private fun render(model: Model, rendered: Boolean) =
                renderTable(model.seed.table, printer, model.caption).el.apply {
                    if (rendered) addAttribute("hidden", "true")
                }

            abstract fun root(html: Html): Html
        }
    }
}
