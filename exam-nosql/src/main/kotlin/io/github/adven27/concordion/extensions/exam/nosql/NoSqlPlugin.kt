package io.github.adven27.concordion.extensions.exam.nosql

import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.clean.CleanCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.set.SetCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.show.ShowCommand

class NoSqlPlugin constructor(
    private val dbTesters: Map<String, NoSqlDBTester>
) : ExamPlugin {

    constructor(dbTester: NoSqlDBTester) : this(
        mapOf(NoSqlDBTester.DEFAULT_DATASOURCE to dbTester)
    )

    override fun commands(): List<NamedExamCommand> =
        listOf(
            SetCommand("nosql-set", dbTesters),
            CheckCommand("nosql-check", dbTesters),
            CleanCommand("nosql-clean", "pre", dbTesters),
            ShowCommand("nosql-show", dbTesters)
        )

    @Suppress("EmptyFunctionBlock")
    override fun setUp() {
    }

    @Suppress("EmptyFunctionBlock")
    override fun tearDown() {
    }
}
