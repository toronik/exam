package io.github.adven27.concordion.extensions.exam.db.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand.Context
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbTester.DataSetExpectation
import io.github.adven27.concordion.extensions.exam.db.DbTester.TableExpectation
import io.github.adven27.concordion.extensions.exam.db.builder.CompareOperation
import io.github.adven27.concordion.extensions.exam.db.builder.CompareOperation.EQUALS
import io.github.adven27.concordion.extensions.exam.db.commands.BaseParser
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.DS
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.OPERATION
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.ORDER_BY
import io.github.adven27.concordion.extensions.exam.db.commands.DbCommand.Companion.WHERE

class DbCheckParser(val dbTester: DbTester) : DbCheckCommand.Parser, BaseParser() {
    override fun parse(context: Context): DbCheckCommand.Model = DbCheckCommand.Model(
        caption = context.el.firstOrNull("caption")?.text(),
        expectation = when {
            isSource(context) -> DataSetExpectation(
                buildDataSetFromSource(dbTester, context.el, context.eval),
                ds = context[DS],
                await = context.awaitConfig,
                compareOperation = compareStrategy(context),
                orderBy = orderBy(context)
            )

            isBlock(context) -> DataSetExpectation(
                buildDataSetFromBlock(dbTester, context.el, context.eval),
                ds = context[DS],
                await = context.awaitConfig,
                compareOperation = compareStrategy(context),
                orderBy = orderBy(context)
            )

            isTable(context) -> TableExpectation(
                ds = context[DS],
                table = table(parseTableName(context.el), context.el, context.eval),
                where = context[WHERE] ?: "",
                orderBy = orderBy(context),
                compareOperation = compareStrategy(context),
                await = context.awaitConfig
            )

            else -> throw UnsupportedOperationException("Unsupported markup ${context.el}")
        }
    )

    private fun compareStrategy(context: Context) =
        context[OPERATION]?.let { CompareOperation.valueOf(it.uppercase()) } ?: EQUALS

    private fun orderBy(context: Context) =
        context[ORDER_BY]?.split(",")?.map { it.trim() }?.toSet() ?: setOf()
}
