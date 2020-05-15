package com.adven.concordion.extensions.exam.ws

import com.adven.concordion.extensions.exam.core.ExamExtension
import com.adven.concordion.extensions.exam.core.ExamPlugin
import com.adven.concordion.extensions.exam.core.commands.ExamCommand
import com.adven.concordion.extensions.exam.core.resolveJson
import com.adven.concordion.extensions.exam.core.resolveXml
import com.adven.concordion.extensions.exam.core.utils.prettyJson
import com.adven.concordion.extensions.exam.core.utils.prettyXml
import com.adven.concordion.extensions.exam.files.commands.PlaceholderSupportDiffEvaluator
import com.google.common.base.Optional
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals
import net.javacrumbs.jsonunit.core.Configuration
import org.concordion.api.Evaluator
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.diff.Diff
import org.xmlunit.diff.DifferenceEvaluators
import org.xmlunit.diff.NodeMatcher
import java.util.*

@SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
class WsPlugin @JvmOverloads constructor(
    uri: String = "http://localhost",
    basePath: String = "",
    port: Int = 8080,
    private val nodeMatcher: NodeMatcher = ExamExtension.DEFAULT_NODE_MATCHER,
    private val jsonUnitCfg: Configuration = ExamExtension.DEFAULT_JSON_UNIT_CFG,
    additionalContentTypeResolvers: Map<ContentType, ContentResolver> = emptyMap(),
    additionalContentTypeVerifiers: Map<ContentType, ContentVerifier> = emptyMap(),
    additionalContentTypePrinters: Map<ContentType, ContentPrinter> = emptyMap(),
    private val contentTypeResolver: ContentTypeResolver = MultiPartAware()
) : ExamPlugin {

    constructor(withPort: Int) : this(port = withPort)
    constructor(withBasePath: String, withPort: Int) : this(basePath = withBasePath, port = withPort)
    constructor(withUri: String, withBasePath: String, withPort: Int, withJsonUnitCfg: Configuration) :
        this(uri = withUri, basePath = withBasePath, port = withPort, jsonUnitCfg = withJsonUnitCfg)

    constructor(withBasePath: String, withPort: Int, withNodeMatcher: NodeMatcher) :
        this(basePath = withBasePath, port = withPort, nodeMatcher = withNodeMatcher)

    constructor(withBasePath: String, withPort: Int, withJsonUnitCfg: Configuration) :
        this(basePath = withBasePath, port = withPort, jsonUnitCfg = withJsonUnitCfg)

    constructor(
        withBasePath: String, withPort: Int, withNodeMatcher: NodeMatcher, withJsonUnitCfg: Configuration
    ) : this(
        basePath = withBasePath, port = withPort, nodeMatcher = withNodeMatcher, jsonUnitCfg = withJsonUnitCfg
    )

    constructor(withNodeMatcher: NodeMatcher) : this(nodeMatcher = withNodeMatcher)
    constructor(withJsonUnitCfg: Configuration) : this(jsonUnitCfg = withJsonUnitCfg)
    constructor(withNodeMatcher: NodeMatcher, withJsonUnitCfg: Configuration) :
        this(nodeMatcher = withNodeMatcher, jsonUnitCfg = withJsonUnitCfg)

    private val contentTypeResolvers: MutableMap<ContentType, ContentResolver> = mutableMapOf(
        ContentType.JSON to JsonResolver(),
        ContentType.XML to XmlResolver(),
        ContentType.TEXT to JsonResolver()
    )
    private val contentTypeVerifiers: MutableMap<ContentType, ContentVerifier> = mutableMapOf(
        ContentType.JSON to JsonVerifier(jsonUnitCfg),
        ContentType.XML to XmlVerifier(nodeMatcher, jsonUnitCfg),
        ContentType.TEXT to ContentVerifier.AssertBased()
    )
    private val contentTypePrinters: MutableMap<ContentType, ContentPrinter> = mutableMapOf(
        ContentType.JSON to JsonPrinter(),
        ContentType.XML to XmlPrinter(),
        ContentType.TEXT to ContentPrinter.AsIs()
    )

    init {
        RestAssured.baseURI = uri
        RestAssured.basePath = basePath
        RestAssured.port = port
        contentTypeResolvers += additionalContentTypeResolvers
        contentTypeVerifiers += additionalContentTypeVerifiers
        contentTypePrinters += additionalContentTypePrinters
    }

    override fun commands(): List<ExamCommand> = listOf(
        SoapCommand("soap", "div"),
        PostCommand("post", "div"),
        GetCommand("get", "div"),
        PutCommand("put", "div"),
        DeleteCommand("delete", "div"),
        CaseCommand("tr", contentTypeResolvers, contentTypeVerifiers, contentTypePrinters, contentTypeResolver),
        CaseCheckCommand("check", "div"),
        ExpectedStatusCommand("rs-status", "code")
    )

    interface ContentTypeResolver {
        open class Simple : ContentTypeResolver {
            override fun resolve(contentType: String): ContentType {
                return ContentType.fromContentType(contentType) ?: ContentType.TEXT
            }
        }

        fun resolve(contentType: String): ContentType
    }

    open class MultiPartAware : ContentTypeResolver.Simple() {
        override fun resolve(contentType: String) = if (contentType == "multipart/form-data")
            ContentType.JSON else super.resolve(contentType)
    }

    interface ContentResolver {
        fun resolve(content: String, evaluator: Evaluator): String
    }

    open class JsonResolver() : ContentResolver {
        override fun resolve(content: String, evaluator: Evaluator): String = evaluator.resolveJson(content)
    }

    open class XmlResolver() : ContentResolver {
        override fun resolve(content: String, evaluator: Evaluator): String = evaluator.resolveXml(content)
    }

    interface ContentPrinter {
        open class AsIs() : ContentPrinter {
            override fun print(content: String): String = content
            override fun style(): String = "text"
        }

        fun print(content: String): String
        fun style(): String
    }

    open class JsonPrinter() : ContentPrinter {
        override fun print(content: String): String = content.prettyJson()
        override fun style(): String = "json"
    }

    open class XmlPrinter() : ContentPrinter {
        override fun print(content: String): String = content.prettyXml()
        override fun style(): String = "xml"
    }

    interface ContentVerifier {
        fun verify(expected: String, actual: String): Result
        fun setConfiguration(cfg: Configuration)

        open class AssertBased : ContentVerifier {
            override fun verify(expected: String, actual: String) = try {
                when {
                    expected.isNotEmpty() && actual.isEmpty() -> Result.failed(details = "Actual is empty", actual = actual, expected = expected)
                    expected.isEmpty() && actual.isEmpty() -> Result.passed()
                    else -> {
                        assertThat(expected, actual)
                        Result.passed()
                    }
                }
            } catch (e: AssertionError) {
                Result.failed(details = e.message ?: "$e", actual = actual, expected = expected)
            }

            protected open fun assertThat(expected: String, actual: String) = assertThat(actual, equalTo(expected))

            override fun setConfiguration(cfg: Configuration) = Unit
        }

        data class Fail(val details: String, val expected: String, val actual: String)
        data class Result(val fail: Optional<Fail>) {
            companion object {
                fun passed(): Result = Result(Optional.absent())
                fun failed(details: String, actual: String, expected: String): Result = Result(Optional.of(Fail(details, expected, actual)))
            }
        }
    }

    open class XmlVerifier(private val nodeMatcher: NodeMatcher, private var configuration: Configuration) : ContentVerifier.AssertBased() {
        override fun assertThat(expected: String, actual: String) {
            val diff = diff(expected, actual)
            if (diff.hasDifferences()) throw AssertionError(diff.toString())
        }

        override fun setConfiguration(cfg: Configuration) {
            configuration = cfg
        }

        protected fun diff(expected: String, actual: String): Diff {
            return DiffBuilder.compare(expected.trim())
                .checkForSimilar().withNodeMatcher(nodeMatcher)
                .withTest(actual.trim())
                .withDifferenceEvaluator(
                    DifferenceEvaluators.chain(
                        DifferenceEvaluators.Default,
                        PlaceholderSupportDiffEvaluator(configuration)
                    )
                )
                .ignoreComments().ignoreWhitespace().build()
        }
    }

    open class JsonVerifier(private var configuration: Configuration) : ContentVerifier.AssertBased() {
        override fun assertThat(expected: String, actual: String) = assertJsonEquals(expected, actual, configuration)

        override fun setConfiguration(cfg: Configuration) {
            configuration = cfg
        }
    }
}