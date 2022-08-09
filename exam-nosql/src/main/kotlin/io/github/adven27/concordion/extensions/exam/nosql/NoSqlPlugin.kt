package io.github.adven27.concordion.extensions.exam.nosql

import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.core.commands.NamedExamCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand
import io.github.adven27.concordion.extensions.exam.nosql.commands.set.SetCommand

class NoSqlPlugin @JvmOverloads constructor(
    private val dbTester: NoSqlDBTester
) : ExamPlugin {

    override fun commands(): List<NamedExamCommand> =
        listOf(
            SetCommand("nosql-set", dbTester),
            CheckCommand("nosql-check", dbTester)
        )

    @Suppress("EmptyFunctionBlock")
    override fun setUp() {
    }

    @Suppress("EmptyFunctionBlock")
    override fun tearDown() {
    }
}
