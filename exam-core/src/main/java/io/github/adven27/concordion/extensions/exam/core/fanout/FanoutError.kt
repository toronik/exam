package io.github.adven27.concordion.extensions.exam.core.fanout

/**
 * Every way a fanout marker can be wrong. Silently doing nothing is not an option: a marker that is
 * ignored gives a green run that verified less than the spec says, which is worse than a red one.
 */
sealed class FanoutError(message: String) : IllegalStateException(message) {

    /**
     * An untitled block never becomes a concordion example - `ConcordionPostprocessor` has nothing to
     * put into `c:example` - so there is nothing to clone. Reported with the beginning of the block
     * text, since there is no name to refer to.
     */
    class MissingTitle(notation: String, hint: String) : FanoutError(
        "A $notation block has no title, so Exam does not turn it into an example and it cannot be" +
            " fanned out" + (if (hint.isBlank()) "" else "; block starts with: \"$hint\"")
    )

    class Nested(notation: String, example: String) : FanoutError(
        "Example \"$example\" has a nested $notation, which is not supported"
    )

    class NoVariants(notation: String, example: String) : FanoutError(
        "Example \"$example\" is marked $notation but there is nothing to fan it out into"
    )

    class NoRowsTable(example: String) : FanoutError(
        "Example \"$example\" is marked [{outline}] but has no [{rows}] table"
    )

    class EmptyRows(example: String, notation: String) : FanoutError(
        "The $notation table of example \"$example\" has a header but not a single data row"
    )

    class ManyMarkers(example: String, notations: List<String>) : FanoutError(
        "Example \"$example\" carries more than one fanout marker (${notations.joinToString(", ")})," +
            " which is not supported: each would fan the example out inside the cases of the other"
    )

    /**
     * Not thrown: `{{name}}` is also how a spec reads a variable off the evaluator, and those are set
     * anywhere - in a `[{before}]`, in an earlier example, by a fixture. Refusing the document over
     * one would refuse specs that are perfectly correct, and a name that is a real typo still fails
     * loudly at run time, through `helperMissing`.
     */
    class MissingColumns(example: String, names: Set<String>) : FanoutError(
        "The body of example \"$example\" uses ${names.listed()}, which is not a column of [{rows}]:" +
            " left to be resolved as a spec variable at run time"
    )

    /** Not thrown: rendered into the report as a warning, since a matrix column nothing reads is a smell, not a break. */
    class UnusedColumns(example: String, names: Set<String>) : FanoutError(
        "The [{rows}] columns of example \"$example\" are not used by its body: ${names.listed()}"
    )

    class RowSizeMismatch(example: String, notation: String, row: Int, expected: Int, actual: Int) : FanoutError(
        "Data row #$row of the $notation table of example \"$example\" has ${count(actual, "cell", "cells")}" +
            " instead of $expected, as in the header"
    )

    class BlankColumnName(example: String) : FanoutError(
        "The [{rows}] header of example \"$example\" has a column with a blank name"
    )

    class UnknownPerturbation(example: String, unrecognized: Set<String>, available: Set<String>) : FanoutError(
        "Example \"$example\" asks to be stable under ${unrecognized.listed()}," +
            " which Exam does not know. Known: ${available.listed()}"
    )

    class NoPerturbations(example: String, available: Set<String>) : FanoutError(
        "Example \"$example\" is marked [{stable-under}] but names no perturbation, inline or in a" +
            " [{perturbations}] table. Known: ${available.listed()}"
    )

    /**
     * Rather than a precedence rule nobody would remember: two lists of perturbations for one example
     * is a question about which of them runs, and a spec is not the place for that question.
     */
    class TwoWaysToPerturb(example: String) : FanoutError(
        "Example \"$example\" names perturbations both in its marker and in a [{perturbations}] table." +
            " Use one or the other"
    )

    class BlankPerturbation(example: String) : FanoutError(
        "The [{perturbations}] table of example \"$example\" has a row with no perturbation in it"
    )

    class DuplicatePerturbations(example: String, names: Set<String>) : FanoutError(
        "The [{perturbations}] table of example \"$example\" names ${names.listed()} more than once"
    )

    /**
     * The first row of a table is its header here as it is in [{rows}]. A perturbation name standing
     * in it is a header nobody wrote, and dropping it would silently lose the case it stands for.
     */
    class MissingPerturbationsHeader(example: String, first: String) : FanoutError(
        "The first row of the [{perturbations}] table of example \"$example\" is its header, and" +
            " \"$first\" is a perturbation. Give the table a header row"
    )

    /**
     * The shape is deliberately a table rather than a list, so that a perturbation can one day be
     * given arguments - `duplicate` three times, `delay` by thirty seconds. Until it can, a spec
     * asking for them is told so instead of having them dropped.
     */
    class UnsupportedArguments(example: String, name: String, arguments: List<String>) : FanoutError(
        "The [{perturbations}] table of example \"$example\" passes ${arguments.joinToString(", ")} to" +
            " \"$name\", and arguments to a perturbation are not supported yet"
    )

    class NothingToPerturb(example: String, name: String, deliveries: Int) : FanoutError(
        "Example \"$example\" has ${count(deliveries, "delivery", "deliveries")} for \"$name\" to perturb," +
            " so that case would verify nothing the example already verifies"
    )

    class DuplicateColumnNames(example: String, names: Set<String>) : FanoutError(
        "The [{rows}] header of example \"$example\" has duplicate column names: ${names.listed()}"
    )
}

private fun Set<String>.listed() = sorted().joinToString(", ")

private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
