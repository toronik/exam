package io.github.adven27.concordion.extensions.exam.ws

import com.github.jknack.handlebars.Options
import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.handlebars.HANDLEBARS
import io.github.adven27.concordion.extensions.exam.ws.HttpContentType.TEXT
import org.concordion.api.Command

@Suppress("LongParameterList")
class WsPlugin @JvmOverloads constructor(
    private val host: String = "localhost",
    private val basePath: String = "",
    private val port: Int? = 8080,
    private val httpContentTypeResolver: HttpContentTypeResolver = HttpContentTypeResolver.Simple(),
    private val httpTester: HttpTester = RawHttpTester("$host${port?.let { ":$it" } ?: ""}$basePath"),
    private val override: Map<String, Command> = mapOf()
) : ExamPlugin.NoSetUp(), ExamPlugin {

    constructor(withPort: Int) : this(port = withPort)
    constructor(withBasePath: String, withPort: Int) : this(basePath = withBasePath, port = withPort)

    init {
        HANDLEBARS.registerHelpers(WsHelperSource::class.java.apply { WsHelperSource.httpTester = httpTester })
    }

    override fun commands(): Map<String, Command> =
        mapOf("http" to HttpCommand(httpContentTypeResolver, httpTester)) + override

    interface HttpContentTypeResolver {
        open class Simple : HttpContentTypeResolver {
            override fun resolve(httpContentType: String) =
                (HttpContentType.from(httpContentType) ?: TEXT).name.lowercase()
        }

        fun resolve(httpContentType: String): String
    }
}

@Suppress("EnumNaming")
enum class WsHelperSource(
    override val example: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val expected: Any? = "",
    override val options: Map<String, String> = emptyMap()
) : ExamHelper {
    http(
        "{{http 'POST' '/mirror/request' '{\"a\": 1}' Content-Type='application/json'}}",
        mapOf(),
        HttpResponse.ok("{\"POST\":\"/mirror/request\",\"body\":{\"a\":1}}")
    ) {
        override fun apply(context: Any?, options: Options) = httpTester.send(
            """
                $context ${options.param<String>(0)}
                ${options.hash.entries.joinToString("\r\n") { (k, v) -> "$k: $v" }}
                ${options.param<String>(1)?.let { "\r\n$it" } ?: ""}
            """.trimIndent()
        )
    };

    override fun toString() = describe()

    companion object {
        lateinit var httpTester: HttpTester
    }
}
