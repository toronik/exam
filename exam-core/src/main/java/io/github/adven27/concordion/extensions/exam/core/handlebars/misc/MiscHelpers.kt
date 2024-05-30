package io.github.adven27.concordion.extensions.exam.core.handlebars.misc

import com.github.jknack.handlebars.Options
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.jackson2ObjectMapperProvider
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.handlebars.evaluator
import io.github.adven27.concordion.extensions.exam.core.prettyJson
import io.github.adven27.concordion.extensions.exam.core.readFile
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.toDate
import java.time.LocalDate

@Suppress("EnumNaming", "EnumEntryNameCase", "MagicNumber")
enum class MiscHelpers(
    override val example: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val expected: Any? = "",
    override val options: Map<String, String> = emptyMap()
) : ExamHelper {
    vars("""{{vars v1='a' v2=2}}""", mapOf(), "") {
        override fun invoke(context: Any?, options: Options): Any {
            val eval = options.evaluator()
            options.hash.map { (k, v) ->
                eval.setVariable("#$k", if (v is String) eval.resolveToObj(v) else v)
            }
            return ""
        }
    },
    set("""{{set 1 "someVar"}}""", mapOf(), 1) {
        override fun invoke(context: Any?, options: Options): Any? = options.params.map {
            options.evaluator().setVariable("#$it", context)
        }.let { context }
    },
    fnn("""{{fnn var1 var2 "default value"}}""", mapOf(), "default value") {
        override fun invoke(context: Any?, options: Options) = context ?: options.params.firstNotNullOfOrNull { it }
    },
    map("""{{map key='value'}}""", mapOf(), mapOf("key" to "value")) {
        override fun invoke(context: Any?, options: Options): Map<*, *> =
            if (context is Map<*, *>) context + options.hash else options.hash
    },
    ls("""{{ls '1' '2'}}""", mapOf(), listOf("1", "2")) {
        override fun invoke(context: Any?, options: Options): List<*> =
            if (context is List<*>) context + options.params.toList() else listOf(context) + options.params.toList()
    },
    json("""{{json (map f1='1' f2=(ls '1' '2'))}}""", mapOf(), """{"f1":"1","f2":["1","2"]}""") {
        override fun invoke(context: Any?, options: Options): String =
            jackson2ObjectMapperProvider.getObjectMapper(false).writeValueAsString(
                if (options.params.isEmpty()) context else listOf(context) + options.params
            )
    },
    prettyJson("""{{prettyJson '{"a": 1}'}}""", mapOf(), "{\n  \"a\": 1\n}") {
        override fun invoke(context: Any?, options: Options): String = (context as String).prettyJson()
    },
    NULL("{{NULL}}", emptyMap(), null) {
        override fun invoke(context: Any?, options: Options): Any = Result.success(null)
    },
    exist("{{exist obj}}", mapOf("obj" to "any"), true) {
        override fun invoke(context: Any?, options: Options): Any = context != null
    },
    eval("{{eval '#var'}}", mapOf("var" to 2), 2) {
        override fun invoke(context: Any?, options: Options): Any? = options.evaluator().evaluate("$context")
    },
    resolve("{{resolve 'today is {{today}}' var='val'}}", mapOf(), "today is ${LocalDate.now().toDate()}") {
        override fun invoke(context: Any?, options: Options): Any? {
            val eval = options.evaluator()
            options.hash.forEach { (k, v) -> eval.setVariable("#$k", if (v is String) eval.resolveToObj(v) else v) }
            return eval.resolveToObj("$context")
        }
    },
    file("{{file '/hb/some-file.txt' var1='val1' var2='val2'}}", mapOf(), "today is ${LocalDate.now().toDate()}") {
        override fun invoke(context: Any?, options: Options): Any? {
            val eval = options.evaluator()
            options.hash.forEach { (k, v) -> eval.setVariable("#$k", if (v is String) eval.resolveToObj(v) else v) }
            return eval.resolveToObj(context.toString().readFile()).also {
                options.hash.forEach { (key, _) -> eval.setVariable("#$key", null) }
            }
        }
    },
    jsonPath("""{{jsonPath '{"root": {"nested": 1}}' '$.root.nested'}}""", mapOf(), 1) {
        override fun invoke(context: Any?, options: Options): Any? =
            runCatching { JsonPath.read<Any>(context.toString(), options.param(0)) }
                .recover { if (it is PathNotFoundException) null else throw it }
                .getOrThrow()
    },
    math("{{math '+' 1 2}}", mapOf(), 3.0) {
        override fun invoke(context: Any?, options: Options) = when (context.toString()) {
            "+" -> options.params.reduce { acc, next -> acc.toString().toDouble() + next.toString().toDouble() }
            "-" -> options.params.reduce { acc, next -> acc.toString().toDouble() - next.toString().toDouble() }
            "*" -> options.params.reduce { acc, next -> acc.toString().toDouble() * next.toString().toDouble() }
            "/" -> options.params.reduce { acc, next -> acc.toString().toDouble() / next.toString().toDouble() }
            else -> error("Unsupported math operation: $context. Supported: +, - , *, /.")
        }
    },
    prop("{{prop 'system.property' 'optional default'}}", mapOf(), "optional default") {
        override fun invoke(context: Any?, options: Options) = System.getProperty(context.toString(), options.param(0))
    },
    env("{{env 'env.property' 'optional default'}}", mapOf(), "optional default") {
        override fun invoke(context: Any?, options: Options) = System.getenv(context.toString()) ?: options.param(0)
    };

    override fun apply(context: Any?, options: Options): Any? {
        if (name !in setOf("vars", "file", "resolve", "map")) validate(options)
        val result = try {
            this(context, options)
        } catch (expected: Exception) {
            throw ExamHelper.InvocationFailed(name, context, options, expected)
        }
        return result
    }

    override fun toString() = describe()
    abstract operator fun invoke(context: Any?, options: Options): Any?
}
