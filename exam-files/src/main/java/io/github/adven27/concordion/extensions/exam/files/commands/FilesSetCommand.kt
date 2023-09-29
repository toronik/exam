package io.github.adven27.concordion.extensions.exam.files.commands

import io.github.adven27.concordion.extensions.exam.core.content
import io.github.adven27.concordion.extensions.exam.core.html.codeXml
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.core.html.table
import io.github.adven27.concordion.extensions.exam.core.html.trWithTDs
import io.github.adven27.concordion.extensions.exam.files.FileTester
import java.io.File
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder

class FilesSetCommand(private val fileTester: FileTester) : BaseCommand<Any?, Any?>(setOf()) {
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
        val root = table(commandCall.element)
        val path = root.getAttr("dir")
        if (path != null) {
            val evalPath = evaluator.evaluate(path).toString()
            fileTester.clearFolder(evalPath)
            root.invoke(flCaption(evalPath))
            addHeader(root, HEADER, FILE_CONTENT)
            var empty = true
            for (f in root.childs()) {
                if ("file" == f.localName()) {
                    f.content()
                    val file = f.content(evaluator)
                    fileTester.createFile(evalPath + File.separator + f.attr("name"), file)
                    root.invoke(
                        trWithTDs(
                            span(f.attr("name")),
                            codeXml(file)
                        )
                    ).remove(f)
                    empty = false
                }
            }
            if (empty) {
                addRow(root, EMPTY, "")
            }
        }
    }
}
