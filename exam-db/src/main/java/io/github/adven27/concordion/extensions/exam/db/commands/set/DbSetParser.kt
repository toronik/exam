package io.github.adven27.concordion.extensions.exam.db.commands.set

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.DatasetSeed
import io.github.adven27.concordion.extensions.exam.db.builder.ExamDataSet
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy.CLEAN_INSERT
import io.github.adven27.concordion.extensions.exam.db.commands.BaseParser
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.OPERATION

open class DbSetParser(val dbTester: DbTester) : DbSetCommand.Parser, BaseParser() {

    override fun parse(context: Context) = DatasetSeed(
        context[DbCommand.DS],
        when {
            isSource(context) -> buildDataSetFromSource(dbTester, context.el, context.eval)
            isBlock(context) -> buildDataSetFromBlock(dbTester, context.el, context.eval)
            isTable(context) -> ExamDataSet(table(parseTableName(context.el), context.el, context.eval), context.eval)
            else -> throw UnsupportedOperationException("Unsupported markup ${context.el}")
        },
        seedStrategy(context)
    )

    private fun seedStrategy(context: Context) =
        context[OPERATION]?.let { SeedStrategy.valueOf(it.uppercase()) } ?: CLEAN_INSERT
}
