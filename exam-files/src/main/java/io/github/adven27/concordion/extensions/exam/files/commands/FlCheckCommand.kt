package io.github.adven27.concordion.extensions.exam.files.commands

import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.XmlVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.ContentAttrs
import io.github.adven27.concordion.extensions.exam.core.content
import io.github.adven27.concordion.extensions.exam.core.html.codeXml
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.divCollapse
import io.github.adven27.concordion.extensions.exam.core.html.generateId
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.table
import io.github.adven27.concordion.extensions.exam.core.html.td
import io.github.adven27.concordion.extensions.exam.core.html.tr
import io.github.adven27.concordion.extensions.exam.core.prettyXml
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.files.FileTester
import io.github.adven27.concordion.extensions.exam.files.FilesResultRenderer
import io.github.adven27.concordion.extensions.exam.files.commands.FlCheckCommand.Model
import java.io.File
import mu.KLogging
import org.concordion.api.CommandCall
import org.concordion.api.Element
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.Result
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.AssertEqualsListener
import org.concordion.api.listener.AssertFailureEvent
import org.concordion.api.listener.AssertSuccessEvent
import org.concordion.internal.util.Announcer

open class FlCheckCommand(private val fileTester: FileTester) : BaseCommand<Model, Model>(setOf("dir")) {
    private val listeners = Announcer.to(AssertEqualsListener::class.java)

    data class Model(val dir: String, val expected: List<FileTester.File>)

    override fun model(context: Context) = Model(
        dir = requireNotNull(context[DIR]) { "Required 'dir' attribute" }.let { context.eval.evaluate(it).toString() },
        expected = context.el.childs().mapNotNull { f ->
            f.takeIf { it.localName() == "file" }?.let {
                FileTester.File(
                    name = requireNotNull(f.attr(NAME)) { "Required 'name' attribute" },
                    content = ContentAttrs(f, context.eval).content
                )
            }
        }
    )

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder): Model {
        val actual = fileTester.fileNames(model.dir)
        model.expected.map { f ->
            when {
                f.name in actual -> when {
                    f.content != null -> XmlVerifier().verify(f.content.body, fileTester.read(model.dir, f.name))
                        .fold(onSuccess = { CheckResult(f) }, onFailure = { CheckResult(f, Fail.Content(it)) })
                    else -> CheckResult(f)
                }
                else -> CheckResult(f, Fail.Absent())
            }
        }
    }

    data class CheckResult(val expected: FileTester.File, val fail: Fail? = null)

    sealed class Fail(): AssertionError() {
        class Content() : Fail()
        class Absent() : Fail()
    }

    override fun render(commandCall: CommandCall, result: Model) {
        TODO("Not yet implemented")
    }

    @Suppress("LongMethod", "NestedBlockDepth", "SpreadOperator")
    override fun verify(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        val root = commandCall.html().css("table-responsive")
        val table = table()
        root.moveChildrenTo(table)
        root(table)
//        if (path != null) {
        val evalPath = ""
        val names = fileTester.fileNames(evalPath)
        val surplusFiles: MutableList<String> = if (names.isEmpty()) ArrayList() else ArrayList(listOf(*names))
        table(flCaption(evalPath))
        addHeader(table, HEADER, FILE_CONTENT)
        var empty = true
        for (f in table.childs()) {
            if ("file" == f.localName()) {
                val fileTag = fileTester.read(f.getAttr("name"))
                val resolvedName = evaluator.resolveToObj(fileTag.name)
                val expectedName = resolvedName?.toString() ?: fileTag.name!!
                val fileNameTD = td(expectedName)
                var pre = codeXml("")
                if (!fileTester.fileExists(evalPath + File.separator + expectedName)) {
                    resultRecorder.record(Result.FAILURE)
                    announceFailure(fileNameTD.el(), "", null)
                } else {
                    resultRecorder.record(Result.SUCCESS)
                    announceSuccess(fileNameTD.el())
                    surplusFiles.remove(expectedName)
                    if (fileTag.content == null) {
                        val id = generateId()
                        val content = fileTester.read(evalPath, expectedName)
                        if (!content.isEmpty()) {
                            pre = div().style("position: relative").invoke(
                                divCollapse("", id),
                                div("id".to(id)).css("collapse show").invoke(
                                    pre.text(content)
                                )
                            )
                        }
                    } else {
                        checkContent(
                            evalPath + File.separator + expectedName,
                            fileTag.content,
                            resultRecorder,
                            pre.el()
                        )
                    }
                }
                table.invoke(
                    tr()(
                        fileNameTD,
                        td()(pre)
                    )
                ).remove(f)
                empty = false
            }
        }
        for (file in surplusFiles) {
            resultRecorder.record(Result.FAILURE)
            val td = td()
            val tr = tr().invoke(
                td,
                td().invoke(
                    codeXml(fileTester.read(evalPath, file))
                )
            )
            table.invoke(tr)
            announceFailure(td.el(), null, file)
        }
        if (empty) {
            addRow(table, EMPTY, "")
        }
//        }
    }

    private fun checkContent(path: String, expected: String?, resultRecorder: ResultRecorder, element: Element) {
        if (!fileTester.fileExists(path)) {
            xmlDoesNotEqual(resultRecorder, element, "(not set)", expected)
            return
        }
        val prettyActual = fileTester.documentFrom(path).prettyXml()
        try {
            XmlVerifier().verify(expected!!, prettyActual)
                .onFailure { f ->
                    when (f) {
                        is ContentVerifier.Fail -> xmlDoesNotEqual(resultRecorder, element, f.actual, f.expected)
                        else -> throw f
                    }
                }
                .onSuccess {
                    element.appendText(prettyActual)
                    xmlEquals(resultRecorder, element)
                }
        } catch (ignore: Exception) {
            logger.warn("Xml verification failed", ignore)
            xmlDoesNotEqual(resultRecorder, element, prettyActual, expected)
        }
    }

    private fun xmlEquals(resultRecorder: ResultRecorder, element: Element) {
        resultRecorder.record(Result.SUCCESS)
        announceSuccess(element)
    }

    private fun xmlDoesNotEqual(resultRecorder: ResultRecorder, element: Element, actual: String, expected: String?) {
        resultRecorder.record(Result.FAILURE)
        announceFailure(element, expected, actual)
    }

    private fun announceSuccess(element: Element) {
        listeners.announce().successReported(AssertSuccessEvent(element))
    }

    private fun announceFailure(element: Element, expected: String?, actual: Any?) {
        listeners.announce().failureReported(AssertFailureEvent(element, expected, actual))
    }

    companion object : KLogging() {
    }

    init {
        listeners.addListener(FilesResultRenderer())
    }
}
