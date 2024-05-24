package io.github.adven27.concordion.extensions.exam.ws

import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.contentVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.div
import io.github.adven27.concordion.extensions.exam.core.html.errorMessage
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.li
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.html.tag
import io.github.adven27.concordion.extensions.exam.core.html.toHtml
import io.github.adven27.concordion.extensions.exam.core.html.ul
import io.github.adven27.concordion.extensions.exam.core.resolve
import io.github.adven27.concordion.extensions.exam.ws.HttpCommand.Model
import io.github.adven27.concordion.extensions.exam.ws.HttpCommand.Verification
import io.github.adven27.concordion.extensions.exam.ws.WsPlugin.HttpContentTypeResolver
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItems
import kotlin.Result.Companion.success

@Suppress("TooManyFunctions")
open class HttpCommand(
    private val typeResolver: HttpContentTypeResolver,
    private val tester: HttpTester
) : ExamCommand<Model, List<Verification>>() {

    override fun model(context: Context) = findListings(context.el)
        .let { (req, resp) ->
            Model(
                variable = context.expression.takeIf { it.isNotBlank() },
                req = req.text(),
                resp = resp?.text(),
                where = context.el.childOrNull { it.localName() == "table" && it.hasClass("where") }
                    ?.let { table ->
                        val keys = table.child("thead", "tr").childs().map { it.text() }
                        table.child("tbody")
                            .childs("tr")
                            .map { tr -> tr.childs("td").map { it.text() } }
                            .map { keys.zip(it).toMap() }
                    },
                verifier = resp?.child("code")?.el?.getAttributeValue("verifier", ExamExtension.NS),
                await = context.awaitConfig
            )
        }

    private fun findListings(html: Html) = when {
        html.localName() == "code" -> html.parent() to null
        else ->
            html
                .takeIf(::listingBlockOrDetails)
                ?.let(::unwrapDetails)
                ?.let { listing -> preBlock(listing) to null }
                ?: html.childs()
                    .filter(::listingBlockOrDetails)
                    .mapNotNull(::unwrapDetails)
                    .map(::preBlock)
                    .take(2)
                    .let { it[0] to it.getOrNull(1) }
    }

    private fun listingBlockOrDetails(tag: Html) = tag.hasClass("listingblock") || tag.localName() == "details"
    private fun preBlock(listingBlock: Html) = listingBlock.child { it.hasClass("content") }.child("pre")

    private fun unwrapDetails(tag: Html) = tag.takeUnless { tag.localName() == "details" }
        ?: tag.child { it.hasClass("content") }.childOrNull { it.hasClass("listingblock") }

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = verifications(model, eval)
        .onEach { recorder.record(if (it.fail) FAILURE else SUCCESS) }
        .also {
            model.variable?.let { v ->
                eval.setVariable(
                    v.takeIf { it.startsWith("#") } ?: "#$v",
                    it.map { Interaction(it.req, it.resp.actual) }
                )
            }
        }

    private fun verifications(m: Model, eval: Evaluator) = m.where
        ?.map { vars -> await(m, eval) { m, eval -> verifyWhere(vars, eval, m) } }
        ?: run {
            listOf(
                await(m, eval) { m, eval ->
                    val r = eval.resolve(m.req)
                    verify(m.resp?.let { eval.resolve(it) }, tester.parseRequest(r), tester.send(r), eval, m.verifier)
                }
            )
        }

    private fun await(m: Model, eval: Evaluator, check: (Model, Evaluator) -> Verification) =
        m.await?.let { c ->
            var lastFailed: Verification? = null
            runCatching {
                c.await("Await HTTP").until(
                    { check(m, eval) },
                    { v -> (!v.fail).also { if (!it || lastFailed == null) lastFailed = v } }
                )
            }.getOrElse { lastFailed!!.apply { awaitConfig = c } }
        } ?: check(m, eval)

    private fun verifyWhere(vars: Map<String, String>, eval: Evaluator, m: Model): Verification {
        vars.setTo(eval)
        val req = eval.resolve(m.req)
        return verify(m.resp?.let { eval.resolve(it) }, tester.parseRequest(req), tester.send(req), eval, m.verifier)
            .also { vars.removeFrom(eval) }
            .also { v -> v.desc = vars.description() }
    }

    private fun Map<String, String>.description() = filterKeys(String::isBlank).values.firstOrNull()

    private fun Map<String, String>.setTo(eval: Evaluator) {
        filterKeys(String::isNotBlank).onEach { (k, v) -> eval.setVariable("#$k", v) }
    }

    private fun Map<String, String>.removeFrom(eval: Evaluator) {
        filterKeys(String::isNotBlank).onEach { (k, _) -> eval.setVariable("#$k", null) }
    }

    private fun verify(resp: String?, req: HttpRequest, actual: HttpResponse, eval: Evaluator, verifier: String?) =
        resp?.let { tester.parseResponse(it) }
            ?.let { verify(req = req, act = actual, exp = it, eval = eval, verifier = verifier) }
            ?: Verification.success(req, actual)

    private fun verify(req: HttpRequest, act: HttpResponse, exp: HttpResponse, eval: Evaluator, verifier: String?) =
        Verification(
            req = req,
            resp = Verification.Response(act, exp),
            body = runCatching {
                verifyBody(
                    actual = act.body,
                    expected = exp.body,
                    verifier = contentVerifier(
                        verifier ?: resolveType(
                            exp.contentType() ?: act.contentType() ?: "text"
                        )
                    ),
                    eval = eval
                )
            },
            headers = runCatching { verifyHeaders(act.headers, exp.headers) },
            statusCode = runCatching { verifyStatusCode(act.statusCode, exp.statusCode) },
            statusPhrase = runCatching { verifyStatusPhrase(act.statusPhrase, exp.statusPhrase) }
        )

    private fun print(message: Message) = message.print(typeConfig(message.contentType() ?: "text").printer())

    protected open fun verifyBody(actual: String?, expected: String?, verifier: ContentVerifier, eval: Evaluator) =
        when {
            expected == null && actual == null -> Unit
            actual == null -> throw AssertionError()
            expected == null -> throw AssertionError()
            else -> verifier.verify(expected, actual, eval).map { }.getOrThrow()
        }

    @Suppress("SpreadOperator")
    protected open fun verifyHeaders(actual: Map<String, String>, expected: Map<String, String>) =
        assertThat("Headers mismatch", actual.toList(), hasItems(*expected.toList().toTypedArray()))

    protected open fun verifyStatusCode(actual: Int, expected: Int) =
        assertThat("Status code mismatch", actual, equalTo(expected))

    protected open fun verifyStatusPhrase(actual: String, expected: String) = expected.takeIf(String::isNotBlank)?.let {
        assertThat("Reason phrase mismatch", actual, equalTo(expected))
    } ?: Unit

    protected fun typeConfig(httpContentType: String) = contentVerifier(resolveType(httpContentType))
    protected fun resolveType(httpContentType: String) = typeResolver.resolve(httpContentType)

    @Suppress("NestedBlockDepth")
    override fun render(commandCall: CommandCall, result: List<Verification>) {
        findListings(commandCall.html()).let { (req, resp) ->
            val listingBlockReq = req.parent().parent()
            val listingBlockResp = resp?.parent()?.parent()
            when (result.size) {
                1 -> {
                    content(listingBlockReq, listingBlockResp, result[0]).let { (req, resp) ->
                        if (container(listingBlockReq).localName() == "details") {
                            container(listingBlockReq)
                        } else {
                            listingBlockReq
                        }.below(req)
                        if (containerOrNull(listingBlockResp)?.localName() == "details") {
                            containerOrNull(listingBlockResp)
                        } else {
                            listingBlockResp
                        }?.below(resp!!)
                    }
                }

                else -> {
                    val where = commandCall.html().childOrNull { it.localName() == "table" && it.hasClass("where") }!!
                    where.below(
                        whereTabsTemplate(
                            result.map { r ->
                                tab(
                                    id = System.currentTimeMillis(),
                                    content = content(listingBlockReq, listingBlockResp, r)
                                        .let { (req, resp) -> listOfNotNull(req, resp) },
                                    desc = r.desc ?: ""
                                )
                            }
                        )
                    )
                    where.remove()
                }
            }
            if (container(listingBlockReq).localName() == "details") {
                container(listingBlockReq)
            } else {
                listingBlockReq
            }.remove()
            if (containerOrNull(listingBlockResp)?.localName() == "details") {
                containerOrNull(listingBlockResp)
            } else {
                listingBlockResp
            }?.remove()
        }
    }

    private fun content(blockReq: Html, blockResp: Html?, r: Verification) =
        if (container(blockReq).localName() == "details") {
            container(blockReq).deepClone().apply {
                child { it.hasClass("content") }
                    .child { it.hasClass("listingblock") }
                    .child { it.hasClass("content") }
                    .child("pre")
                    .swapText(print(r.req))
            }
        } else {
            blockReq.deepClone()
                .apply { child { it.hasClass("content") }.child("pre").swapText(print(r.req)) }
        } to
            if (containerOrNull(blockResp)?.localName() == "details") {
                containerOrNull(blockResp)?.deepClone()?.apply {
                    child { it.hasClass("content") }
                        .child { it.hasClass("listingblock") }
                        .child { it.hasClass("content") }
                        .let {
                            val pre = it.child("pre")
                            it(if (r.fail) renderError(pre, r) else renderSuccess(pre, r))
                        }
                }
            } else {
                blockResp?.deepClone()?.apply {
                    child { it.hasClass("content") }
                        .let {
                            val pre = it.child("pre")
                            it(if (r.fail) renderError(pre, r) else renderSuccess(pre, r))
                        }
                }
            }

    private fun containerOrNull(listingBlock: Html?) = listingBlock?.parent()?.parent()
    private fun container(listingBlock: Html) = listingBlock.parent().parent()

    private fun renderSuccess(pre: Html, r: Verification): Html {
        val id = System.currentTimeMillis()
        return template(id).toHtml().apply {
            findBy("nav-et-$id")!!(pre.css("success").deepClone().apply { el.swapText(print(r.resp.expected!!)) })
            findBy("nav-at-$id")!!(pre.css("success").deepClone().apply { el.swapText(print(r.resp.actual)) })
            pre.parent().remove(pre)
        }
    }

    private fun renderError(pre: Html, r: Verification): Html = errorMessage(
        message = (r.awaitConfig?.timeoutMessage(null) ?: "") + listOfNotNull(
            r.body.exceptionOrNull(),
            r.headers.exceptionOrNull(),
            r.statusCode.exceptionOrNull(),
            r.statusPhrase.exceptionOrNull()
        ).joinToString("\n\n") { it.rootCauseMessage() },
        type = "json",
        html = div("class" to "http failure")(
            Html("del", print(r.resp.expected!!), "class" to "expected"),
            Html("ins", print(r.resp.actual), "class" to "actual")
        )
    ).second.also { pre.parent().remove(pre)(Html("fail")) }

    data class Verification(
        val req: HttpRequest,
        val resp: Response,
        val body: Result<Unit>,
        val headers: Result<Unit>,
        val statusCode: Result<Unit>,
        val statusPhrase: Result<Unit>,
        var awaitConfig: AwaitConfig? = null
    ) {
        companion object {
            fun success(request: HttpRequest, response: HttpResponse) = Verification(
                req = request,
                resp = Response(response, null),
                body = success(Unit),
                headers = success(Unit),
                statusCode = success(Unit),
                statusPhrase = success(Unit)
            )
        }

        var desc: String? = null
        val fail = body.isFailure || statusCode.isFailure || statusPhrase.isFailure || headers.isFailure

        data class Response(val actual: HttpResponse, val expected: HttpResponse?)
    }

    data class Interaction(val req: HttpRequest, val resp: HttpResponse)
    data class Model(
        val variable: String?,
        val req: String,
        val resp: String?,
        val where: List<Map<String, String>>?,
        val verifier: String?,
        val await: AwaitConfig?
    )

    companion object {
        private fun template(id: Long) = //language=xhtml
            """
            <div>
                <nav>
                  <ul class="nav nav-tabs" role="tablist">
                    <li class="nav-item" role="presentation">
                        <button class="nav-link active small text-success" type="button" role="tab"
                        id="nav-et-$id-tab"
                        data-bs-toggle="tab" data-bs-target="#nav-et-$id"
                        aria-controls="nav-et-$id" aria-selected="true">Expected</button>
                    </li>
                    <li class="nav-item" role="presentation">
                        <button class="nav-link small" type="button" role="tab"
                        onclick="setTimeout(() => { window.dispatchEvent(new Event('resize')); }, 200)"
                        id="nav-at-$id-tab"
                        data-bs-toggle="tab" data-bs-target="#nav-at-$id"
                        aria-controls="nav-at-$id" aria-selected="false">Actual</button>
                    </li>
                  </ul>
                </nav>
                <div class="tab-content">
                  <div class="tab-pane fade show active" id="nav-et-$id" role="tabpanel" aria-labelledby="nav-et-$id-tab"/>
                  <div class="tab-pane fade" id="nav-at-$id" role="tabpanel" aria-labelledby="nav-at-$id-tab"/>
                </div>
            </div>
            """

        private fun whereTabsTemplate(tabs: List<Pair<Html, Html>>): Html = tabs.let { list ->
            val failed = tabs.indexOfFirst { it.first.attr("class")?.contains("failure") ?: false }
            val active = if (failed == -1) 0 else failed
            return div()(
                tag("nav")(
                    ul("class" to "nav nav-tabs", "role" to "tablist")(
                        list.mapIndexed { i, p ->
                            li().css("nav-item")(p.first.apply { if (i == active) css("active show") })
                        }
                    )
                ),
                div()(
                    div("class" to "tab-content")(
                        list.mapIndexed { i, p -> p.second.apply { if (i == active) css("active show") } }
                    )
                )
            )
        }

        private fun tab(id: Long, content: List<Html>, desc: String): Pair<Html, Html> {
            val fail = content.any { it.descendants("fail").isNotEmpty() }
            val name = System.currentTimeMillis()
            return Html(
                "button",
                desc,
                "id" to "nav-$name-$id-tab",
                "class" to "nav-link small ${if (fail) "failure" else "text-success"} ",
                "data-bs-toggle" to "tab",
                "data-bs-target" to "#nav-$name-$id",
                "role" to "tab",
                "aria-controls" to "nav-$name-$id",
                "aria-selected" to "false",
                "onclick" to "setTimeout(() => { window.dispatchEvent(new Event('resize')); }, 200)"
            ) to div(
                "class" to "tab-pane fade",
                "id" to "nav-$name-$id",
                "role" to "tabpanel",
                "aria-labelledby" to "nav-$name-$id-tab"
            )(div()(content))
        }
    }
}
