package io.github.adven27.concordion.extensions.exam.core

import mu.KLogging
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Attributes.FONT_ICONS
import org.asciidoctor.AttributesBuilder
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.time.measureTimedValue

open class AdocExtension : ConcordionExtension {
    companion object : KLogging() {
        const val BASE = "ext/ascii"
        const val BASE_BS = "ext/bootstrap"
        private const val SPECS_ADOC_BASE_DIR = "SPECS_ADOC_BASE_DIR"
        private const val SPECS_ADOC_VERSION = "SPECS_ADOC_VERSION"
        val ADOC_BASE_DIR = File(System.getProperty(SPECS_ADOC_BASE_DIR, "./src/test/resources/specs"))
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
        val CACHE: MutableMap<String, ByteArrayInputStream> = mutableMapOf()

        fun copyGeneratedDiagramImagesToReports(baseDir: File) {
            File(baseDir.path + File.separator + "img").takeIf { it.exists() }?.let { img ->
                val output = File(getBaseOutputDir().path + File.separator + "specs/img")
                output.takeUnless { it.exists() }?.let { Files.createDirectories(it.toPath()) }
                img.listFiles()?.forEach {
                    Files.move(
                        it.toPath(),
                        File(output.path + File.separator + it.name).toPath(),
                        REPLACE_EXISTING
                    )
                }
                Files.deleteIfExists(img.toPath())
            }
        }
    }

    protected open fun configureAttributesBuilder(b: AttributesBuilder): AttributesBuilder = b

    override fun addTo(ex: ConcordionExtender) {
        addStyles(ex)
        ex.withSpecificationType("adoc") { i, n ->
            measureTimedValue {
                CACHE.getOrPut(n) {
                    ByteArrayInputStream(ADOC.convert(InputStreamReader(i).readText(), options()).toByteArray())
                        .also { copyGeneratedDiagramImagesToReports(ADOC_BASE_DIR) }
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

    private fun options(): Options? = Options.builder()
        .standalone(true)
        .baseDir(ADOC_BASE_DIR)
        .safe(UNSAFE)
        .backend("xhtml5")
        .attributes(
            Attributes.builder()
                .noFooter(true)
                .imagesDir("img")
                .sourceHighlighter("highlight.js")
                .showTitle(true)
                .tableOfContents(true)
                .tableOfContents(LEFT)
                .icons(FONT_ICONS)
                .attributes(
                    mapOf(
                        "toclevels" to "4",
                        "diagram-cachedir" to getBaseOutputDir().path,
                        "version" to ADOC_VERSION
                    )
                )
                .apply { configureAttributesBuilder(this) }
                .build()
        )
        .build()

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
