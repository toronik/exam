package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig.Companion.AWAIT_AT_MOST_SEC_CAMEL
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig.Companion.AWAIT_AT_MOST_SEC_SPINAL
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig.Companion.AWAIT_POLL_DELAY_MILLIS_CAMEL
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig.Companion.AWAIT_POLL_DELAY_MILLIS_SPINAL
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig.Companion.AWAIT_POLL_INTERVAL_MILLIS_CAMEL
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig.Companion.AWAIT_POLL_INTERVAL_MILLIS_SPINAL
import io.github.adven27.concordion.extensions.exam.core.html.addCcAttr
import io.github.adven27.concordion.extensions.exam.core.html.addExamAttr
import io.github.adven27.concordion.extensions.exam.core.html.html
import nu.xom.Attribute
import nu.xom.Builder
import nu.xom.Element
import org.asciidoctor.ast.Document
import org.asciidoctor.extension.Postprocessor
import org.concordion.api.ImplementationStatus.EXPECTED_TO_FAIL
import org.concordion.api.ImplementationStatus.EXPECTED_TO_PASS
import org.concordion.api.ImplementationStatus.IGNORED
import org.concordion.api.ImplementationStatus.UNIMPLEMENTED
import org.concordion.internal.ConcordionBuilder.NAMESPACE_CONCORDION_2007
import java.io.StringReader
import kotlin.system.measureTimeMillis

@Suppress("TooManyFunctions")
open class ConcordionPostprocessor : Postprocessor() {
    companion object {
        private const val N = "xmlns:e='${ExamExtension.NS}' " +
            "xmlns:cc='$NAMESPACE_CONCORDION_2007' " +
            "xmlns:c='$NAMESPACE_CONCORDION_2007' " +
            "xmlns:concordion='$NAMESPACE_CONCORDION_2007'"
    }

    override fun process(document: Document, output: String): String {
        val doc = Builder().build(
            StringReader(output.replaceFirst("<html xmlns=\"http://www.w3.org/1999/xhtml\"", "<html $N"))
        )
        measureTimeMillis {
            doc.find("e-mq-clean").forEach { it.addExamAttr("mq-clean", "#TEXT") }
            doc.find("e-db-clean").forEach { it.addExamAttr("db-clean", "#TEXT") }
            doc.find("e-set=").forEach { th(it, "set", true) }
            doc.find("c-set=").forEach { th(it, "set", false) }
            doc.find("c-execute=").forEach { thExp(it, "c-execute=", "execute") }
            doc.find("e-execute=").forEach { thExp(it, "e-execute=", "execute") }
            doc.find("c-assert-equals=").forEach { thExp(it, "c-assert-equals=", "assert-equals") }
            doc.find("c-echo=").forEach { thExp(it, "c-echo=", "echo") }
            doc.find("e-echo=").forEach { thExp(it, "e-echo=", "echo") }
            doc.find("c-run").forEach { it.addCcAttr("run", "concordion") }
            doc.find("e-run").forEach { it.addCcAttr("run", "concordion") }
            doc.eqParams()
            transformToConcordionExamples(doc)
            transformToConcordionBeforeExample(doc)
        }.let { println("ConcordionPostprocessor: $it") }
        return doc.toXML()
    }

    private fun nu.xom.Document.eqParams() {
        setOf(
            "eq",
            "eq-json",
            "eq-xml",
            "verifier",
            "ds",
            AWAIT_AT_MOST_SEC_SPINAL,
            AWAIT_AT_MOST_SEC_CAMEL,
            AWAIT_POLL_DELAY_MILLIS_SPINAL,
            AWAIT_POLL_DELAY_MILLIS_CAMEL,
            AWAIT_POLL_INTERVAL_MILLIS_SPINAL,
            AWAIT_POLL_INTERVAL_MILLIS_CAMEL
        ).onEach { p ->
            find("e-$p=").forEach { thExp(it, "e-$p=", p) }
        }
    }

    private fun th(el: Element, cmd: String, exam: Boolean) {
        val parent = (el.parent as Element)
        val v = parseValue(el)
        (if (parent.localName == "th") parent else el).let {
            if (exam) it.addExamAttr(cmd, v) else it.addCcAttr(cmd, v)
        }
    }

    private fun thExp(el: Element, opt: String, cmd: String) {
        val (cl, exp) = el.getAttribute("class").value.split(opt)
            .let { if (it.size == 2) it[0] to it[1] else null to it[0] }
        val parent = (el.parent as Element)
        (if (parent.localName == "th") parent else el).let {
            if (opt.startsWith("e-")) it.addExamAttr(cmd, exp) else it.addCcAttr(cmd, exp)
        }
        el.addAttribute(Attribute("class", cl?.trim() ?: ""))
    }

    private fun nu.xom.Document.find(part: String) = query("//*[contains(@class, '$part')]").map { it as Element }

    private fun parseValue(e: Element) = e.getAttribute("class").value.split(" ")[0].split("=")[1]

    open fun transformToConcordionExamples(doc: nu.xom.Document) {
        doc.query("//div[contains(@class, 'exampleblock')]/div[contains(@class, 'title')]")
            .map { it as Element }
            .forEach {
                val content = it.parent as Element
                content.addCcAttr("example", it.html().text())
                content.removeChild(it)
                content.addCcAttr(
                    "status",
                    if (content.childElements.size() == 0) {
                        UNIMPLEMENTED.tag.apply { content.appendChild(this) }
                    } else {
                        val classes = content.getAttributeValue("class").split(" ")
                        when {
                            UNIMPLEMENTED.tag in classes -> UNIMPLEMENTED.tag
                            EXPECTED_TO_FAIL.tag in classes -> EXPECTED_TO_FAIL.tag
                            IGNORED.tag in classes -> IGNORED.tag
                            else -> EXPECTED_TO_PASS.tag
                        }
                    }
                )
            }
    }

    open fun transformToConcordionBeforeExample(doc: nu.xom.Document) {
        doc.query("//div[@id='before']")
            .singleOrNull()
            ?.let { it as Element }
            ?.addCcAttr("example", "before")
    }
}
