package io.github.adven27.concordion.extensions.exam.core.fanout

import nu.xom.Document
import nu.xom.Element
import org.concordion.api.listener.DocumentParsingListener

/**
 * One clone to be produced from a marked example block.
 */
interface Variant {
    /** Tells the clone apart in the report; appended to the name of the block it came from. */
    val name: String

    /** Specializes the clone - substitutes row values, marks a perturbation, whatever "variant" means to the source. */
    fun applyTo(clone: Element)
}

/**
 * What a [FanoutSource] made of one marked block: the clones to produce, what tells the cases apart
 * (shown in the header of the group, since it is cut out of the clones themselves) and anything the
 * reader of the report should be told about the markup that produced them.
 */
data class Fanout(
    val variants: List<Variant>,
    val matrix: Element? = null,
    val notices: List<String> = emptyList()
)

/**
 * Turns one marked example block into the variants it stands for. Outline reads them off a table of
 * data; another source may read them off a list of perturbations. The cloning itself is the same.
 */
interface FanoutSource {
    /** The css class the marker of this source lands as. */
    val marker: String

    /** How the marker is written in a spec. For error messages only. */
    val notation: String

    fun fanout(block: Element, exampleName: String): Fanout
}

/**
 * Scenario outline for Exam specs: a marked example block plus a source of variants becomes one
 * concordion example per variant.
 *
 * Cloning is a DOM operation done while the document is being parsed, before any command runs, so
 * every clone is a first class example: its own status in the report, its own `[{before}]`,
 * `@BeforeExample`, `ExpectedToFail` and `withFocusOnFailed`. Nothing has to be done about the
 * `ResultRecorder` and nothing depends on the order extensions are registered in - by this point
 * `ConcordionPostprocessor` (an asciidoctor postprocessor, a whole stage earlier) has already
 * turned titled blocks into examples.
 */
class ExampleFanout(private val sources: List<FanoutSource>) : DocumentParsingListener {
    constructor(vararg sources: FanoutSource) : this(sources.toList())

    override fun beforeParsing(document: Document) = sources.forEach { source ->
        document.exampleBlocksMarked(source.marker).forEach { fanOut(it, source) }
    }

    private fun fanOut(block: Element, source: FanoutSource) {
        val name = block.exampleName() ?: throw FanoutError.MissingTitle(source.notation, block.textSnippet())
        if (block.descendantsMarked(source.marker).isNotEmpty()) throw FanoutError.Nested(source.notation, name)

        val (variants, matrix, notices) = source.fanout(block, name)
        if (variants.isEmpty()) throw FanoutError.NoVariants(source.notation, name)

        val group = VariantGroup(name, matrix, notices)
        val taken = mutableSetOf<String>()
        variants.forEach { variant ->
            group.add(variant.name, block.cloneFor(variant, source.marker, name, taken))
        }
        val parent = block.parent as Element
        parent.insertChild(group.element, parent.indexOf(block))
        parent.removeChild(block)
    }

    private fun Element.cloneFor(variant: Variant, marker: String, name: String, taken: MutableSet<String>) =
        deepCopy().apply {
            classes(classes() - marker)
            variant.applyTo(this)
            // A c:example collision breaks concordion, which addresses examples by name, so names
            // assembled from variants that happen to coincide are disambiguated.
            exampleName(unique("$name — ${variant.name}", taken).also { taken += it })
        }

    private fun unique(candidate: String, taken: Set<String>): String {
        if (candidate !in taken) return candidate
        var suffix = 2
        while ("$candidate #$suffix" in taken) suffix++
        return "$candidate #$suffix"
    }
}
