package io.github.adven27.concordion.extensions.exam.core.report

import nu.xom.Attribute
import org.assertj.core.api.Assertions.assertThat
import org.concordion.api.Element
import org.concordion.api.Resource
import org.concordion.api.listener.SpecificationProcessingEvent
import org.junit.Test

class AgentReportListenerTest {

    private val collected = mutableListOf<SpecResult>()
    private val accumulator = object : AgentReportAccumulator() {
        override fun add(result: SpecResult) {
            collected.add(result)
        }
    }

    @Test
    fun `extracts passed example`() {
        val root = buildDom {
            example("happy path", successes = 3) {}
        }
        fire(root, "/specs/Simple.html")

        assertThat(collected[0].status).isEqualTo(Status.PASS)
        assertThat(collected[0].examples[0].failures).isEmpty()
    }

    @Test
    fun `extracts failure with message and anchor`() {
        val root = buildDom {
            example("http check", failures = 1) {
                errorContainer("error-123") {
                    preFailure("Status code mismatch\nExpected: <200>\nbut: was <404>")
                    httpFailure("HTTP/1.1 200", "HTTP/1.1 404")
                }
            }
        }
        fire(root, "/specs/Http.html")

        val failure = collected[0].examples[0].failures[0]
        assertThat(failure.command).isEqualTo("http")
        assertThat(failure.message).contains("Status code mismatch")
        assertThat(failure.htmlAnchor).isEqualTo("error-123")
        assertThat(failure.expected).contains("200")
        assertThat(failure.actual).contains("404")
    }

    @Test
    fun `extracts context from ancestor http attribute`() {
        val root = buildDom {
            example("endpoint check", failures = 1) {
                httpCommandContainer("POST /conflicts/1/resolve") {
                    errorContainer("error-456") {
                        preFailure("JSON documents are different")
                        httpFailure("expected", "actual")
                    }
                }
            }
        }
        fire(root, "/specs/Ctx.html")

        assertThat(collected[0].examples[0].failures[0].context).isEqualTo("POST /conflicts/1/resolve")
    }

    @Test
    fun `filters out example-header pseudo-failures`() {
        val root = buildDom {
            example("My Example", failures = 1) {
                val span = Element("span")
                span.addStyleClass("failure")
                val del = Element("del")
                del.appendText("My Example")
                span.appendChild(del)
                parent.appendChild(span)
            }
        }
        fire(root, "/specs/Header.html")

        assertThat(collected[0].examples[0].failures).isEmpty()
    }

    @Test
    fun `extracts error with stacktrace`() {
        val root = buildDom {
            example("load data", exceptions = 1) {
                errorSpan(
                    "HikariPool-1 - Connection not available",
                    "at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:155)"
                )
            }
        }
        fire(root, "/specs/Broken.html")

        assertThat(collected[0].status).isEqualTo(Status.ERROR)
        assertThat(collected[0].examples[0].error).contains("HikariPool-1")
    }

    @Test
    fun `multiple examples mixed`() {
        val root = buildDom {
            example("passes", successes = 2) {}
            example("fails", failures = 1) {
                errorContainer("error-789") {
                    preFailure("expected:<ACTIVE> but was:<CONFLICT>")
                }
            }
        }
        fire(root, "/specs/Multi.html")

        assertThat(collected[0].examples[0].status).isEqualTo(Status.PASS)
        assertThat(collected[0].examples[1].status).isEqualTo(Status.FAIL)
    }

    private fun fire(root: Element, resourcePath: String) {
        val listener = AgentReportListener(accumulator, setOf("db-check", "mq-check", "http", "eq"))
        listener.afterProcessingSpecification(SpecificationProcessingEvent(Resource(resourcePath), root))
    }

    // --- DOM Builder ---

    private fun buildDom(block: DomBuilder.() -> Unit): Element {
        val root = Element("html")
        val body = Element("body")
        root.appendChild(body)
        DomBuilder(body).block()
        return root
    }

    private class DomBuilder(private val parent: Element) {
        fun example(name: String, successes: Int = 0, failures: Int = 0, exceptions: Int = 0, block: EB.() -> Unit) {
            val div = Element("div").apply {
                addAttribute("id", name.replace(" ", "-"))
                addStyleClass("exam-example mb-3")
                addAttribute("data-summary-success", successes.toString())
                addAttribute("data-summary-failure", failures.toString())
                addAttribute("data-summary-exception", exceptions.toString())
                val h4 = Element("h4")
                h4.appendText(name)
                appendChild(h4)
            }
            EB(div).block()
            parent.appendChild(div)
        }
    }

    private class EB(val parent: Element) {
        fun errorContainer(id: String, block: EB.() -> Unit) {
            val wrapper = Element("div").apply { addStyleClass("alert-warning") }
            val inner = Element("div")
            val container = Element("div").apply { addAttribute("id", id) }
            EB(container).block()
            inner.appendChild(container)
            wrapper.appendChild(inner)
            parent.appendChild(wrapper)
        }

        fun httpCommandContainer(endpoint: String, block: EB.() -> Unit) {
            val xomDiv = nu.xom.Element("div")
            xomDiv.addAttribute(Attribute("e:http", "http://exam.extension.io", endpoint))
            val div = Element(xomDiv)
            EB(div).block()
            parent.appendChild(div)
        }

        fun preFailure(text: String) {
            val pre = Element("pre").apply { addStyleClass("doc-code language-json failure") }
            val code = Element("code")
            code.appendText(text)
            pre.appendChild(code)
            parent.appendChild(pre)
        }

        fun httpFailure(expected: String, actual: String) {
            val div = Element("div").apply { addStyleClass("http failure") }
            val del = Element("del").apply {
                addStyleClass("expected")
                appendText(expected)
            }
            val ins = Element("ins").apply {
                addStyleClass("actual")
                appendText(actual)
            }
            div.appendChild(del)
            div.appendChild(ins)
            parent.appendChild(div)
        }

        fun errorSpan(message: String, stacktrace: String?) {
            val span = Element("span").apply { addStyleClass("error") }
            val msg = Element("span").apply {
                addStyleClass("errorMessage")
                appendText(message)
            }
            span.appendChild(msg)
            if (stacktrace != null) {
                val pre = Element("pre").apply {
                    addStyleClass("stackTrace")
                    appendText(stacktrace)
                }
                span.appendChild(pre)
            }
            parent.appendChild(span)
        }
    }
}
