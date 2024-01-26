package io.github.adven27.concordion.extensions.exam.ws

import io.github.adven27.concordion.extensions.exam.core.ContentPrinter
import rawhttp.core.EagerHttpResponse
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpOptions
import rawhttp.core.RawHttpRequest
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import java.nio.charset.StandardCharsets
import kotlin.jvm.optionals.getOrNull

interface HttpParser {
    fun parseResponse(r: String): HttpResponse
    fun parseRequest(r: String): HttpRequest
    fun send(r: String): HttpResponse
}

interface Message {
    val body: String?
    val headers: Map<String, String>
    val httpVersion: String
    val raw: String
    fun contentType() = headers["Content-Type"]
    fun headers() = headers.takeIf { it.isNotEmpty() }?.map { (k, v) -> "$k: $v" }
        ?.joinToString("\n", postfix = "\n")
        ?: ""
    fun print(printer: ContentPrinter?): String
}

data class HttpResponse(
    val statusCode: Int,
    val statusPhrase: String,
    override val httpVersion: String,
    override val body: String?,
    override val headers: Map<String, String>
) : Message {
    override fun print(printer: ContentPrinter?) =
        "$httpVersion $statusCode $statusPhrase\n${headers()}\n${body?.let { printer?.print(it) } ?: ""}"
    override val raw = print(null)
    override fun toString() =
        """
        HttpResponse:
         statusCode: $statusCode
         statusPhrase: $statusPhrase
         httpVersion: $httpVersion
         headers: $headers
         body: $body
         raw:
         $raw
        """.trimIndent()

    companion object {
        @Suppress("MagicNumber")
        fun ok(body: String? = null, headers: Map<String, String> = mapOf()) =
            HttpResponse(200, "OK", "HTTP/1.1", body, headers)
    }
}

data class HttpRequest(
    val method: String,
    val url: String,
    override val httpVersion: String,
    override val body: String?,
    override val headers: Map<String, String>
) : Message {
    override fun print(printer: ContentPrinter?) =
        "$method $url $httpVersion\n${headers()}\n${body?.let { printer?.print(it) } ?: ""}"

    override val raw = print(null)

    override fun toString() =
        """
        HttpRequest:
         method: $method
         url: $url
         httpVersion: $httpVersion
         headers: $headers
         body: $body
         raw:
         $raw
        """.trimIndent()
}

open class RawHttpParser(
    private val host: String,
    private val http: RawHttp = RawHttp(RawHttpOptions.newBuilder().allowContentLengthMismatch().build())
) : HttpParser {

    override fun send(r: String) = TcpRawHttpClient().use { toHttpResponse(it.send(parse(r)).eagerly()) }
    override fun parseResponse(r: String) = toHttpResponse(http.parseResponse(r).eagerly(false))

    private fun toHttpResponse(response: EagerHttpResponse<Void>) = HttpResponse(
        statusCode = response.statusCode,
        statusPhrase = response.startLine.reason,
        httpVersion = response.startLine.httpVersion.toString(),
        body = response.body.getOrNull()?.decodeBodyToString(StandardCharsets.UTF_8),
        headers = response.headers.headerNames.associateWith { response.headers.get(it) }
            .mapValues { (_, v) -> v.joinToString() }
    )

    override fun parseRequest(r: String) = parse(r).let { request ->
        HttpRequest(
            method = request.method,
            url = request.uri.let{ "${it.path}?${it.query}" },
            httpVersion = request.startLine.httpVersion.toString(),
            body = request.body.getOrNull()?.decodeBodyToString(StandardCharsets.UTF_8),
            headers = request.headers.headerNames.associateWith { request.headers.get(it) }
                .mapValues { (_, v) -> v.joinToString() }
        )
    }

    protected open fun parse(req: String): RawHttpRequest = req.lines().let { lines ->
        lines.withIndex()
            .dropWhile { it.value.isBlank() }
            .firstOrNull { it.value.isBlank() }
            ?.index
            ?.let { blankIndex ->
                val request = rawHttpRequest(lines.take(blankIndex).joinToString("\r\n"))
                request.takeIf { it.headers.contains("Content-Length") || blankIndex == lines.lastIndex }
                    ?: request.withBody(StringBody(lines.drop(blankIndex + 1).joinToString("\r\n")))
            } ?: rawHttpRequest(req)
    }

    private fun rawHttpRequest(r: String): RawHttpRequest = runCatching { http.parseRequest(r) }
        .recover { http.parseRequest(addHostHeader(r)) }
        .getOrThrow()

    private fun addHostHeader(r: String) = r.lines()
        .dropWhile(String::isBlank)
        .toMutableList()
        .apply { add(1, "Host: $host") }
        .joinToString("\r\n")
}
