@file:Suppress("TooManyFunctions")

package io.github.adven27.concordion.extensions.exam.core

import ch.qos.logback.classic.turbo.TurboFilter
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.ValueResolver
import com.github.jknack.handlebars.context.JavaBeanValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.context.MethodValueResolver
import io.github.adven27.concordion.extensions.exam.core.handlebars.EvaluatorValueResolver
import io.github.adven27.concordion.extensions.exam.core.handlebars.HANDLEBARS
import io.github.adven27.concordion.extensions.exam.core.json.DefaultObjectMapperProvider
import io.github.adven27.concordion.extensions.exam.core.logger.LoggerLevelFilter
import io.github.adven27.concordion.extensions.exam.core.logger.LoggingFormatterExtension
import io.github.adven27.concordion.extensions.exam.core.utils.After
import io.github.adven27.concordion.extensions.exam.core.utils.Before
import io.github.adven27.concordion.extensions.exam.core.utils.DateFormatMatcher
import io.github.adven27.concordion.extensions.exam.core.utils.DateFormattedAndWithinDate
import io.github.adven27.concordion.extensions.exam.core.utils.DateFormattedAndWithinNow
import io.github.adven27.concordion.extensions.exam.core.utils.DateWithin
import io.github.adven27.concordion.extensions.exam.core.utils.XMLDateWithin
import io.github.adven27.concordion.extensions.exam.core.utils.uuid
import net.javacrumbs.jsonunit.JsonAssert.`when`
import net.javacrumbs.jsonunit.core.Configuration
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS
import net.javacrumbs.jsonunit.providers.Jackson2ObjectMapperProvider
import org.concordion.api.extension.ConcordionExtender
import org.concordion.api.extension.ConcordionExtension
import org.concordion.api.listener.ExampleEvent
import org.hamcrest.Matcher
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors.byName
import org.xmlunit.diff.ElementSelectors.byNameAndText
import org.xmlunit.diff.NodeMatcher
import java.util.function.Consumer

class ExamExtension(private vararg var plugins: ExamPlugin) : ConcordionExtension {
    private var focusOnError: Boolean = true
    private var enableLoggingFormatterExtension: Boolean = true
    private var nodeMatcher: NodeMatcher = defaultNodeMatcher
    private var skipDecider: SkipDecider = SkipDecider.NoSkip()

    /**
     * Attach xmlunit/jsonunit matchers.
     *
     * @param matcherName name to reference in placeholder.
     * @param matcher     implementation.
     * usage: {{matcherName param1 param2}}
     */
    @Suppress("unused")
    fun withPlaceholderMatcher(matcherName: String, matcher: Matcher<*>): ExamExtension {
        MATCHERS[matcherName] = matcher
        return this
    }

    @Suppress("unused")
    fun withXmlUnitNodeMatcher(nodeMatcher: NodeMatcher): ExamExtension {
        this.nodeMatcher = nodeMatcher
        return this
    }

    @Suppress("unused")
    fun withJackson2ObjectMapperProvider(provider: Jackson2ObjectMapperProvider): ExamExtension {
        jackson2ObjectMapperProvider = provider
        return this
    }

    @Suppress("unused")
    fun withHandlebarResolvers(vararg resolvers: ValueResolver): ExamExtension {
        handlebarResolvers = resolvers.toList().toTypedArray()
        return this
    }

    @Suppress("unused")
    fun withHandlebar(fn: Consumer<Handlebars>): ExamExtension {
        fn.accept(HANDLEBARS)
        return this
    }

    @Suppress("unused")
    fun withVerifiers(vararg overrideVerifiers: Pair<String, ContentVerifier>): ExamExtension {
        CONTENT_VERIFIERS += overrideVerifiers.toMap()
        return this
    }

    @Suppress("unused")
    fun withHelpers(vararg helperSources: Any): ExamExtension {
        withHandlebar { hb -> helperSources.forEach { hb.registerHelpers(it) } }
        return this
    }

    /**
     * All examples but failed will be collapsed
     */
    @Suppress("unused")
    fun withFocusOnFailed(enabled: Boolean): ExamExtension {
        focusOnError = enabled
        return this
    }

    @Suppress("unused")
    fun enableLoggingFormatterExtension(enabled: Boolean): ExamExtension {
        enableLoggingFormatterExtension = enabled
        return this
    }

    @Suppress("unused")
    fun runOnlyExamplesWithPathsContains(vararg substrings: String): ExamExtension {
        skipDecider = object : SkipDecider {
            var reason = ""
            override fun test(event: ExampleEvent): Boolean {
                val skips = mutableListOf<String>()
                val name = event.resultSummary.specificationDescription
                val noSkip = substrings.none { substring ->
                    if (name.contains(substring)) {
                        true
                    } else {
                        skips += substring
                        false
                    }
                }
                if (noSkip) {
                    reason = "specification name: \"$name\" not contains: $skips \n"
                }
                return noSkip
            }

            override fun reason(): String = reason
        }
        return this
    }

    @Suppress("unused")
    fun withSkipExampleDecider(decider: SkipDecider): ExamExtension {
        skipDecider = decider
        return this
    }

    @Suppress("unused")
    fun withLoggingFilter(loggerLevel: Map<String, String>): ExamExtension {
        loggingFilter = LoggerLevelFilter(loggerLevel)
        return this
    }

    override fun addTo(ex: ConcordionExtender) {
        val registry = CommandRegistry()
        plugins.forEach { registry.register(it.commands()) }

        registry.commands().filter { "example" != it.key }.forEach { ex.withCommand(NS, it.key, it.value) }

        TopButtonExtension().addTo(ex)
//        TocbotExtension().addTo(ex)
        if (enableLoggingFormatterExtension) {
            LoggingFormatterExtension().addTo(ex)
        }
//        ex.withThrowableListener(ErrorListener())
        if (focusOnError) {
            ex.withSpecificationProcessingListener(FocusOnErrorsListener())
        }
        ex.withExampleListener(ExamExampleListener(skipDecider))
        ex.withDocumentParsingListener(ExamDocumentParsingListener(registry))
    }

    fun setUp() {
        plugins.forEach { it.setUp() }
    }

    fun tearDown() {
        plugins.forEach { it.tearDown() }
    }

    companion object {
        val PARSED_COMMANDS: MutableMap<String, String> = HashMap()
        const val NS = "http://exam.extension.io"

        val MATCHERS: MutableMap<String, Matcher<*>> = mutableMapOf(
            "formattedAs" to DateFormatMatcher(),
            "formattedAndWithin" to DateFormattedAndWithinDate(),
            "formattedAndWithinNow" to DateFormattedAndWithinNow(),
            "xmlDateWithinNow" to XMLDateWithin(),
            "within" to DateWithin(),
            "uuid" to uuid(),
            "after" to After(),
            "before" to Before()
        )

        @JvmField
        var handlebarResolvers = arrayOf<ValueResolver>(
            EvaluatorValueResolver.INSTANCE,
            JavaBeanValueResolver.INSTANCE,
            MethodValueResolver.INSTANCE,
            MapValueResolver.INSTANCE
        )

        @JvmField
        var jackson2ObjectMapperProvider: Jackson2ObjectMapperProvider = DefaultObjectMapperProvider()

        @JvmField
        val defaultNodeMatcher = DefaultNodeMatcher(byNameAndText, byName)

        @JvmField
        val defaultJsonUnitCfg: Configuration = `when`(IGNORING_ARRAY_ORDER).let { cfg ->
            MATCHERS.map { cfg to it }
                .reduce { acc, cur ->
                    acc.first
                        .withMatcher(acc.second.key, acc.second.value)
                        .withMatcher(cur.second.key, cur.second.value) to cur.second
                }.first
        }

        const val VERIFIER_JSON = "json"
        const val VERIFIER_JSON_IGNORE_EXTRA_FIELDS = "jsonIgnoreExtraFields"
        const val VERIFIER_JSON_ARRAY_ORDERED = "jsonArrayOrdered"
        const val VERIFIER_XML = "xml"
        const val VERIFIER_TEXT = "text"

        val CONTENT_VERIFIERS: MutableMap<String, ContentVerifier> = mutableMapOf(
            VERIFIER_JSON to JsonVerifier(),
            VERIFIER_JSON_IGNORE_EXTRA_FIELDS to JsonVerifier { it.withOptions(IGNORING_EXTRA_FIELDS) },
            VERIFIER_JSON_ARRAY_ORDERED to JsonVerifier {
                it.withOptions(it.options.apply { remove(IGNORING_ARRAY_ORDER) })
            },
            VERIFIER_XML to XmlVerifier(),
            VERIFIER_TEXT to ContentVerifier.Default("text")
        )

        @JvmStatic
        fun contentVerifier(verifier: String): ContentVerifier = checkNotNull(CONTENT_VERIFIERS[verifier]) {
            "Content verifier '$verifier' not found. Provide it via ExamExtension(...).withVerifiers(...)"
        }

        var loggingFilter: TurboFilter = LoggerLevelFilter()
    }
}
