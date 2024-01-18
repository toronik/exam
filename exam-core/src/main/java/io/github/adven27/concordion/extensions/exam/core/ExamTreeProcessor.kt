package io.github.adven27.concordion.extensions.exam.core

import mu.KLogging
import org.asciidoctor.ast.Cell
import org.asciidoctor.ast.Document
import org.asciidoctor.ast.StructuralNode
import org.asciidoctor.ast.Table
import org.asciidoctor.extension.Treeprocessor
import kotlin.collections.List
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.system.measureTimeMillis
import org.asciidoctor.ast.List as AstList

open class ExamTreeProcessor : Treeprocessor() {
    companion object : KLogging() {
        private val OPTS: Map<Any, Any> = mutableMapOf("subs" to ":attributes")
    }

    override fun process(document: Document) = document.also {
        measureTimeMillis { processNode(it) }.let { logger.info { "Processed ${document.doctitle} in $it" } }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun processNode(node: StructuralNode) {
        val blocks = node.blocks
        for (i in blocks.indices) {
            val b = blocks[i]
            when {
                b.style == "source" -> propagateToAttrs(b, sourceBlock())?.let { blocks[i] = it }
                b.style == "e-execute" -> propagateToAttrs(b, executeBlock())?.let { blocks[i] = it }
                b.style == "open" -> processNode(b).also { propagateToAttrs(b, openBlock())?.let { blocks[i] = it } }
                b is AstList -> propagateToAttrs(b, listBlock())?.let { blocks[i] = it }
                b.blocks.isNotEmpty() -> processNode(b)
                b is Table -> b.body.flatMap { it.cells }.map { c -> (c as Cell).innerDocument?.let(::processNode) }
                    .also { propagateToAttrs(b, tableBlock())?.let { blocks[i] = it } }
            }
        }
    }

    private fun listBlock() = { n: StructuralNode, opts: List<String> ->
        n.convert().replace("<li>\n<p>", "<li><p>").replace("</p>\n</li>", "</p></li>")
            .replaceFirst("<ul", opts.joinToString(" ", prefix = "<ul ", postfix = " "))
            .replaceFirst("<ol", opts.joinToString(" ", prefix = "<ol ", postfix = " "))
    }

    private fun openBlock() = { n: StructuralNode, opts: List<String> ->
        n.convert().replaceFirst("<div class=\"content\"", opts.joinToString(" ", prefix = "<div class=\"content\" "))
    }

    private fun sourceBlock() = { n: StructuralNode, opts: List<String> ->
        val precode = "<pre class=\"highlight.js highlight\"><code"
        n.convert().let { html ->
            html.substringAfter("$precode data-lang=\"")
                .substringBefore("\"")
                .takeIf { it.isNotBlank() }
                ?.let { it.takeUnless { it == "httprequest" } ?: "http" }
                ?.let { html.replaceFirst(precode, "$precode class='language-$it'") }
            html.replaceFirst(precode, opts.joinToString(" ", prefix = "$precode "))
        }
    }

    private fun executeBlock() = { n: StructuralNode, opts: List<String> ->
        n.convert().replaceFirst("<table ", opts.joinToString(" ", prefix = "<table ", postfix = " "))
    }

    private fun tableBlock() = { n: StructuralNode, opts: List<String> ->
        n.convert().replaceFirst("<table ", opts.joinToString(" ", prefix = "<table ", postfix = " "))
    }

    private fun propagateToAttrs(n: StructuralNode, propagate: (n: StructuralNode, opts: List<String>) -> String) =
        opts(n)?.let { createBlock(n.parent as StructuralNode, "pass", propagate(n, it), n.parent.attributes, OPTS) }

    private fun opts(n: StructuralNode) = n.attributes
        .filterKeys { it.startsWith("e-") || it.startsWith("c-") }
        .mapKeys { it.key.replaceFirst("e-", "e:").replaceFirst("c-", "c:") }
        .takeIf { it.isNotEmpty() }
        ?.let { it.map { (k, v) -> "$k=\"${v ?: ""}\"" } }
}
