package io.github.adven27.concordion.extensions.exam.core.fanout

import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.NS
import io.github.adven27.concordion.extensions.exam.core.handlebars.HelperMissing
import io.github.adven27.concordion.extensions.exam.core.html.addExamAttr
import nu.xom.Attribute
import nu.xom.Element

/** Css class of the `[{outline}]` marker, declared as a role: `:outline: role=e-outline`. */
const val OUTLINE = "e-outline"

/** Exam-namespaced attribute of the `[{rows}]` table, declared as `:rows: e-outline-rows=`. */
const val OUTLINE_ROWS = "outline-rows"

/** Column that only names the case, by convention read by [caseOrAllValues] and not expected in the body. */
const val CASE = "case"

/** One line of a `[{rows}]` table: column name to cell value. */
data class Row(val values: Map<String, String>) {
    operator fun get(column: String): String? = values[column]
}

/** How long a name assembled out of row values may get before it stops being a name. */
private const val MAX_NAME = 40

/**
 * Default naming of a clone: the `case` column if there is one, otherwise the row itself, cut short.
 * The name is a tab label, an example id and part of a log file name, so a six column matrix must
 * not turn into all six values.
 */
fun caseOrAllValues(row: Row): String = row[CASE] ?: row.values.values.joinToString(" / ").let {
    if (it.length <= MAX_NAME) it else it.take(MAX_NAME - 1).trimEnd() + "…"
}

/**
 * Variants of an `[{outline}]` example, taken from its `[{rows}]` table. One clone per data row,
 * named after the row, with the row values put into the body and the table itself cut out of the
 * clone - what the report has to show is the case, not the matrix it came from.
 *
 * @param nameBy how a clone is named. The name goes into the report, so make it say what the case is.
 * @param nameColumns columns that only name the case and are therefore not expected in the body.
 */
class Outline @JvmOverloads constructor(
    private val nameBy: (Row) -> String = ::caseOrAllValues,
    private val nameColumns: Set<String> = setOf(CASE)
) : FanoutSource {
    override val marker = OUTLINE
    override val notation = "[{outline}]"

    override fun fanout(block: Element, exampleName: String): Fanout {
        val table = block.tableMarked(OUTLINE_ROWS) ?: throw FanoutError.NoRowsTable(exampleName)
        val rows = rows(table, exampleName)
        val declared = rows.first().values.keys
        // Read once per block, from the body only: the table is data, and a `{nil}` cell arrives as
        // the text `{{NULL}}` - shaped exactly like a column reference.
        val referenced = referencedIn(block.textExcluding(table), declared)
        return Fanout(
            variants = rows.map { RowVariant(nameBy(it), it) },
            header = table.deepCopy().also { copy -> copy.removeAttribute(copy.getAttribute(OUTLINE_ROWS, NS)) },
            notices = listOfNotNull(
                notice(referenced - declared) { FanoutError.MissingColumns(exampleName, it) },
                notice(declared - referenced - nameColumns) { FanoutError.UnusedColumns(exampleName, it) }
            )
        )
    }

    private fun rows(table: Element, exampleName: String): List<Row> {
        val rows = table.cellRows()
        if (rows.size < 2) throw FanoutError.EmptyRows(exampleName, notation)
        val header = rows.first()
        if (header.any { it.isBlank() }) throw FanoutError.BlankColumnName(exampleName)
        // Duplicates would collapse in toMap(), the last value silently winning.
        header.duplicates().ifNotEmpty { throw FanoutError.DuplicateColumnNames(exampleName, it) }
        return rows.drop(1).mapIndexed { i, cells ->
            // zip() would truncate to the shorter list, dropping data without a word.
            if (cells.size != header.size) {
                throw FanoutError.RowSizeMismatch(exampleName, notation, i + 1, header.size, cells.size)
            }
            Row(header.zip(cells).toMap())
        }
    }

    private class RowVariant(override val name: String, private val row: Row) : Variant {
        override fun applyTo(clone: Element) {
            clone.tableMarked(OUTLINE_ROWS)?.detach()
            clone.substitute(row)
            clone.setVariables(row)
        }
    }
}

/**
 * `{{column}}` in the body of a clone, replaced by the value of the row it belongs to. This is what
 * makes the report show the case rather than the template, which is the whole point of an outline:
 * a failure has to be readable without going back to the matrix.
 *
 * One pass over the original text, not a `replace` per column: a value that itself contains
 * `{{other}}` must not be substituted again.
 */
internal fun Element.substitute(row: Row) {
    if (row.values.isEmpty()) return
    val placeholder = Regex("\\{\\{(" + row.values.keys.joinToString("|") { Regex.escape(it) } + ")}}")
    query(".//text()").textNodes().forEach { node ->
        placeholder.replace(node.value) { row.values.getValue(it.groupValues[1]) }
            .takeIf { it != node.value }
            ?.let { node.value = it }
    }
}

/**
 * The same row values as spec variables, so that handlebars resolves `{{column}}` at run time too.
 * Substitution reaches the text of the clone; it does not reach content the clone pulls in later,
 * as `{{file '...'}}` does. Hidden, or the values show up in the report as stray text.
 */
internal fun Element.setVariables(row: Row) {
    insertChild(
        Element("div").apply {
            addAttribute(Attribute(CLASS, "hide"))
            row.values.forEach { (column, cell) ->
                appendChild(
                    Element("span").apply {
                        addExamAttr("set", column)
                        appendChild(cell)
                    }
                )
            }
        },
        0
    )
}

private val examHelpers: Set<String> by lazy { HelperMissing.helpersDesc().values.flatMap { it.keys }.toSet() }

/**
 * `\w` in java is ascii only, so a cyrillic column reference would not be seen at all: a missing
 * column would pass validation and a used one would be reported unused.
 */
private val PLACEHOLDER = Regex("\\{\\{([\\p{L}\\p{N}_]+)}}")

/**
 * Column references in a body. Exam sentinels (`{nil}` -> `{{NULL}}`, `{any}` -> `{{ignore}}`, ...)
 * are shaped like column references but are handlebars helpers - unless a column of that name is
 * actually declared, in which case the reference is to the column.
 */
private fun referencedIn(body: String, declared: Set<String>) = PLACEHOLDER.findAll(body)
    .map { it.groupValues[1] }
    .filter { it in declared || it !in examHelpers }
    .toSet()

private inline fun notice(names: Set<String>, error: (Set<String>) -> FanoutError) =
    names.takeIf { it.isNotEmpty() }?.let { error(it).message }
