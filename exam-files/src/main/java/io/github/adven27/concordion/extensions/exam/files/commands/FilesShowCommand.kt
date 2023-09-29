package io.github.adven27.concordion.extensions.exam.files.commands

import io.github.adven27.concordion.extensions.exam.core.html.italic
import io.github.adven27.concordion.extensions.exam.core.html.table
import io.github.adven27.concordion.extensions.exam.core.html.th
import io.github.adven27.concordion.extensions.exam.core.html.thead
import io.github.adven27.concordion.extensions.exam.files.FileTester
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder

class FilesShowCommand(private val fileTester: FileTester) : BaseCommand<Any?, Any?>(setOf()) {
    override fun model(context: Context): Any? {
        TODO("Not yet implemented")
    }

    override fun render(commandCall: CommandCall, result: Any?) {
        TODO("Not yet implemented")
    }

    override fun process(model: Any?, eval: Evaluator, recorder: ResultRecorder): Any? {
        TODO("Not yet implemented")
    }

    override fun setUp(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        val element = table(commandCall.element)
        val path = element.getAttr("dir")
        if (path != null) {
            val evalPath = evaluator.evaluate(path).toString()
            element.invoke(
                thead().invoke(
                    th().invoke(
                        italic(" ").css("far fa-folder-open me-1")
                    ).text(evalPath)
                )
            )
            val fileNames = fileTester.fileNames(evalPath)
            if (fileNames.size == 0) {
                addRow(element, EMPTY)
            } else {
                for (fName in fileNames) {
                    addRow(element, fName)
                }
            }
        }
    }

    companion object {
        private const val EMPTY = "<EMPTY>"
    }
}
