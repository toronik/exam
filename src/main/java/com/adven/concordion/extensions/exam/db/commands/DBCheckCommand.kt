package com.adven.concordion.extensions.exam.db.commands

import com.adven.concordion.extensions.exam.core.html.*
import com.adven.concordion.extensions.exam.core.resolveToObj
import com.adven.concordion.extensions.exam.core.utils.parsePeriod
import com.adven.concordion.extensions.exam.db.DbPlugin
import com.adven.concordion.extensions.exam.db.DbResultRenderer
import com.adven.concordion.extensions.exam.db.DbTester
import com.adven.concordion.extensions.exam.db.DbUnitConfig
import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.AssertEqualsListener
import org.concordion.api.listener.AssertFailureEvent
import org.concordion.api.listener.AssertSuccessEvent
import org.concordion.internal.util.Announcer
import org.dbunit.assertion.DbComparisonFailure
import org.dbunit.assertion.DbUnitAssert
import org.dbunit.assertion.Difference
import org.dbunit.assertion.comparer.value.IsActualEqualToExpectedValueComparer
import org.dbunit.assertion.comparer.value.IsActualWithinToleranceOfExpectedTimestampValueComparer
import org.dbunit.dataset.ITable
import org.dbunit.dataset.SortedTable
import org.dbunit.dataset.datatype.DataType
import org.dbunit.util.QualifiedTableName
import org.joda.time.LocalDateTime
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DBCheckCommand(
    name: String,
    tag: String,
    dbTester: DbTester,
    valuePrinter: DbPlugin.ValuePrinter,
    private val dbUnitConfig: DbUnitConfig
) : DBCommand(name, tag, dbTester, valuePrinter) {
    private val listeners = Announcer.to(AssertEqualsListener::class.java)

    private val actualTable: ITable
        get() {
            val conn = dbTester.executors[ds]!!.connection
            val qualifiedName = QualifiedTableName(expectedTable.tableName(), conn.schema).qualifiedName
            val where = if (where.isNullOrEmpty()) "" else "WHERE $where"
            return conn.createQueryTable(qualifiedName, "select * from $qualifiedName $where")
        }

    init {
        listeners.addListener(DbResultRenderer())
    }

    private fun failure(resultRecorder: ResultRecorder, html: Html, actual: Any?, expected: String): Html {
        resultRecorder.record(FAILURE)
        listeners.announce().failureReported(AssertFailureEvent(html.el, expected, actual))
        return html
    }

    private fun success(resultRecorder: ResultRecorder, html: Html): Html {
        resultRecorder.record(SUCCESS)
        listeners.announce().successReported(AssertSuccessEvent(html.el))
        return html
    }

    override fun verify(cmd: CommandCall?, evaluator: Evaluator?, resultRecorder: ResultRecorder?) {
        dbUnitConfig.valueComparer.setEvaluator(evaluator!!)
        dbUnitConfig.columnValueComparers.forEach { it.value.setEvaluator(evaluator) }
        assertEq(cmd.html(), resultRecorder)
    }

    private fun assertEq(rootEl: Html, resultRecorder: ResultRecorder?) {
        var root = rootEl
        val columns: Array<String> = if (orderBy.isEmpty()) expectedTable.columnNamesArray() else orderBy
        val expected = sortedTable(expectedTable, columns)
        lateinit var actual: ITable
        val atMostSec = root.takeAwayAttr("awaitAtMostSec")
        val pollDelay = root.takeAwayAttr("awaitPollDelayMillis")
        val pollInterval = root.takeAwayAttr("awaitPollIntervalMillis")
        try {
            if (atMostSec != null || pollDelay != null || pollInterval != null) {
                val atMost = atMostSec?.toLong() ?: 4
                val delay = pollDelay?.toLong() ?: 0
                val interval = pollInterval?.toLong() ?: 1000
                try {
                    Awaitility.await("Await DB table ${expected.tableName()}")
                        .atMost(atMost, TimeUnit.SECONDS)
                        .pollDelay(delay, TimeUnit.MILLISECONDS)
                        .pollInterval(interval, TimeUnit.MILLISECONDS)
                        .untilAsserted {
                            actual = actualTable.withColumnsAsIn(expectedTable)
                            dbUnitConfig.diffFailureHandler.diffList.clear()
                            dbUnitAssert(expected, actual, columns)
                            if (dbUnitConfig.diffFailureHandler.diffList.isNotEmpty()) {
                                throw AssertionError()
                            }
                        }
                } catch (f: ConditionTimeoutException) {
                    root(pre(
                        "DB check with poll delay $delay ms and poll interval $interval ms didn't complete within $atMost seconds:"
                    ).css("alert alert-danger small"))
                    if (f.cause is DbComparisonFailure) {
                        throw f.cause as DbComparisonFailure
                    }
                }
            } else {
                actual = actualTable.withColumnsAsIn(expectedTable)
                dbUnitAssert(expected, actual, columns)
            }
        } catch (f: DbComparisonFailure) {
            //TODO move to ResultRenderer
            resultRecorder!!.record(FAILURE)
            val div = div().css("rest-failure bd-callout bd-callout-danger")(div(f.message))
            root.below(div)

            val exp = tableSlim()
            div(span("Expected: "), exp)
            root = exp

            val act = tableSlim()
            renderTable(act, actual)
            div(span("but was: "), act)
        } finally {
            checkResult(root, expected, sortedTable(actual, columns), dbUnitConfig.diffFailureHandler.diffList as List<Difference>, resultRecorder!!)
        }
    }

    private fun dbUnitAssert(expected: SortedTable, actual: ITable, columns: Array<String>) {
        DbUnitAssert().assertWithValueComparer(
            expected,
            sortedTable(actual, columns),
            dbUnitConfig.diffFailureHandler,
            dbUnitConfig.valueComparer,
            dbUnitConfig.columnValueComparers
        )
    }

    private fun sortedTable(iTable: ITable, columns: Array<String>) = SortedTable(iTable, columns).apply {
        setUseComparable(true)
        dbUnitConfig.overrideRowSortingComparer?.let { setRowComparator(it) }
    }

    private fun checkResult(root: Html, expected: ITable, actual: ITable, diffs: List<Difference>, resultRecorder: ResultRecorder) {
        val cols = expected.columnNames()
        root(
            tableCaption(root.attr("caption"), expected.tableName()),
            thead()(
                tr()(
                    cols.map { th(it) }
                )))
        root(
            if (expected.rowCount == 0) {
                listOf(
                    tr()(
                        td("<EMPTY>").attrs("colspan" to "${cols.size}").markAsSuccess(resultRecorder)
                    )
                )
            } else {
                (0 until expected.rowCount).map { row ->
                    tr()(
                        cols.map {
                            val expectedValue = valuePrinter.print(expected[row, it])
                            td().apply {
                                diffs.firstOrNull { diff ->
                                    diff.rowIndex == row && diff.columnName == it
                                }?.markAsFailure(resultRecorder, this)
                                    ?: markAsSuccess(resultRecorder)(
                                        Html(valuePrinter.wrap(expected[row, it])).text(
                                            if (isDbMatcher(expectedValue) && actual.rowCount == expected.rowCount)
                                                " (${actual[row, it]})"
                                            else
                                                ""
                                        )
                                    )
                            }
                        })
                }
            })
    }

    private fun Html.markAsSuccess(resultRecorder: ResultRecorder) = success(resultRecorder, this)
    private fun Difference.markAsFailure(resultRecorder: ResultRecorder, td: Html): Html {
        return failure(resultRecorder, td, this.actualValue, valuePrinter.print(this.expectedValue))
    }

    companion object {
        fun isDbMatcher(text: String) = (text.isRegex() || text.isWithin() || text.isNumber() || text.isNotNull())
    }
}

class WithinValueComparer(tolerance: Long) : IsActualWithinToleranceOfExpectedTimestampValueComparer(0, tolerance) {
    public override fun isExpected(
        expectedTable: ITable?,
        actualTable: ITable?,
        rowNum: Int,
        columnName: String?,
        dataType: DataType,
        expectedValue: Any?,
        actualValue: Any?
    ) = super.isExpected(expectedTable, actualTable, rowNum, columnName, dataType, expectedValue, actualValue)

    override fun convertValueToTimeInMillis(timestampValue: Any?) = if (timestampValue is java.sql.Date) timestampValue.time
    else super.convertValueToTimeInMillis(timestampValue)
}

open class RegexAndWithinAwareValueComparer : IsActualEqualToExpectedValueComparer() {
    protected lateinit var evaluator: Evaluator

    fun setEvaluator(evaluator: Evaluator): RegexAndWithinAwareValueComparer {
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
    ): Boolean = when {
        expected.isNotNull() -> setVarIfNeeded(actual, expected) { a, _ -> a != null }
        expected.isNumber() -> setVarIfNeeded(actual, expected) { a, _ -> regexMatches("^\\d+\$", a) }
        expected.isString() -> setVarIfNeeded(actual, expected) { a, _ -> regexMatches("^\\w+\$", a) }
        expected.isRegex() -> setVarIfNeeded(actual, expected) { a, e -> regexMatches(e, a) }
        expected.isWithin() -> setVarIfNeeded(actual, expected) { a, e ->
            WithinValueComparer(expected.toString().withinPeriod()).isExpected(
                expectedTable, actualTable, rowNum, columnName, dataType, resolve(e.toString()), a
            )
        }
        else -> super.isExpected(expectedTable, actualTable, rowNum, columnName, dataType, expected, actual)
    }

    private fun setVarIfNeeded(actual: Any?, expected: Any?, check: (actual: Any?, expected: Any?) -> Boolean): Boolean {
        val split = expected.toString().split(">>")
        if (split.size > 1) evaluator.setVariable("#${split[1]}", actual)
        return check(actual, split[0])
    }

    private fun resolve(expected: String): Timestamp {
        val expectedDateExpression = expected.substring(expected.indexOf("}") + 1).trim()
        return Timestamp(
            (if (expectedDateExpression.isBlank()) Date() else (evaluator.resolveToObj(expectedDateExpression) as Date))
                .time
        )
    }

    private fun regexMatches(expectedValue: Any?, actualValue: Any?): Boolean {
        if (actualValue == null) return false
        val expected = expectedValue.toString()
        return regexMatches(expected.substring(expected.indexOf("}") + 1).trim(), actualValue)
    }

    private fun regexMatches(pattern: String, actualValue: Any?): Boolean =
        if (actualValue == null) false else Pattern.compile(pattern).matcher(actualValue.toString()).matches()
}

class IgnoreMillisComparer : RegexAndWithinAwareValueComparer() {
    override fun isExpected(
        expectedTable: ITable?, actualTable: ITable?, rowNum: Int, columnName: String?, dataType: DataType, expected: Any?, actual: Any?
    ): Boolean = if (super.isExpected(expectedTable, actualTable, rowNum, columnName, dataType, expected, actual)) true
    else compareIgnoringMillis(expected, actual)

    private fun compareIgnoringMillis(expected: Any?, actual: Any?): Boolean {
        val expectedDt = LocalDateTime.fromDateFields(expected as Date).withMillisOfSecond(0)
        val actualDt = LocalDateTime.fromDateFields(actual as Timestamp)
        return expectedDt.isEqual(actualDt) || expectedDt.plusSeconds(1).isEqual(actualDt)
    }
}

private fun Any?.isNotNull() = this != null && this.toString().startsWith("!{notNull}")
private fun Any?.isNumber() = this != null && this.toString().startsWith("!{number}")
private fun Any?.isString() = this != null && this.toString().startsWith("!{string}")
private fun Any?.isRegex() = this != null && this.toString().startsWith("!{regex}")
private fun Any?.isWithin() = this != null && this.toString().startsWith("!{within ")

private fun String.withinPeriod() = parsePeriod(
    this.substring(
        "!{within ".length,
        this.indexOf("}")
    ).trim()
).toPeriod().toStandardDuration().millis