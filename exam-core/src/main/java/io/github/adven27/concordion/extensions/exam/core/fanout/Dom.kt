package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import nu.xom.Attribute
import nu.xom.Document
import nu.xom.Element
import nu.xom.Node
import nu.xom.Nodes
import nu.xom.Text
import org.concordion.internal.ConcordionBuilder.NAMESPACE_CONCORDION_2007

internal const val EXAMPLE_BLOCK = "exampleblock"
internal const val EXAMPLE = "example"
internal const val CLASS = "class"

internal fun Nodes.elements(): List<Element> = (0 until size()).map { this[it] as Element }

internal fun Nodes.textNodes(): List<Text> = (0 until size()).mapNotNull { this[it] as? Text }

internal fun Element.classes(): Set<String> =
    getAttributeValue(CLASS).orEmpty().split(" ").filter { it.isNotBlank() }.toSet()

internal fun Element.classes(classes: Collection<String>) = addAttribute(Attribute(CLASS, classes.joinToString(" ")))

/**
 * Marked example blocks, by the css class the marker lands as. A marker declared as a role
 * (`:outline: role=e-outline`) is the only channel that reaches an example block: `e-`-prefixed
 * attributes are propagated to tables, source and open blocks only.
 */
internal fun Document.exampleBlocksMarked(marker: String): List<Element> =
    elementsMarked(query("//div[contains(@class, '$marker')]"), marker).filter { EXAMPLE_BLOCK in it.classes() }

internal fun Element.descendantsMarked(marker: String): List<Element> =
    elementsMarked(query(".//*[contains(@class, '$marker')]"), marker)

private fun elementsMarked(found: Nodes, marker: String) = found.elements().filter { marker in it.classes() }

/**
 * The human readable example name. By the time a [org.concordion.api.listener.DocumentParsingListener]
 * sees the document, the `div.title` of an asciidoc example block is gone: `ConcordionPostprocessor`
 * has already moved it into the `c:example` attribute. Null means the block is not a concordion
 * example at all - it had no title.
 */
internal fun Element.exampleName(): String? =
    getAttributeValue(EXAMPLE, NAMESPACE_CONCORDION_2007)?.trim()?.takeIf { it.isNotBlank() }

internal fun Element.exampleName(name: String) {
    getAttribute(EXAMPLE, NAMESPACE_CONCORDION_2007)!!.value = name
}

internal fun Element.tableMarked(marker: String): Element? =
    query(".//table").elements().firstOrNull { it.getAttributeValue(marker, ExamExtension.NS) != null }

internal fun Element.deepCopy(): Element = copy() as Element

internal fun Element.detach() = parent?.let { (it as Element).removeChild(this) }

/**
 * Text of the whole subtree except [excluded]. Placeholders are looked for in the body of a block,
 * and the variants table is data, not body: a `{nil}` cell arrives as the text `{{NULL}}`, which is
 * shaped exactly like a column reference.
 */
internal fun Element.textExcluding(excluded: Element?): String = if (excluded == null) {
    value
} else {
    query(".//text()").textNodes().filterNot { excluded.isAncestorOf(it) }.joinToString("") { it.value }
}

private fun Element.isAncestorOf(node: Node): Boolean {
    var parent = node.parent
    while (parent != null) {
        if (parent === this) return true
        parent = parent.parent
    }
    return false
}

/** Something to point at in an error message about a block that has no name to be called by. */
internal fun Element.textSnippet(maxLength: Int = 60): String =
    value.trim().replace(Regex("\\s+"), " ").take(maxLength)
