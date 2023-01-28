package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.ActualProvider
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected

class NoSqlActualProvider(
    private val dbTesters: Map<String, NoSqlDBTester>
) : ActualProvider<Expected, Pair<Boolean, Actual>> {

    override fun provide(source: Expected): Pair<Boolean, Actual> =
        dbTesters[source.dsName]?.let { false to Actual(it.read(source.collection)) }
            ?: throw IllegalArgumentException("NoSqlDBTester with name ${source.dsName} is not registered")
}
