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

    class EmptyRows(example: String) : FanoutError(
        "The [{rows}] table of example \"$example\" has a header but not a single data row"
    )

    class MissingColumns(example: String, names: Set<String>) : FanoutError(
        "The body of example \"$example\" refers to values missing from [{rows}]: ${names.listed()}"
    )

    /** Not thrown: rendered into the report as a warning, since a matrix column nothing reads is a smell, not a break. */
    class UnusedColumns(example: String, names: Set<String>) : FanoutError(
        "The [{rows}] columns of example \"$example\" are not used by its body: ${names.listed()}"
    )

    class RowSizeMismatch(example: String, row: Int, expected: Int, actual: Int) : FanoutError(
        "Data row #$row of the [{rows}] table of example \"$example\" has $actual cells" +
            " instead of $expected, as in the header"
    )

    class BlankColumnName(example: String) : FanoutError(
        "The [{rows}] header of example \"$example\" has a column with a blank name"
    )

    class UnknownPerturbation(example: String, name: String, available: Set<String>) : FanoutError(
        "Example \"$example\" is asked to be stable under \"$name\", which is not a perturbation" +
            " Exam knows: ${available.listed()}"
    )

    /** Not thrown: a perturbation that has nothing to do is a case that proves nothing, and silence about it is worse. */
    class NothingToPerturb(example: String, name: String, deliveries: Int) : FanoutError(
        "Example \"$example\" has $deliveries deliveries to perturb, so its \"$name\" case verifies" +
            " nothing the unperturbed example does not"
    )

    class DuplicateColumnNames(example: String, names: Set<String>) : FanoutError(
        "The [{rows}] header of example \"$example\" has duplicate column names: ${names.listed()}"
    )
}

private fun Set<String>.listed() = sorted().joinToString(", ")
