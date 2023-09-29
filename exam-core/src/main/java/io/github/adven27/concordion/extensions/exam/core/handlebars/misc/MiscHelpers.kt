package io.github.adven27.concordion.extensions.exam.core.handlebars.misc

import com.github.jknack.handlebars.Options
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.handlebars.evaluator
import io.github.adven27.concordion.extensions.exam.core.prettyJson
import io.github.adven27.concordion.extensions.exam.core.readFile
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.toDate
import java.time.LocalDate

/* ktlint-disable enum-entry-name-case */
@Suppress("EnumNaming", "EnumEntryName")
enum class MiscHelpers(
    override val example: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val expected: Any? = "",
    override val options: Map<String, String> = emptyMap()
) : ExamHelper {
    set("""{{set 1 "someVar"}}""", mapOf(), 1) {
        override fun invoke(context: Any?, options: Options): Any? = options.params.map {
            options.evaluator().setVariable("#$it", context)
        }.let { context }
    },
    getOr("""{{getOr var "default value"}}""", mapOf(), "default value") {
        override fun invoke(context: Any?, options: Options): Any? = context ?: options.param<String>(0)
    },
    map("""{{map key='value'}}""", mapOf(), mapOf("key" to "value")) {
        override fun invoke(context: Any?, options: Options): Map<*, *> =
            if (context is Map<*, *>) context + options.hash else options.hash
    },
    ls("""{{ls '1' '2'}}""", mapOf(), listOf("1", "2")) {
        override fun invoke(context: Any?, options: Options): List<*> =
            if (context is List<*>) context + options.params.toList() else listOf(context) + options.params.toList()
    },
    json("""{{json (map f1='1' f2=(ls '1' '2'))}}""", mapOf(), "1") {
        override fun invoke(context: Any?, options: Options): String =
            ExamExtension.JACKSON_2_OBJECT_MAPPER_PROVIDER.getObjectMapper(false).writeValueAsString(
                if (options.params.isEmpty()) context else listOf(context) + options.params
            ).prettyJson()
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
            val evaluator = options.evaluator()
            options.hash.forEach { (key, value) ->
                evaluator.setVariable("#$key", evaluator.resolveToObj(value as String?))
            }
            return evaluator.resolveToObj("$context")
        }
    },
    file("{{file '/hb/some-file.txt' var='val'}}", mapOf(), "today is ${LocalDate.now().toDate()}") {
        override fun invoke(context: Any?, options: Options): Any? {
            val evaluator = options.evaluator()
            options.hash.forEach { (key, value) ->
                evaluator.setVariable("#$key", evaluator.resolveToObj(value as String?))
            }
            return evaluator.resolveToObj(context.toString().readFile())
        }
    },
    prop("{{prop 'system.property' 'optional default'}}", mapOf(), "optional default") {
        override fun invoke(context: Any?, options: Options) = System.getProperty(context.toString(), options.param(0))
    },
    env("{{env 'env.property' 'optional default'}}", mapOf(), "optional default") {
        override fun invoke(context: Any?, options: Options) = System.getenv(context.toString()) ?: options.param(0)
    };

    override fun apply(context: Any?, options: Options): Any? {
        if (name !in setOf("file", "resolve", "map")) validate(options)
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
