package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.firstByClass
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.badge
import nu.xom.Element
import org.concordion.api.extension.ConcordionExtender
import org.concordion.api.extension.ConcordionExtension
import org.concordion.api.listener.SpecificationProcessingEvent
import org.concordion.api.listener.SpecificationProcessingListener
import java.util.UUID

internal const val GROUP = "exam-fanout"
internal const val GROUP_NAME = "exam-fanout-name"
internal const val GROUP_HEADER = "exam-fanout-header"
internal const val GROUP_FLATTEN = "exam-fanout-flatten"
internal const val TAB = "nav-link"
internal const val PANE = "tab-pane"
internal const val ACTIVE = "active"
internal const val EXPECTED_TO_FAIL = "ExpectedToFail"

/**
 * The clones of one block, as one thing in the report.
 *
 * A row of independent cards is what fanout renders without this: on ten rows, ten full copies of
 * the same body one after another, no map of what passed and what failed, and the matrix of variants
 * nowhere - it is cut out of every clone. A reader of an outline needs the opposite three things:
 * the template once, the outcomes of all cases at a glance, and the body of the case that failed,
 * with its own values in it.
 *
 * So: the matrix goes into the header of the group, and the bodies become tabs. The tab strip is the
 * map of outcomes - a badge per case - and only one body is on screen, filled in with the values of
 * the case selected. Ten more rows add ten buttons, not ten screens.
 */
internal class VariantGroup(name: String, header: Element?, notices: List<String>) {
    private val id = "e${UUID.randomUUID()}"
    private val tabs = el("ul", CLASS to "nav nav-tabs", "role" to "tablist")
    private val panes = el("div", CLASS to "tab-content")
    private var cases = 0

    val element: Element = el("div", CLASS to "$GROUP card mb-3")(
        el("div", CLASS to "card-header")(
            el("span", CLASS to "$GROUP_NAME fw-semibold").text(name),
            el("button", CLASS to "btn btn-sm btn-link $GROUP_FLATTEN", "type" to "button").text("all cases at once"),
            header?.let { if (GROUP_HEADER in it.classes()) it else el("div", CLASS to GROUP_HEADER)(it) },
            *notices.map { el("div", CLASS to "alert alert-warning").text(it) }.toTypedArray()
        ),
        tabs,
        panes
    )

    /**
     * The first case is made active while the document is parsed, not after the run: a report whose
     * javascript never ran, or whose run died before the listener, still shows a body.
     */
    fun add(label: String, clone: Element) {
        val pane = "$id-${cases++}"
        val first = cases == 1
        tabs(
            el("li", CLASS to "nav-item", "role" to "presentation")(
                el(
                    "button",
                    CLASS to if (first) "$TAB $ACTIVE" else TAB,
                    "data-bs-toggle" to "tab",
                    "data-bs-target" to "#$pane",
                    "type" to "button",
                    "role" to "tab"
                ).text(label)
            )
        )
        panes(el("div", CLASS to if (first) "$PANE $ACTIVE" else PANE, "id" to pane, "role" to "tabpanel")(clone))
    }
}

/**
 * What the group can only be told once the run is over: which case failed.
 *
 * Puts a verdict badge on every tab, opens the first case that needs attention (the first case if
 * none does), and states the outcome of the group as a whole - a clone is a concordion example, so
 * the run summary counts an outline of ten rows as ten unrelated examples and nothing says they were
 * one scenario.
 */
class VariantGroupListener : SpecificationProcessingListener {
    override fun beforeProcessingSpecification(event: SpecificationProcessingEvent) = Unit

    override fun afterProcessingSpecification(event: SpecificationProcessingEvent) =
        Html(event.rootElement).descendants("div").filter { it.hasClass(GROUP) }.forEach { summarize(it) }

    private fun summarize(group: Html) {
        val cases = group.descendants("button").filter { it.hasClass(TAB) }
            .zip(group.descendants("div").filter { it.hasClass(PANE) })
            .map { (tab, pane) -> Case(tab, pane) }
        if (cases.isEmpty()) return

        cases.forEach { it.report() }
        (cases.firstOrNull { it.needsAttention() } ?: cases.first()).activate(cases)
        group.attrs(*summary(cases))
        group.descendants("span").firstByClass(GROUP_NAME)?.parent()?.invoke(verdict(cases))
    }

    private fun summary(cases: List<Case>) = arrayOf(
        "data-summary-cases" to cases.size.toString(),
        "data-summary-success" to cases.sumOf { it.count("success") }.toString(),
        "data-summary-ignore" to cases.sumOf { it.count("ignore") }.toString(),
        "data-summary-failure" to cases.sumOf { it.count("failure") }.toString(),
        "data-summary-exception" to cases.sumOf { it.count("exception") }.toString()
    )

    private fun verdict(cases: List<Case>) = cases.count { it.needsAttention() }.let {
        if (it == 0) {
            badge("${cases.size} cases passed", "success")
        } else {
            badge("$it of ${cases.size} cases failed", "danger")
        }
    }

    private class Case(val tab: Html, val pane: Html) {
        private val example: Html? = pane.childs().firstOrNull { it.attr("data-type") == "example" }

        fun count(of: String) = example?.attr("data-summary-$of")?.toLongOrNull() ?: 0

        private fun status() = example?.attr("data-summary-status")

        /**
         * An expected-to-fail case that failed is doing what the spec says; one that passed is not.
         */
        fun needsAttention() = if (status() == EXPECTED_TO_FAIL) {
            count("failure") + count("exception") == 0L
        } else {
            count("failure") + count("exception") > 0
        }

        fun report() {
            tab(
                when {
                    needsAttention() -> badge("!", "danger")
                    status() == EXPECTED_TO_FAIL -> badge(EXPECTED_TO_FAIL, "warning")
                    else -> badge("✓", "success")
                }
            )
            // Inside a group the tab is what shows and hides a body, so the collapse of the example
            // itself is left without an affordance instead of pretending to be one.
            example?.childs()?.firstByClass("title")
                ?.takeIf { it.attr("data-bs-toggle") != null }
                ?.el?.removeAttribute("data-bs-toggle")
        }

        fun activate(all: List<Case>) {
            all.forEach {
                it.tab.removeClass(ACTIVE)
                it.pane.removeClass(ACTIVE)
            }
            tab.css(ACTIVE)
            pane.css(ACTIVE)
        }
    }
}

/**
 * Everything the group needs from the report itself: the listener that can only run after the run,
 * and the two things html cannot do on its own - flattening a group for search and print, and
 * following a link straight to the case that failed.
 */
internal class VariantGroupExtension : ConcordionExtension {
    override fun addTo(e: ConcordionExtender) {
        e.withSpecificationProcessingListener(VariantGroupListener())
        e.withEmbeddedJavaScript(JS)
    }

    companion object {
        // language=js
        private val JS = """
            window.addEventListener('DOMContentLoaded', function () {
                document.querySelectorAll('.$GROUP_FLATTEN').forEach(function (button) {
                    button.addEventListener('click', function () {
                        var group = button.closest('.$GROUP');
                        var flat = group.classList.toggle('exam-flat');
                        button.textContent = flat ? 'one case at a time' : 'all cases at once';
                    });
                });
                var open = function (hash) {
                    if (!hash) return;
                    var target = document.getElementById(decodeURIComponent(hash.substring(1)));
                    var pane = target && target.closest('.$PANE');
                    if (!pane) return;
                    var tab = document.querySelector('[data-bs-target="#' + pane.id + '"]');
                    if (tab && window.bootstrap) bootstrap.Tab.getOrCreateInstance(tab).show();
                    target.scrollIntoView();
                };
                open(window.location.hash);
                window.addEventListener('hashchange', function () { open(window.location.hash); });
            });
        """.trimIndent()
    }
}
