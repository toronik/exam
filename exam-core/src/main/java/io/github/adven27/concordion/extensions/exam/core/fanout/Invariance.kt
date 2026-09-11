package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.NS
import nu.xom.Element
import org.concordion.api.ImplementationStatus

/** Css class of the `[{stable-under}]` marker, declared as a role: `:stable-under: role=e-stable-under`. */
const val STABLE_UNDER = "e-stable-under"

/** Css class of the `[{stable-except}]` marker, declared as a role: `:stable-except: role=e-stable-except`. */
const val STABLE_EXCEPT = "e-stable-except"

/** Exam-namespaced attribute of the `[{perturbations}]` table, declared as `:perturbations: e-perturbations=`. */
const val PERTURBATIONS = "perturbations"

/** Commands that deliver something to the system, as opposed to setting it up or checking it. */
val DELIVERIES = setOf("mq-set", "execute", "http")

/**
 * Something that can happen to a delivery and must not change the outcome.
 *
 * A perturbation rewrites the actions of a case while the document is parsed, which is the same
 * moment the case itself is made. Nothing is wrapped and nothing is intercepted at run time, so the
 * report shows what was done to the case - the doubled step is there to read - and a perturbation
 * needs to know nothing about queues, brokers or http clients.
 */
interface Perturbation {
    /** The name as it is written in the marker. */
    val name: String

    /** What was done to this case. The reader of a failure has to know what it is being blamed on. */
    val effect: String

    /** Rewrites the deliveries of one case, in document order. */
    fun perturb(deliveries: List<Element>)

    /** Whether this perturbation has anything to do to a case with this many deliveries. */
    fun applies(deliveries: Int): Boolean = deliveries > 0
}

/** Every delivery of the case is performed twice: at-least-once delivery, stated as a property. */
object Duplicate : Perturbation {
    override val name = "duplicate"
    override val effect = "every delivery of this case was performed twice"

    override fun perturb(deliveries: List<Element>) = deliveries.forEach {
        val parent = it.parent as Element
        parent.insertChild(it.deepCopy(), parent.indexOf(it) + 1)
    }
}

/**
 * The deliveries of the case are performed in the opposite order. Reversal rather than a shuffle of
 * some seed: the strongest reordering there is, and reproducible without anyone having to be told a
 * seed to reproduce it.
 */
object Reorder : Perturbation {
    override val name = "reorder"
    override val effect = "the deliveries of this case were performed in the opposite order"

    override fun applies(deliveries: Int) = deliveries > 1

    override fun perturb(deliveries: List<Element>) = deliveries.groupBy { it.parent }
        .filterValues { it.size > 1 }
        .forEach { (parent, siblings) ->
            val at = siblings.map { (parent as Element).indexOf(it) }.sorted()
            siblings.sortedByDescending { (parent as Element).indexOf(it) }
                .forEach { (parent as Element).removeChild(it) }
            at.zip(siblings.reversed()).forEach { (i, e) -> (parent as Element).insertChild(e, i) }
        }
}

/**
 * Variants of a `[{stable-under}]` example: the same body under each of the perturbations named in
 * the marker.
 *
 * This is not the same idea as an outline. An outline says "these cases are different"; invariance
 * says "this outcome does not depend on how the deliveries arrived" - a property, and one that
 * cannot be stated in Exam at all today, which is why authors duplicate whole fixtures by hand. The
 * cases it produces are the same body, so a green run means nothing unless something also proves
 * that a body which is *not* invariant goes red: see `core-invariance.adoc`.
 *
 * @param perturbations what a marker may name.
 * @param deliveries the commands a perturbation is allowed to touch. Seeding a database is not a
 * delivery, and a check is replayed as it stands - what is being questioned is the delivery, not the
 * expectation.
 */
class Invariance @JvmOverloads constructor(
    private val perturbations: List<Perturbation> = listOf(Duplicate, Reorder),
    private val deliveries: Set<String> = DELIVERIES
) : FanoutSource {
    private val available = perturbations.associateBy { it.name }

    override val marker = STABLE_UNDER
    override val notation = "[{stable-under}]"

    override fun fanout(block: Element, exampleName: String): Fanout {
        val table = block.tableMarked(PERTURBATIONS)
        // Known names only: a block may legitimately carry a styling role, and calling that an
        // unknown perturbation would refuse a document over a css class.
        val named = block.classes().filter { it in available }
        if (table != null && named.isNotEmpty()) throw FanoutError.TwoWaysToPerturb(exampleName)

        val asked = if (table == null) inline(block, named, exampleName) else fromTable(table, exampleName)
        val found = block.deliveries(deliveries).size
        // An unused column is a smell; a perturbation with nothing to perturb is an impossible
        // request. The case would come out green having verified nothing, which is the shape this
        // whole feature refuses to render.
        asked.map { available.getValue(it) }
            .firstOrNull { !it.applies(found) }
            ?.let { throw FanoutError.NothingToPerturb(exampleName, it.name, found) }
        return Fanout(
            variants = asked.map { Perturbed(available.getValue(it), deliveries) },
            // The table itself is what the group shows, exactly as an outline shows its matrix; the
            // inline form has nothing to show but the names.
            header = table?.deepCopy()?.also { it.removeAttribute(it.getAttribute(PERTURBATIONS, NS)) }
                ?: el("div", CLASS to GROUP_HEADER).text("stable under: ${asked.joinToString(", ")}"),
            consumed = if (table == null) asked.toSet() else emptySet()
        )
    }

    private fun inline(block: Element, named: List<String>, exampleName: String): List<String> {
        if (named.isNotEmpty()) return named
        val unrecognized = block.classes() - marker - EXAMPLE_BLOCK - STATUSES
        throw if (unrecognized.isEmpty()) {
            FanoutError.NoPerturbations(exampleName, available.keys)
        } else {
            FanoutError.UnknownPerturbation(exampleName, unrecognized, available.keys)
        }
    }

    /**
     * The same shape an outline reads its cases from: a header row, then one row per perturbation.
     * The name is the first column, and a row is allowed to be wider than that so arguments have
     * somewhere to go the day they exist - until then a spec asking for them is told so.
     */
    private fun fromTable(table: Element, exampleName: String): List<String> {
        val rows = table.cellRows()
        if (rows.size < 2) throw FanoutError.EmptyRows(exampleName, "[{perturbations}]")
        val header = rows.first()
        header.firstOrNull { it in available }
            ?.let { throw FanoutError.MissingPerturbationsHeader(exampleName, it) }
        rows.drop(1).forEachIndexed { i, cells ->
            if (cells.size != header.size) {
                throw FanoutError.RowSizeMismatch(exampleName, "[{perturbations}]", i + 1, header.size, cells.size)
            }
            if (cells.size > 1) {
                throw FanoutError.UnsupportedArguments(exampleName, cells.first(), cells.drop(1))
            }
        }
        val named = rows.drop(1).map { it.first() }
        if (named.any { it.isBlank() }) throw FanoutError.BlankPerturbation(exampleName)
        (named.toSet() - available.keys).ifNotEmpty {
            throw FanoutError.UnknownPerturbation(exampleName, it, available.keys)
        }
        named.duplicates().ifNotEmpty { throw FanoutError.DuplicatePerturbations(exampleName, it) }
        return named
    }

    /** The same, plus what a project knows how to do to its own system. */
    fun and(vararg extra: Perturbation) = Invariance(perturbations + extra, deliveries)

    private class Perturbed(private val perturbation: Perturbation, private val deliveries: Set<String>) : Variant {
        override val name = perturbation.name

        override fun applyTo(clone: Element) {
            // The list of perturbations belongs to the group, not to each of its cases - the same
            // reason an outline cuts its matrix out of a clone.
            clone.tableMarked(PERTURBATIONS)?.detach()
            perturbation.perturb(clone.deliveries(deliveries))
            clone.insertChild(el("div", CLASS to "alert alert-info").text(perturbation.effect), 0)
        }
    }
}

/**
 * Status tags arrive as roles too, so a marker may carry one: `[{stable-under} duplicate
 * ExpectedToFail]` is how a case that is expected to break under duplication is written.
 */
private val STATUSES = ImplementationStatus.entries.map { it.tag }.toSet()

/**
 * Deliveries of a case, in document order, minus anything under a `[{stable-except}]`: an at-least-once
 * side effect is sometimes honest - a metric counted twice is counted twice - and a spec has to be
 * able to say so instead of being wrong about it.
 */
internal fun Element.deliveries(commands: Set<String>): List<Element> = query(".//*").elements()
    .filter { element -> commands.any { element.getAttributeValue(it, NS) != null } }
    .filterNot { it.exempt() }

private fun Element.exempt(): Boolean {
    var element: Element? = this
    while (element != null) {
        if (STABLE_EXCEPT in element.classes()) return true
        element = element.parent as? Element
    }
    return false
}
