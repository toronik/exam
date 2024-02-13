package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.ID
import io.github.adven27.concordion.extensions.exam.core.html.ONCLICK
import io.github.adven27.concordion.extensions.exam.core.html.button
import io.github.adven27.concordion.extensions.exam.core.html.italic
import org.concordion.api.Resource
import org.concordion.api.extension.ConcordionExtender
import org.concordion.api.extension.ConcordionExtension
import org.concordion.api.Element as ConcordionElement

class TocbotExtension : ConcordionExtension {
    override fun addTo(e: ConcordionExtender) {
        e.linkedCss(BASE, "tocbot.css")
        e.linkedJs(BASE, "tocbot.min.js")
        e.withEmbeddedJavaScript( // language=js
            """
            window.addEventListener('DOMContentLoaded', function (event) {
                anchors.options = {
                    placement: 'left',
                    icon: '#'
                };
                anchors.add();
                tocbot.init({
                    tocSelector: '.js-toc',
                    contentSelector: '.bd-content',
                    headingSelector: 'h1, h2, h3, h4, h5, h6',
                    hasInnerContainers: true,
                    collapseDepth: 3,
                    scrollSmooth: false,
                    fixedSidebarOffset: 'auto',
                    includeHtml: true
                });
                var collapseDepth = 3;
                jQuery( "#example-summary-badge" ).click(function() {
                    if (collapseDepth === 3) {
                        collapseDepth = 30;
                    } else {
                        collapseDepth = 3;
                    }
                    tocbot.refresh({
                        tocSelector: '.js-toc',
                        contentSelector: '.bd-content',
                        headingSelector: 'h1, h2, h3, h4, h5, h6',
                        hasInnerContainers: true,
                        collapseDepth: collapseDepth,
                        scrollSmooth: false,
                        fixedSidebarOffset: 'auto',
                        includeHtml: true
                    });
                    if (collapseDepth === 30) {
                        jQuery( ".toc-link" ).filter(function( index ) {
                            return jQuery( "span", this ).length === 0
                        }).css("display", "none");
                    } else {
                        jQuery( ".toc-link" ).filter(function( index ) {
                            return jQuery( "span", this ).length === 0
                        }).css("display", "unset");
                    }
                });
            });
            """.trimIndent()
        )
    }

    companion object {
        const val BASE = "ext/tocbot"
    }
}

class TopButtonExtension : ConcordionExtension {
    override fun addTo(e: ConcordionExtender) {
        e.withDocumentParsingListener {
            Html(ConcordionElement(it.rootElement))(
                button("", ID to "btnToTop", ONCLICK to "topFunction()")(
                    italic("").css("fa fa-arrow-up")
                )
            )
        }
    }
}

fun ConcordionExtender.linkedCss(base: String, vararg css: String) = css.forEach {
    withLinkedCSS("\\$base\\$it", Resource("\\$base\\$it"))
}

fun ConcordionExtender.linkedJs(base: String, vararg js: String) = js.forEach {
    withLinkedJavaScript("\\$base\\$it", Resource("\\$base\\$it"))
}

fun ConcordionExtender.resources(base: String, vararg resources: String) = resources.forEach {
    withResource("\\$base\\$it", Resource("\\$base\\$it"))
}
