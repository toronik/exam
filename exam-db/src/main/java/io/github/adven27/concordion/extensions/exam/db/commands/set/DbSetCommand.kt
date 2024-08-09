package io.github.adven27.concordion.extensions.exam.db.commands.set

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.DatasetSeed
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand
import io.github.adven27.concordion.extensions.exam.db.commands.get
import io.github.adven27.concordion.extensions.exam.db.commands.renderTable
import io.github.adven27.concordion.extensions.exam.db.commands.tableName
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder
import org.dbunit.dataset.CompositeDataSet
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable
import org.dbunit.dataset.ITableIterator

open class DbSetCommand(
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    private val parser: Parser = DbSetParser(dbTester),
    private val renderer: Renderer = Renderer.Base(valuePrinter)
) : DbCommand<DatasetSeed, IDataSet>(dbTester) {

    override fun model(context: Context) = parser.parse(context)
    override fun process(model: DatasetSeed, eval: Evaluator, recorder: ResultRecorder) =
        dbTester.seed(model).iterator().let { updateMetadata(it, model.ds) }

    private fun updateMetadata(i: ITableIterator, ds: String?): CompositeDataSet {
        val result: MutableList<ITable> = mutableListOf()
        while (i.next()) result.add(dbTester.updateMetaData(ds, i.table))
        return CompositeDataSet(result.toTypedArray())
    }

    override fun render(commandCall: CommandCall, result: IDataSet) = renderer.render(commandCall, result)

    interface Parser {
        fun parse(context: Context): DatasetSeed
    }

    interface Renderer {
        fun render(commandCall: CommandCall, result: IDataSet)

        class Base(private val printer: DbPlugin.ValuePrinter) : Renderer {

            override fun render(commandCall: CommandCall, result: IDataSet) {
                commandCall.html()
                    .let { if (it.localName() == "table") it else it.parent() }
                    .let {
                        it.el.addAttribute("hidden", "true")
                        result.reverseIterator().apply { while (next()) render(it) }
                    }
            }

            private fun ITableIterator.render(root: Html) {
                root.below(
                    table.let {
                        renderTable(
                            t = it,
                            cell = { td, row, col -> td()(Html(printer.wrap(it.tableName(), col, it[row, col]))) }
                        )
                    }
                )
            }
        }
    }
}
