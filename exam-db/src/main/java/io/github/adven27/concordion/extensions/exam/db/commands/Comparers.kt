package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier
import io.github.adven27.concordion.extensions.exam.core.ContentVerifier.Companion.setActualIfNeeded
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.contentVerifier
import io.github.adven27.concordion.extensions.exam.core.html.rootCauseMessage
import io.github.adven27.concordion.extensions.exam.core.utils.toLocalDateTime
import io.github.adven27.concordion.extensions.exam.db.RowComparator
import org.concordion.api.Evaluator
import org.dbunit.assertion.comparer.value.IsActualEqualToExpectedValueComparer
import org.dbunit.dataset.ITable
import org.dbunit.dataset.SortedTable
import org.dbunit.dataset.datatype.DataType
import java.sql.Timestamp
import java.util.Date

open class ExamMatchersAwareValueComparer : IsActualEqualToExpectedValueComparer() {
    protected lateinit var evaluator: Evaluator
    protected var error = ""

    fun setEvaluator(evaluator: Evaluator): ExamMatchersAwareValueComparer {
        this.evaluator = evaluator
        return this
    }

    override fun isExpected(
        expectedTable: ITable?,
        actualTable: ITable?,
        rowNum: Int,
        columnName: String?,
        dataType: DataType,
        expected: Any?,
        actual: Any?
    ): Boolean {
        error = ""
        return when {
            expected.isError() -> false
            expected.isMatcher() -> setActualIfNeeded(expected as String, actual, evaluator).let {
                ContentVerifier.matcher(it, "\${test-unit.").matches(actual)
            }

            actual is Content && expected is Content -> verify(actual, expected.body)
            actual is Content && expected is String -> verify(actual, expected)

            else -> super.isExpected(expectedTable, actualTable, rowNum, columnName, dataType, expected, actual)
        }
    }

    private fun verify(actual: Content, expected: String) =
        contentVerifier(actual.type).verify(expected, actual.body, evaluator)
            .onFailure { error = " because:\n" + it.rootCauseMessage() }
            .isSuccess

    override fun makeFailMessage(expectedValue: Any?, actualValue: Any?): String =
        super.makeFailMessage(expectedValue, actualValue) + error

    companion object {
        @JvmField
        var ERROR_MARKER = "ERROR RETRIEVING VALUE: "

        fun Any?.isError() = this != null && toString().startsWith(ERROR_MARKER)
        fun Any?.isMatcher() = this is String && startsWith("\${test-unit.")
    }
}

/**
 * Base class for default comparer overriding
 * @see IgnoreMillisComparer
 */
abstract class AbstractFallbackComparer : ExamMatchersAwareValueComparer() {
    override fun isExpected(
        expectedTable: ITable?,
        actualTable: ITable?,
        rowNum: Int,
        columnName: String?,
        dataType: DataType,
        expected: Any?,
        actual: Any?
    ): Boolean = if (super.isExpected(expectedTable, actualTable, rowNum, columnName, dataType, expected, actual)) {
        true
    } else {
        compare(expected, actual)
    }

    abstract fun compare(expected: Any?, actual: Any?): Boolean
}

class IgnoreMillisComparer : AbstractFallbackComparer() {
    override fun compare(expected: Any?, actual: Any?): Boolean {
        val expectedDt = (expected as Date).toLocalDateTime().withNano(0)
        val actualDt = (actual as Timestamp).toLocalDateTime()
        return expectedDt.isEqual(actualDt) || expectedDt.plusSeconds(1).isEqual(actualDt)
    }
}

fun sortedTable(table: ITable, columns: Array<String>, rowComparator: RowComparator) =
    SortedTable(table, columns).apply {
        setUseComparable(true)
        setRowComparator(rowComparator.init(table, columns))
    }

open class VerifierColumnComparer(private val verifier: String) : ExamMatchersAwareValueComparer() {
    override fun isExpected(
        expectedTable: ITable?,
        actualTable: ITable?,
        rowNum: Int,
        columnName: String?,
        dataType: DataType,
        expected: Any?,
        actual: Any?
    ) = contentVerifier(verifier).verify(expected.toString(), actual.toString(), evaluator)
        .onFailure { error = " because:\n" + it.rootCauseMessage() }
        .onSuccess { error = "" }
        .isSuccess
}

@Suppress("unused")
class JsonColumnComparer : VerifierColumnComparer("json")
