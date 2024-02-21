package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.DEFAULT_JSON_UNIT_CFG
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.DEFAULT_NODE_MATCHER
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.MATCHERS
import io.github.adven27.concordion.extensions.exam.core.utils.ExamDiffEvaluator
import io.github.adven27.concordion.extensions.exam.core.utils.JsonPrettyPrinter
import io.github.adven27.concordion.extensions.exam.core.utils.XmlPlaceholderAwareMatcher
import io.github.adven27.concordion.extensions.exam.core.utils.after
import io.github.adven27.concordion.extensions.exam.core.utils.before
import io.github.adven27.concordion.extensions.exam.core.utils.bool
import io.github.adven27.concordion.extensions.exam.core.utils.ignore
import io.github.adven27.concordion.extensions.exam.core.utils.notNull
import io.github.adven27.concordion.extensions.exam.core.utils.number
import io.github.adven27.concordion.extensions.exam.core.utils.string
import io.github.adven27.concordion.extensions.exam.core.utils.uuid
import io.github.adven27.concordion.extensions.exam.core.utils.within
import mu.KLogging
import net.javacrumbs.jsonunit.JsonAssert
import net.javacrumbs.jsonunit.core.Configuration
import net.javacrumbs.jsonunit.core.internal.JsonUtils
import nu.xom.Builder
import nu.xom.Document
import nu.xom.Serializer
import org.concordion.api.Evaluator
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.matchesRegex
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.Diff
import org.xmlunit.diff.DifferenceEvaluator
import org.xmlunit.diff.DifferenceEvaluators
import org.xmlunit.diff.DifferenceEvaluators.chain
import org.xmlunit.diff.NodeMatcher
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.StringReader

open class Content(val body: String, val type: String) {
    class Json(content: String) : Content(content, "json")
    class Xml(content: String) : Content(content, "xml")
    class Text(content: String) : Content(content, "text")

    fun pretty() = body.pretty(type)

    override fun toString() = pretty()
}

interface ContentPrinter {
    open class AsIs : ContentPrinter {
        override fun print(content: String): String = content
        override fun style(): String = "text"
    }

    fun print(content: String): String
    fun style(): String
}

open class JsonPrinter : ContentPrinter {
    override fun print(content: String): String = content.prettyJson()
    override fun style(): String = "json"
}

open class XmlPrinter : ContentPrinter {
    override fun print(content: String): String = content.prettyXml()
    override fun style(): String = "xml"
}

fun String.prettyXml(): String =
    if (isBlank()) "" else Builder().build(StringReader(this.trim())).prettyXml().let { removeXmlTag(it) }

private fun removeXmlTag(it: String) = it.substring(it.indexOf('\n') + 1)

fun String.prettyJson() = JsonPrettyPrinter().prettyPrint(this)
fun String.pretty(type: String) = when (type) {
    "json" -> prettyJson()
    "xml" -> prettyXml()
    else -> this
}

fun Document.prettyXml(): String {
    try {
        val out = ByteArrayOutputStream()
        val serializer = Serializer(out, "UTF-8")
        serializer.indent = 2
        serializer.write(this)
        return out.toString("UTF-8").trimEnd()
    } catch (expected: Exception) {
        throw InvalidXml(expected)
    }
}

class InvalidXml(t: Throwable) : RuntimeException(t)

interface ContentVerifier {
    fun verify(expected: String, actual: String, eval: Evaluator): Result<Content>
    fun printer(): ContentPrinter

    open class Default(val type: String, val printer: ContentPrinter = ContentPrinter.AsIs()) : ContentVerifier {
        override fun verify(expected: String, actual: String, eval: Evaluator) =
            eval.resolve(expected, type).replace("\${test-unit.", "\${$type-unit.").let {
                verifyResolved(setActualIfNeeded(it, actual, eval)!!, actual)
            }

        override fun printer() = printer

        private fun verifyResolved(expected: String, actual: String) = try {
            when {
                actual.isEmpty() ->
                    if (expected.isEmpty()) {
                        Result.success(Content(body = expected, type = type))
                    } else {
                        Result.failure(Fail("Actual was empty", expected, actual))
                    }

                else -> {
                    assertThat(expected, actual)
                    Result.success(Content(body = expected, type = type))
                }
            }
        } catch (e: AssertionError) {
            logger.warn("Content verification error", e)
            Result.failure(Fail(e.message ?: "$e", expected, actual, type))
        }

        protected open fun assertThat(expected: String, actual: String) =
            assertThat("Text mismatch", actual, matcher(expected) as Matcher<String>)
    }

    data class Fail(val details: String, val expected: String, val actual: String, val type: String = "text") :
        java.lang.AssertionError(details)

    class Exception(actual: String, expected: String, throwable: Throwable) :
        RuntimeException("Failed to verify content:\n$actual\nExpected:\n$expected", throwable)

    companion object : KLogging() {
        fun matcher(e: Any?, prefix: String = "\${text-unit."): Matcher<out Any?> = when {
            e is String -> when {
                e == "${prefix}any-string}" -> string()
                e == "${prefix}any-number}" -> number()
                e == "${prefix}any-boolean}" -> bool()
                e == "${prefix}uuid}" -> uuid()
                e == "${prefix}ignore}" -> ignore()
                e == "${prefix}not-null}" -> notNull()
                e.startsWith("${prefix}regex}") -> matchesRegex(e.substringAfter("}"))
                e.startsWith("${prefix}matches:after}") -> after(e.substringAfter("}"))
                e.startsWith("${prefix}matches:before}") -> before(e.substringAfter("}"))
                e.startsWith("${prefix}matches:within}") -> within(e.substringAfter("}"))
                else -> equalTo(e)
            }

            else -> equalTo(e)
        }

        fun <T> setActualIfNeeded(expected: String?, actual: T, eval: Evaluator) = if (expected != null) {
            val split = expected.split(">>")
            if (split.size > 1) eval.setVariable("#${split[1]}", actual)
            split[0]
        } else {
            null
        }
    }
}

open class XmlVerifier(
    private val nodeMatcher: NodeMatcher,
    private val diffEvaluator: DifferenceEvaluator,
    printer: ContentPrinter
) : ContentVerifier.Default("xml", printer) {

    @JvmOverloads
    constructor(
        diffEvaluator: DifferenceEvaluator = ExamDiffEvaluator(XmlPlaceholderAwareMatcher(MATCHERS)),
        printer: ContentPrinter = XmlPrinter(),
        configureNodeMatcher: (NodeMatcher) -> NodeMatcher = { it }
    ) : this(configureNodeMatcher(DEFAULT_NODE_MATCHER), diffEvaluator, printer)

    override fun assertThat(expected: String, actual: String) = diff(expected, actual).let {
        if (it.hasDifferences()) throw AssertionError(it.toString())
    }

    protected fun diff(expected: String, actual: String): Diff = DiffBuilder.compare(expected.trim())
        .checkForSimilar().withNodeMatcher(nodeMatcher)
        .withTest(actual.trim())
        .withDifferenceEvaluator(chain(DifferenceEvaluators.Default, diffEvaluator))
        .ignoreComments().ignoreWhitespace().build()
}

@Suppress("TooGenericExceptionCaught")
open class JsonVerifier(
    protected val configuration: Configuration,
    printer: ContentPrinter
) : ContentVerifier.Default("json", printer) {

    @JvmOverloads
    constructor(printer: ContentPrinter = JsonPrinter(), configure: (Configuration) -> Configuration = { it }) :
        this(configure(DEFAULT_JSON_UNIT_CFG), printer)

    override fun assertThat(expected: String, actual: String) {
        validate(actual)
        try {
            JsonAssert.assertJsonEquals(expected, actual, configuration)
        } catch (ae: AssertionError) {
            throw ae
        } catch (e: Exception) {
            throw ContentVerifier.Exception(actual, expected, e)
        }
    }

    protected fun validate(actual: String) {
        try {
            JsonUtils.convertToJson(actual, "actual", false)
        } catch (expected: RuntimeException) {
            throw AssertionError(
                "Can not convert actual to json: ${expected.cause?.message ?: expected.message}",
                expected
            )
        }
    }
}

fun String.findResource(eval: Evaluator? = null) = ExamExtension::class.java.getResource(eval?.resolve(this) ?: this)
    ?: throw FileNotFoundException("File not found: $this")

fun String.readFile(eval: Evaluator? = null) = findResource(eval).readText().trimIndent()
