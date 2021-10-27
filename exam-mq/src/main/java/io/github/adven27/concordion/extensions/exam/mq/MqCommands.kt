package io.github.adven27.concordion.extensions.exam.mq

import io.github.adven27.concordion.extensions.exam.core.ContentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.contentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.ExamResultRenderer
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamVerifyCommand
import io.github.adven27.concordion.extensions.exam.core.commands.FromAttrs
import io.github.adven27.concordion.extensions.exam.core.commands.VarsAttrs
import io.github.adven27.concordion.extensions.exam.core.commands.awaitConfig
import io.github.adven27.concordion.extensions.exam.core.errorMessage
import io.github.adven27.concordion.extensions.exam.core.html.CLASS
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.caption
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.divCollapse
import io.github.adven27.concordion.extensions.exam.core.html.generateId
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.italic
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.core.html.table
import io.github.adven27.concordion.extensions.exam.core.html.tbody
import io.github.adven27.concordion.extensions.exam.core.html.td
import io.github.adven27.concordion.extensions.exam.core.html.tr
import io.github.adven27.concordion.extensions.exam.core.vars
import mu.KLogging
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.Result.FAILURE
import org.concordion.api.ResultRecorder
import org.junit.Assert.assertEquals

@Suppress("TooManyFunctions")
class MqCheckCommand(
    name: String,
    tag: String,
    private val mqTesters: Map<String, MqTester>
) : ExamVerifyCommand(name, tag, ExamResultRenderer()) {

    companion object : KLogging()

    @Suppress("LongMethod", "TooGenericExceptionCaught", "NestedBlockDepth")
    override fun verify(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        val root = cmd.html()
        Attrs(cmd).let { attrs ->
            val expectedMessages: List<TypedMessage> = expectedMessages(root, eval)
            var actualMessages: List<MqTester.Message> = mqTesters.getOrFail(attrs.mqName).receive().toMutableList()

            try {
                try {
                    assertEquals(expectedMessages.size, actualMessages.size)
                } catch (e: java.lang.AssertionError) {
                    attrs.awaitConfig?.let {
                        actualMessages =
                            awaitExpectedSize(expectedMessages, actualMessages, attrs.mqName, attrs.awaitConfig)
                    } ?: throw e
                }
            } catch (s: SizeMismatchError) {
                resultRecorder.record(FAILURE)
                root.removeChildren().below(
                    sizeCheckError(s.mqName, s.expected, s.actual, s.message)
                )
                root.below(pre(attrs.awaitConfig?.timeoutMessage(s.cause!!) ?: s.toString()).css("alert alert-danger small"))
                return
            } catch (e: java.lang.AssertionError) {
                resultRecorder.record(FAILURE)
                root.below(sizeCheckError(attrs.mqName, expectedMessages, actualMessages, e.message))
                root.parent().remove(root)
                return
            }

            val tableContainer = table()(captionEnvelopOpen(attrs.mqName))
            root.removeChildren().attrs("class" to "mq-check")(tableContainer)

            var cnt: Html? = null
            if (!attrs.vertically) {
                cnt = tr()
                tableContainer(cnt)
            }

            if (expectedMessages.isEmpty()) {
                if (cnt == null) tableContainer(tr()(td("<EMPTY>"))) else cnt(td("<EMPTY>"))
            }

            expectedMessages.sortedTyped(attrs.contains)
                .zip(actualMessages.sorted(attrs.contains)) { e, a -> VerifyPair(a, e) }
                .forEach {
                    logger.info("Verifying {}", it)
                    val typeConfig = contentTypeConfig(it.expected.type)
                    val bodyContainer = container("", attrs.collapsable, typeConfig.printer.style())
                    val headersContainer = checkHeaders(
                        it.actual.headers,
                        it.expected.headers,
                        resultRecorder,
                    )
                    checkContent(
                        typeConfig,
                        it.actual.body,
                        it.expected.body,
                        resultRecorder,
                        bodyContainer
                    )

                    if (cnt != null) {
                        cnt(
                            td()(
                                table().tableContainerAppend(headersContainer, bodyContainer, attrs.collapsable)
                            )
                        )
                    } else {
                        tableContainer.tableContainerAppend(headersContainer, bodyContainer, attrs.collapsable)
                    }
                }
        }
    }

    private fun Html.tableContainerAppend(headersContainer: Html?, bodyContainer: Html, collapsable: Boolean): Html =
        this(
            tr()(td()(headersContainer)),
            tr()(if (collapsable) collapsed(bodyContainer) else bodyContainer)
        )

    private fun expectedMessages(root: Html, eval: Evaluator) = messageTags(root).mapNotNull { html ->
        MessageAttrs(html, eval).let { nullOrMessage(it.from, it.headers) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun awaitExpectedSize(
        expected: List<TypedMessage>,
        originalActual: List<MqTester.Message>,
        mqName: String,
        awaitConfig: AwaitConfig
    ): List<MqTester.Message> {
        val actual = originalActual.toMutableList()
        val prevActual: MutableList<MqTester.Message> = mutableListOf()
        try {
            val tester = mqTesters.getOrFail(mqName)
            awaitConfig.await("Await MQ $mqName").untilAsserted {
                actual
                    .apply { prevActual.apply { clear(); addAll(this) } }
                    .apply { if (!tester.accumulateOnRetries()) clear() }
                    .addAll(tester.receive())
                assertEquals(expected.size, actual.size)
            }
            return actual
        } catch (e: Exception) {
            throw SizeMismatchError(
                mqName,
                expected,
                prevActual,
                e.cause?.message ?: e.message ?: "$e",
                e
            )
        }
    }

    class SizeMismatchError(
        val mqName: String,
        val expected: List<TypedMessage>,
        val actual: List<MqTester.Message>,
        message: String,
        exception: Throwable
    ) : java.lang.AssertionError(message, exception)

    @Suppress("SpreadOperator")
    private fun sizeCheckErrorHeaders(
        expected: Map<String, String>,
        actual: Map<String, String>,
        msg: String?
    ): Html = div().css("rest-failure bd-callout bd-callout-danger")(
        div(msg),
        span("Expected:"),
        expected.renderHeaders(),
        span("but was:"),
        actual.renderHeaders(),
    )

    private fun messageTags(root: Html) =
        root.childs().filter { it.localName() == "message" }.ifEmpty { listOf(root) }

    private fun nullOrMessage(from: FromAttrs, headers: Map<String, String>) =
        if (from.content.isEmpty()) null
        else TypedMessage(from.contentType, from.content, headers)

    private fun List<TypedMessage>.sortedTyped(contains: String) =
        if (needSort(contains)) sortedBy { it.body } else this

    private fun List<MqTester.Message>.sorted(contains: String) =
        if (needSort(contains)) sortedBy { it.body } else this

    private fun needSort(contains: String) = "EXACT" != contains

    private fun renderMessages(msg: String, messages: List<TypedMessage>, mqName: String) =
        listOf(
            span(msg),
            table()(
                captionEnvelopOpen(mqName),
                tbody()(
                    messages.map { tr()(container(it.body, type = it.type)) }
                )
            )
        )

    private fun container(txt: String, collapsable: Boolean = false, type: String) =
        container(txt, type, collapsable).style("margin: 0")

    @Suppress("SpreadOperator")
    private fun checkHeaders(
        actual: Map<String, String>,
        expected: Map<String, String>,
        resultRecorder: ResultRecorder
    ): Html? = if (expected.isEmpty()) null
    else try {
        assertEquals("Different headers size", expected.size, actual.size)
        table()(
            caption("Headers").css("small"),
            *expected.entries.partition { actual[it.key] != null }.let { (m, u) ->
                matchedRows(m, resultRecorder, actual) + unmatchedRows(u, actual, m, resultRecorder)
            }.toTypedArray()
        )
    } catch (e: AssertionError) {
        resultRecorder.record(FAILURE)
        sizeCheckErrorHeaders(expected, actual, e.message)
    }

    private fun matchedRows(
        matched: List<Map.Entry<String, String>>,
        resultRecorder: ResultRecorder,
        actual: Map<String, String>
    ) = matched.map { headerRow(resultRecorder, it.key to actual[it.key]!!, it.toPair()) }

    private fun unmatchedRows(
        unmatchedExpected: List<Map.Entry<String, String>>,
        actual: Map<String, String>,
        matched: List<Map.Entry<String, String>>,
        resultRecorder: ResultRecorder
    ) = unmatchedExpected.zip(unmatchedActual(actual, matched)).map { (e, a) ->
        headerRow(resultRecorder, a.toPair(), e.toPair())
    }

    private fun unmatchedActual(actual: Map<String, String>, matched: List<Map.Entry<String, String>>) =
        actual.entries.filterNot { matched.hasKey(it) }

    private fun List<Map.Entry<String, String>>.hasKey(it: Map.Entry<String, String>) = map { it.key }.contains(it.key)

    private fun headerRow(resultRecorder: ResultRecorder, a: Pair<String, String>, e: Pair<String, String>) = tr()(
        resultRecorder.check(td(), a.first, e.first),
        resultRecorder.check(td(), a.second, e.second),
    )

    @Suppress("TooGenericExceptionCaught")
    private fun checkContent(
        typeConfig: ContentTypeConfig,
        actual: String,
        expected: String,
        resultRecorder: ResultRecorder,
        root: Html
    ) = typeConfig.let { (_, verifier, printer) ->
        verifier.verify(expected, actual).onFailure { f ->
            when (f) {
                is ContentVerifier.Fail -> {
                    root.attr("class", "")
                    // FIXME for 'Verify queue with horizontal layout'
                    if (root.el.localName == "td") {
                        root.css("exp-body")
                    }
                    val diff = div().css(typeConfig.printer.style())
                    val (_, errorMsg) = errorMessage(
                        message = f.details,
                        html = diff,
                        type = typeConfig.printer.style()
                    )
                    resultRecorder.failure(diff, printer.print(f.actual), printer.print(f.expected))
                    root(errorMsg)
                }
                else -> throw f
            }
        }.onSuccess {
            root.text(printer.print(expected))
            resultRecorder.pass(root)
        }
    }

    @Suppress("SpreadOperator")
    private fun sizeCheckError(
        mqName: String,
        expected: List<TypedMessage>,
        actual: List<MqTester.Message>,
        msg: String?
    ): Html = div().css("rest-failure bd-callout bd-callout-danger")(
        div(msg),
        *renderMessages("Expected: ", expected, mqName).toTypedArray(),
        *renderMessages("but was: ", actual.map { TypedMessage("xml", it.body, it.headers) }, mqName).toTypedArray()
    )

    open class TypedMessage(val type: String, body: String = "", headers: Map<String, String> = emptyMap()) :
        MqTester.Message(body, headers)

    open class ParametrizedTypedMessage(
        type: String,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
        val params: Map<String, String> = emptyMap()
    ) : TypedMessage(type, body, headers)

    data class VerifyPair(val actual: MqTester.Message, val expected: TypedMessage) {
        override fun toString() = "actual=$actual, expected=$expected"
    }

    class Attrs(call: CommandCall) {
        val mqName: String = call.html().attr(NAME) ?: call.expression
        val vertically = call.html().takeAwayAttr("layout", "VERTICALLY").uppercase() == "VERTICALLY"
        val contains = call.html().takeAwayAttr("contains", "EXACT")
        val collapsable = call.html().takeAwayAttr("collapsable", "false").toBoolean()
        val awaitConfig = call.awaitConfig()

        companion object {
            private const val NAME = "name"
        }
    }
}

class MessageAttrs(root: Html, evaluator: Evaluator) {
    private val verifyAs: String? = root.takeAwayAttr(VERIFY_AS)
    private val formatAs: String? = root.takeAwayAttr(FORMAT_AS)
    val from: FromAttrs = FromAttrs(root, evaluator, verifyAs ?: formatAs)
    val vars: VarsAttrs = VarsAttrs(root, evaluator)
    val headers: Map<String, String> = root.takeAwayAttr(HEADERS).attrToMap(evaluator)

    companion object {
        private const val VERIFY_AS = "verifyAs"
        private const val FORMAT_AS = "formatAs"
        private const val HEADERS = "headers"
        private const val PARAMS = "params"
    }
}

@Suppress("SpreadOperator")
private fun Map<String, String>.renderHeaders() = if (isNotEmpty())
    table()(
        caption("Headers").css("small"),
        *this.toSortedMap().map { tr()(td(it.key), td(it.value)) }.toTypedArray()
    )
else null

class MqPurgeCommand(name: String, tag: String, private val mqTesters: Map<String, MqTester>) : ExamCommand(name, tag) {
    override fun execute(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        super.execute(commandCall, evaluator, resultRecorder, fixture)
        commandCall.html().also { root ->
            Attrs.from(root).also {
                mqTesters.getOrFail(it.mqName).purge()
                renderCommand(root, it.mqName)
            }
        }
    }

    private fun renderCommand(root: Html, mqName: String) {
        root.removeChildren()(
            table()(
                caption()(
                    italic(" ", CLASS to "far fa-envelope-open me-1"),
                    span("$mqName purged")
                )
            )
        )
    }

    data class Attrs(val mqName: String) {
        companion object {
            private const val NAME = "name"
            fun from(root: Html) = Attrs(root.attrOrFail(NAME))
        }
    }
}

private fun String?.attrToMap(eval: Evaluator): Map<String, String> =
    this?.vars(eval)?.mapValues { it.value.toString() } ?: emptyMap()

private fun Map<String, MqTester>.getOrFail(mqName: String?): MqTester = this[mqName]
    ?: throw IllegalArgumentException("MQ with name $mqName not registered in MqPlugin")

private fun captionEnvelopOpen(mqName: String) =
    caption()(italic(" ", CLASS to "fa fa-envelope-open me-1"), span(mqName))

private fun captionEnvelopClosed(mqName: String?) =
    caption()(italic(" ", CLASS to "fa fa-envelope me-1"), span(mqName))

private fun container(text: String, type: String, collapsable: Boolean) =
    if (collapsable) collapsableContainer(text, type) else fixedContainer(text, type)

private fun collapsed(container: Html) = td("class" to "exp-body")(
    div().style("position: relative")(
        divCollapse("", container.attr("id").toString()).css("default-collapsed"),
        container
    )
)

private fun fixedContainer(text: String, type: String) = td(text).css("$type exp-body")

private fun collapsableContainer(text: String, type: String) =
    div(text, "id" to generateId()).css("$type file collapse show")
