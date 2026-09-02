package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.NS
import nu.xom.Element
import org.concordion.api.ImplementationStatus

/** Css class of the `[{stable-under}]` marker, declared as a role: `:stable-under: role=e-stable-under`. */
const val STABLE_UNDER = "e-stable-under"

/** Css class of the `[{stable-except}]` marker, declared as a role: `:stable-except: role=e-stable-except`. */
const val STABLE_EXCEPT = "e-stable-except"

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
    perturbations: List<Perturbation> = listOf(Duplicate, Reorder),
    private val deliveries: Set<String> = DELIVERIES
) : FanoutSource {
    private val available = perturbations.associateBy { it.name }

    override val marker = STABLE_UNDER
    override val notation = "[{stable-under}]"

    override fun fanout(block: Element, exampleName: String): Fanout {
        val asked = block.classes() - marker - EXAMPLE_BLOCK - STATUSES
        asked.firstOrNull { it !in available }
            ?.let { throw FanoutError.UnknownPerturbation(exampleName, it, available.keys) }
        val found = block.deliveries(deliveries).size
        return Fanout(
            variants = asked.map { Perturbed(available.getValue(it), deliveries) },
            header = el("div", CLASS to GROUP_HEADER).text("stable under: ${asked.joinToString(", ")}"),
            notices = asked.map { available.getValue(it) }
                .filterNot { it.applies(found) }
                .map { FanoutError.NothingToPerturb(exampleName, it.name, found).message!! }
        )
    }

    private class Perturbed(private val perturbation: Perturbation, private val deliveries: Set<String>) : Variant {
        override val name = perturbation.name

        override fun applyTo(clone: Element) {
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
