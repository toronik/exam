package io.github.adven27.concordion.extensions.exam.core

import mu.KLogging
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Attributes.FONT_ICONS
import org.asciidoctor.Options
import org.asciidoctor.Placement.LEFT
import org.asciidoctor.SafeMode.UNSAFE
import org.asciidoctor.ast.Document
import org.asciidoctor.extension.IncludeProcessor
import org.asciidoctor.extension.LocationType
import org.asciidoctor.extension.PreprocessorReader
import org.asciidoctor.syntaxhighlighter.SyntaxHighlighterAdapter
import org.concordion.api.extension.ConcordionExtender
import org.concordion.api.extension.ConcordionExtension
import org.concordion.internal.ConcordionBuilder.getBaseOutputDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.time.measureTimedValue

open class AdocExtension : ConcordionExtension {
    companion object : KLogging() {
        const val BASE = "ext/ascii"
        const val BASE_BS = "ext/bootstrap"
        const val BASE_CM = "ext/codemirror"
        const val BASE_FA = "ext/fontawesome"
        private const val SPECS_ADOC_RESOURCES_DIR = "SPECS_ADOC_RESOURCES_DIR"
        private const val SPECS_ADOC_BASE_DIR = "SPECS_ADOC_BASE_DIR"
        private const val SPECS_ADOC_VERSION = "SPECS_ADOC_VERSION"
        private const val SPECS_TOC_LEVELS = "SPECS_TOC_LEVELS"
        val RESOURCES_DIR: String = System.getProperty(SPECS_ADOC_RESOURCES_DIR, "./src/test/resources")
        val BASE_DIR: String = System.getProperty(SPECS_ADOC_BASE_DIR, "/specs")
        val ADOC_VERSION: String = System.getProperty(SPECS_ADOC_VERSION, "<Set system property $SPECS_ADOC_VERSION>")
        val TOC_LEVELS: String = System.getProperty(SPECS_TOC_LEVELS, "4")
        val ADOC: Asciidoctor = Asciidoctor.Factory.create().apply {
            System.setProperty("jruby.compat.version", "RUBY1_9")
            System.setProperty("jruby.compile.mode", "OFF")
            requireLibrary("asciidoctor-diagram")
            syntaxHighlighterRegistry().register(HighlightJs::class.java, "highlight.js")
            javaExtensionRegistry()
                .includeProcessor(VarsAwareIncludeProcessor::class.java)
                .postprocessor(ConcordionPostprocessor::class.java)
                .treeprocessor(ExamTreeProcessor())
        }
        val ADOC_OPTS: Options = Options.builder()
            .standalone(true)
            .baseDir(File("$RESOURCES_DIR$BASE_DIR"))
            .safe(UNSAFE)
            .backend("xhtml5")
            .attributes(
                Attributes.builder()
                    .noFooter(true)
                    .sourceHighlighter("highlight.js")
                    .showTitle(true)
                    .tableOfContents(true)
                    .tableOfContents(LEFT)
                    .icons(FONT_ICONS)
                    .attributes(
                        mapOf(
                            "table-caption!" to "",
                            "toclevels" to TOC_LEVELS,
                            "diagram-cachedir" to getBaseOutputDir().path,
                            "imagesoutdir" to getBaseOutputDir().path + BASE_DIR,
                            "version" to ADOC_VERSION
                        )
                    )
                    .build()
            )
            .build()
        val CACHE: MutableMap<String, ByteArray> = mutableMapOf()
    }

    override fun addTo(ex: ConcordionExtender) {
        addStyles(ex)
        ex.withSpecificationType("adoc") { i, n ->
            measureTimedValue {
                ByteArrayInputStream(CACHE.getOrPut(n) { ADOC.convert(text(i), ADOC_OPTS).toByteArray() })
            }.also { logger.info { "$n converted in " + it.duration } }.value
        }
    }

    private fun text(i: InputStream) =
        (ExamExtension::class.java.classLoader.getResource(".asciidoctorconfig")?.readText() ?: "") +
            System.lineSeparator() +
            ExamExtension::class.java.getResource("/specs/.asciidoctorconfig")?.path?.let { "include::$it[]" } +
            System.lineSeparator() +
            InputStreamReader(i).readText()

    private fun addStyles(ex: ConcordionExtender) {
        ex.linkedCss(BASE_CM, "enable-codemirror.css")
        ex.linkedJs(BASE_CM, "cm6.bundle.min.js", "enable-codemirror.js")

        ex.linkedCss(BASE_BS, "bootstrap.min.css", "enable-bootstrap.css", "scrollToTop.css")
        ex.linkedJs(BASE_BS, "bootstrap.bundle.min.js", "jquery-3.2.1.slim.min.js", "scrollToTop.js")

        ex.linkedCss(
            BASE_FA,
            "css/all.min.css",
            "css/regular.min.css",
            "css/solid.min.css",
            "css/all.min.css"
        )
        ex.resources(
            BASE_FA,
            "webfonts/fa-regular-400.woff2",
            "webfonts/fa-regular-400.eot",
            "webfonts/fa-regular-400.svg",
            "webfonts/fa-regular-400.ttf",
            "webfonts/fa-regular-400.woff",
            "webfonts/fa-solid-900.woff2",
            "webfonts/fa-solid-900.eot",
            "webfonts/fa-solid-900.svg",
            "webfonts/fa-solid-900.ttf",
            "webfonts/fa-solid-900.woff"
        )

        ex.linkedCss(
            BASE,
            "css/site.css",
            "css/font.css",
            "css/font-awesome.css",
            "css/tune.css"
        )
        ex.resources(
            BASE,
            "highlight/highlightjs-copy.min.js",
            "highlight/highlight.min.js",
            "highlight/languages/java.min.js",
            "highlight/languages/kotlin.min.js",
            "highlight/languages/asciidoc.min.js",
            "highlight/languages/handlebars.min.js",
            "highlight/languages/sql.min.js",
            "highlight/languages/pgsql.min.js",
            "highlight/languages/json.min.js",
            "highlight/languages/xml.min.js",
            "highlight/languages/http.min.js",
            "highlight/styles/stackoverflow-light.min.css",
            "highlight/styles/stackoverflow-dark.min.css",
            "highlight/styles/highlightjs-copy.min.css",
            "fonts/fontawesome-webfont.eot",
            "fonts/fontawesome-webfont.svg",
            "fonts/fontawesome-webfont.ttf",
            "fonts/fontawesome-webfont.woff",
            "fonts/fontawesome-webfont.woff2"
        )
    }

    protected open fun attrs(): Map<String, String> = mapOf()

    class HighlightJs : SyntaxHighlighterAdapter {
        override fun hasDocInfo(location: LocationType) = location == LocationType.FOOTER
        override fun getDocinfo(location: LocationType, document: Document, options: Map<String, Any>) =
            // language=html
            """
            <link rel="stylesheet" href="../ext/ascii/highlight/styles/stackoverflow-light.min.css"/>
            <link rel="stylesheet" href="../ext/ascii/highlight/styles/highlightjs-copy.min.css"/>
            <script src="../ext/ascii/highlight/highlight.min.js">.</script>
            <script src="../ext/ascii/highlight/highlightjs-copy.min.js">.</script>
            <script>
            window.addEventListener('DOMContentLoaded', function (event) {
                jQuery(".details").wrap(unescape("&lt;details&gt;&lt;/details&gt;"));
                jQuery(".pre").wrap(unescape("&lt;pre&gt;&lt;/pre&gt;"));
                jQuery(".table").wrap(unescape("&lt;div class='table-responsive'&gt;&lt;/div&gt;"));
                hljs.configure({cssSelector: 'pre code[data-lang]:not([data-lang="json"], [data-lang="xml"]), pre code[class^="language-"], pre code[class*=" language-"], pre.code' });
                hljs.addPlugin(new CopyButtonPlugin());
                hljs.highlightAll();
                let fail = $(".failure").get(0);
                if(fail) fail.scrollIntoView();
                enableCM();
            });
            </script>
            """.trimIndent()
    }

    class VarsAwareIncludeProcessor : IncludeProcessor() {
        private val nativeOptions = setOf("leveloffset", "lines", "tag", "tags", "indent", "encoding", "opts")
        override fun handles(target: String): Boolean = target.endsWith("/")

        override fun process(
            document: Document,
            reader: PreprocessorReader,
            target: String,
            attributes: Map<String, Any>
        ) {
            val f = File("${document.attributes["docdir"]!!}/$target")
            val resDir = "/src/test/resources"
            val vars = attributes
                .filterKeys { it !in nativeOptions }
                .map { (k, v) -> "$k=${if (v.toString().startsWith("(")) v else "'$v'"}" }
                .joinToString(" ", prefix = " ")
            reader.pushInclude(
                "{{file '${f.absolutePath.substringAfter(resDir)}'$vars}}",
                target,
                f.absolutePath,
                1,
                attributes.filterKeys { it in nativeOptions }
            )
        }
    }
}
