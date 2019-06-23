package com.adven.concordion.extensions.exam.db.commands

import com.adven.concordion.extensions.exam.core.commands.ExamCommand
import com.adven.concordion.extensions.exam.core.html.html
import com.adven.concordion.extensions.exam.db.DbTester
import com.adven.concordion.extensions.exam.db.builder.DataSetBuilder
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder
import org.dbunit.operation.DatabaseOperation

class DBCleanCommand(name: String, tag: String, private val dbTester: DbTester) : ExamCommand(name, tag) {

    override fun setUp(cmd: CommandCall?, eval: Evaluator?, resultRecorder: ResultRecorder?) {
        val el = cmd.html()
        val ds = el.takeAwayAttr("ds", DbTester.DEFAULT_DATASOURCE)
        val builder = DataSetBuilder()
        val tables = el.takeAwayAttr("tables", eval)!!
        tables.split(",").map { builder.newRowTo(it.trim()).add() }
        dbTester.executors[ds]!!.apply {
            setUpOperation = DatabaseOperation.DELETE_ALL
            dataSet = builder.build()
            onSetup()
        }
        el.text(tables)
    }
}