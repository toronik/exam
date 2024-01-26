package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.FilesSeed
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy.CLEAN_INSERT
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITableIterator

open class DbExecuteCommand(
    dbTester: DbTester,
    private var valuePrinter: DbPlugin.ValuePrinter
) : DbCommand<FilesSeed, IDataSet>(dbTester) {

    override fun model(context: Context) = FilesSeed(
        ds = context.el.getAttr(DS),
        datasets = context.expression.split(",").map { context.el.getAttr("dir") + it.trim() },
        strategy = context[OPERATION]?.let { SeedStrategy.valueOf(it.uppercase()) } ?: CLEAN_INSERT
    )

    override fun process(model: FilesSeed, eval: Evaluator, recorder: ResultRecorder) = dbTester.seed(model, eval)
    override fun render(commandCall: CommandCall, result: IDataSet) {
        commandCall.html().let { result.iterator().apply { while (next()) render(it) } }
    }

    private fun ITableIterator.render(root: Html) {
        root(
            table.let {
                renderTable(it, { td, row, col -> td()(Html(valuePrinter.wrap(it.tableName(), col, it[row, col]))) })
            }
        )
    }
}
