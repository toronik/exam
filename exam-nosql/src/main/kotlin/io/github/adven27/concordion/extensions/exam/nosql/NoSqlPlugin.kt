package io.github.adven27.concordion.extensions.exam.nosql

import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.clean.CleanCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.set.SetCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.show.ShowCommand

class NoSqlPlugin constructor(private val dbTester: NoSqlDBTester) : ExamPlugin {

    override fun commands(): List<NamedExamCommand> =
        listOf(
            SetCommand("nosql-set", dbTester),
            CheckCommand("nosql-check", dbTester),
            CleanCommand("nosql-clean", "pre", dbTester),
            ShowCommand("nosql-show", dbTester)
        )

    @Suppress("EmptyFunctionBlock")
    override fun setUp() {
    }

    @Suppress("EmptyFunctionBlock")
    override fun tearDown() {
    }
}
