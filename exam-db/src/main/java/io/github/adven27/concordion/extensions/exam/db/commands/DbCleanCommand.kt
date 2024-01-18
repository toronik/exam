package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.DatasetSeed
import io.github.adven27.concordion.extensions.exam.db.builder.DataSetBuilder
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy.DELETE_ALL
import io.github.adven27.concordion.extensions.exam.db.commands.DbCleanCommand.Model
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder

open class DbCleanCommand(private val dbTester: DbTester) : ExamCommand<Model, Model>() {

    override fun model(context: Context) = Model(
        ds = context[DbCommand.DS],
        tables = context.eval.evaluate(context.expression).toString().split(",").map { it.trim() }
    )

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = model.apply {
        dbTester.seed(
            DatasetSeed(
                ds = ds,
                dataset = DataSetBuilder().apply { tables.map { newRowTo(it).add() } }.build(),
                strategy = DELETE_ALL
            )
        )
    }

    override fun render(commandCall: CommandCall, result: Model) =
        commandCall.element.swapText(result.tables.joinToString())

    data class Model(val ds: String?, val tables: List<String>)
}
