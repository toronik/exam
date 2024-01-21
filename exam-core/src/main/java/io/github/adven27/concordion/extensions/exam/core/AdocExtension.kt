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
import java.io.InputStreamReader
import kotlin.time.measureTimedValue

open class AdocExtension : ConcordionExtension {
    companion object : KLogging() {
        const val BASE = "ext/ascii"
        const val BASE_BS = "ext/bootstrap"
        private const val SPECS_ADOC_RESOURCES_DIR = "SPECS_ADOC_RESOURCES_DIR"
        private const val SPECS_ADOC_BASE_DIR = "SPECS_ADOC_BASE_DIR"
        private const val SPECS_ADOC_VERSION = "SPECS_ADOC_VERSION"
        val RESOURCES_DIR: String = System.getProperty(SPECS_ADOC_RESOURCES_DIR, "./src/test/resources")
        val BASE_DIR: String = System.getProperty(SPECS_ADOC_BASE_DIR, "/specs")
        val ADOC_VERSION: String = System.getProperty(SPECS_ADOC_VERSION, "<Set system property $SPECS_ADOC_VERSION>")
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
                            "toclevels" to "4",
                            "diagram-cachedir" to getBaseOutputDir().path,
                            "imagesoutdir" to getBaseOutputDir().path + BASE_DIR,
                            "version" to ADOC_VERSION
                        )
                    )
                    .build()
            )
            .build()
        val CACHE: MutableMap<String, ByteArrayInputStream> = mutableMapOf()
    }

    override fun addTo(ex: ConcordionExtender) {
        addStyles(ex)
        ex.withSpecificationType("adoc") { i, n ->
            measureTimedValue {
                CACHE.getOrPut(n) {
                    ByteArrayInputStream(ADOC.convert(InputStreamReader(i).readText(), ADOC_OPTS).toByteArray())
                }
            }.let {
                logger.info { "$n converted in " + it.duration }
                it.value
            }
        }
    }

    private fun addStyles(ex: ConcordionExtender) {
        ex.linkedCss(BASE_BS, "bootstrap.min.css", "enable-bootstrap.css", "doc.min.css", "scrollToTop.css")
        ex.linkedJs(
            BASE_BS,
            "bootstrap.bundle.min.js",
            "jquery-3.2.1.slim.min.js",
            "sidebar.js",
            "doc.min.js",
            "scrollToTop.js"
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
            <link rel="stylesheet" href="../ext/ascii/highlight/styles/stackoverflow-light.min.css">.</link>
            <link rel="stylesheet" href="../ext/ascii/highlight/styles/highlightjs-copy.min.css">.</link>
            <script src="../ext/ascii/highlight/highlight.min.js">.</script>
            <script src="../ext/ascii/highlight/highlightjs-copy.min.js">.</script>
            <script>
            window.addEventListener('DOMContentLoaded', function (event) {
                jQuery(".details").wrap(unescape("&lt;details&gt;&lt;/details&gt;"));
                jQuery(".pre").wrap(unescape("&lt;pre&gt;&lt;/pre&gt;"));
                jQuery("table").wrap(unescape("&lt;div class='table-responsive'&gt;&lt;/div&gt;"));
                enableCM();
                hljs.configure({cssSelector: 'pre code[data-lang], pre code[class^="language-"], pre code[class*=" language-"], pre.code' });
                hljs.addPlugin(new CopyButtonPlugin());
                hljs.highlightAll();
                let fail = $(".failure").get(0);
                if(fail) fail.scrollIntoView();
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
                .map { (k, v) -> "$k=$v" }
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
