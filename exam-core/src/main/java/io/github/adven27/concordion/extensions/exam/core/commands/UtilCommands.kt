package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.JsonContentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.TextContentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.XmlContentTypeConfig
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.readFile
import io.github.adven27.concordion.extensions.exam.core.resolveNoType
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.vars
import io.restassured.RestAssured
import nu.xom.Element
import nu.xom.XPathContext
import org.awaitility.core.ConditionFactory
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.internal.command.SetCommand
import org.junit.Assert.assertEquals

open class SetVarCommand(override val tag: String) : SetCommand(), BeforeParseExamCommand {

    override fun setUp(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        val el = cmd.html()
        val valueAttr = el.attr("value")
        val valueFrom = el.attr("from")
        val vars = el.getAttr("vars").vars(eval, separator = el.getAttr("varsSeparator", ","))
        vars.forEach {
            val key = "#${it.key}"
            check(eval.getVariable(key) != null) {
                "Variable $key already exists and will be shadowed in set command context. Use different variable name."
            }
            eval.setVariable(key, it.value)
        }
        val value = when {
            valueAttr != null -> eval.resolveToObj(valueAttr)
            valueFrom != null -> eval.resolveNoType(valueFrom.readFile())
            else -> eval.resolveToObj(el.text().trim())
        }

        eval.setVariable(varExp(varAttr(el) ?: cmd.expression), value)
        vars.forEach { eval.setVariable("#${it.key}", null) }

        cmd.element.appendSister(
            cmd.element.deepClone().apply { swapText(value.toString()) }
        )
        cmd.element.addAttribute("hidden", "true")
    }

    private fun varAttr(el: Html) = el.attr("var") ?: el.el.getAttributeValue("set", ExamExtension.NS)
    private fun varExp(varName: String) = if (varName.startsWith("#")) varName else "#$varName"

    override fun beforeParse(elem: Element) {
        org.concordion.api.Element(elem).appendNonBreakingSpaceIfBlank()
        super.beforeParse(elem)
    }
}

@Suppress("MagicNumber")
class AwaitCommand(tag: String) : ExamCommand<AwaitCommand.Model, Unit>(
    setOf(
        UNTIL_TRUE,
        UNTIL_HTTP_POST,
        UNTIL_HTTP_GET,
        SECONDS,
        WITH_BODY_FROM,
        WITH_CONTENT_TYPE,
        HAS_BODY_FROM,
        HAS_BODY,
        HAS_STATUS_CODE
    ),
    tag
) {
    companion object {
        const val UNTIL_TRUE = "untilTrue"
        const val UNTIL_HTTP_GET = "untilHttpGet"
        const val UNTIL_HTTP_POST = "untilHttpPost"
        const val SECONDS = "seconds"
        const val WITH_BODY_FROM = "withBodyFrom"
        const val WITH_CONTENT_TYPE = "withContentType"
        const val HAS_BODY_FROM = "hasBodyFrom"
        const val HAS_BODY = "hasBody"
        const val HAS_STATUS_CODE = "hasStatusCode"
    }

    override fun model(context: Context): Model {
        val untilTrue = context[UNTIL_TRUE]
        val untilGet = context[UNTIL_HTTP_GET]
        val untilPost = context[UNTIL_HTTP_POST]
        val withBody = (context[WITH_BODY_FROM]?.readFile() ?: context.el.text()).let {
            context.eval.resolveToObj(it).toString()
        }
        val hasBody = context[HAS_BODY] ?: context[HAS_BODY_FROM]?.readFile()?.let {
            context.eval.resolveToObj(it).toString()
        }
        val hasStatus = context[HAS_STATUS_CODE]
        val seconds = context[SECONDS]?.toInt() ?: 0
        val config = context.el.awaitConfig("") ?: AwaitConfig()

        return when {
            untilTrue != null -> UntilTrue(untilTrue, config, context.eval)
            !untilGet.isNullOrBlank() -> UntilHttpGet(untilGet, hasBody, hasStatus, config, context.eval)
            !untilPost.isNullOrBlank() -> UntilHttpPost(
                untilPost,
                withBody,
                context[WITH_CONTENT_TYPE] ?: "application/json",
                hasBody,
                hasStatus,
                config,
                context.eval
            )

            seconds > 0 -> Delay(seconds, config, context.eval)
            else -> error("Required one of the attributes: $UNTIL_TRUE/$UNTIL_HTTP_GET/$UNTIL_HTTP_POST/$SECONDS")
        }
    }

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) {
        with(model) {
            when (this) {
                is Delay -> Thread.sleep(1000L * seconds)
                is UntilTrue -> config.await().alias(expression).until { eval.evaluate(expression) == true }
                is HttpReq -> when (this) {
                    is UntilHttpGet -> config.await().get(eval, url, hasBody, hasStatus)
                    is UntilHttpPost -> config.await().post(eval, body, contentType, url, hasBody, hasStatus)
                }
            }
        }
    }

    override fun render(commandCall: CommandCall, result: Unit) {
        commandCall.html().removeChildren()
    }

    @Suppress("LongParameterList")
    private fun ConditionFactory.post(
        eval: Evaluator,
        body: String,
        contentType: String,
        url: String,
        expectedBody: String?,
        expectedStatus: String?
    ) = untilAsserted {
        RestAssured.given().body(body).contentType(contentType).post(url)
            .apply { eval.setVariable("#exam_response", this) }
            .then().let {
                if (expectedStatus != null) it.statusCode(expectedStatus.toInt())
                if (expectedBody != null) assertEquals(expectedBody, it.extract().body().asString())
            }
    }

    private fun ConditionFactory.get(
        eval: Evaluator,
        url: String,
        expectedBody: String?,
        expectedStatus: String?
    ) = untilAsserted {
        RestAssured.get(url)
            .apply { eval.setVariable("#exam_response", this) }
            .then().let {
                if (expectedStatus != null) it.statusCode(expectedStatus.toInt())
                if (expectedBody != null) assertEquals(expectedBody, it.extract().body().asString())
            }
    }

    class UntilTrue(val expression: String, config: AwaitConfig, eval: Evaluator) : Model(config, eval)
    class UntilHttpGet(
        url: String,
        hasBody: String?,
        hasStatus: String?,
        config: AwaitConfig,
        eval: Evaluator
    ) : HttpReq(url, hasBody, hasStatus, config, eval)

    @Suppress("LongParameterList")
    class UntilHttpPost(
        url: String,
        val body: String,
        val contentType: String,
        hasBody: String?,
        hasStatus: String?,
        config: AwaitConfig,
        eval: Evaluator
    ) : HttpReq(url, hasBody, hasStatus, config, eval)

    class Delay(val seconds: Int, config: AwaitConfig, eval: Evaluator) : Model(config, eval)

    sealed class HttpReq(
        val url: String,
        val hasBody: String?,
        val hasStatus: String?,
        config: AwaitConfig,
        eval: Evaluator
    ) : Model(config, eval)

    sealed class Model(val config: AwaitConfig, val eval: Evaluator)
}

class BeforeEachExampleCommand : SimpleCommand() {
    override fun beforeParse(elem: Element) {
        super.beforeParse(elem)
        examples(elem).apply { elem.detach() }.forEach { (it as Element).insertChild(elem.copy(), 0) }
    }

    private fun examples(elem: Element) =
        elem.document.rootElement.getFirstChildElement("body")
            .query(".//e:example", XPathContext("e", ExamExtension.NS))
}

class XmlEqualsCommand : ExamAssertEqualsCommand(XmlContentTypeConfig())
class XmlEqualsFileCommand : ExamAssertEqualsCommand(XmlContentTypeConfig(), { it.readFile() })
class JsonEqualsCommand : ExamAssertEqualsCommand(JsonContentTypeConfig())
class JsonEqualsFileCommand : ExamAssertEqualsCommand(JsonContentTypeConfig(), { it.readFile() })
class TextEqualsCommand : ExamAssertEqualsCommand(TextContentTypeConfig())
class TextEqualsFileCommand : ExamAssertEqualsCommand(TextContentTypeConfig(), { it.readFile() })
