package io.github.adven27.concordion.extensions.exam.db

import com.github.jknack.handlebars.Options
import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.db.commands.DbCleanCommand
import io.github.adven27.concordion.extensions.exam.db.commands.set.DbSetCommand
import io.github.adven27.concordion.extensions.exam.db.commands.DbShowCommand
import io.github.adven27.concordion.extensions.exam.db.commands.ExamMatchersAwareValueComparer
import io.github.adven27.concordion.extensions.exam.db.commands.check.DbCheckCommand
import mu.KLogging
import org.concordion.api.Command
import org.concordion.api.Element
import org.dbunit.assertion.DiffCollectingFailureHandler
import org.dbunit.dataset.Column
import org.dbunit.dataset.Columns.findColumnsByName
import org.dbunit.dataset.ITable
import org.dbunit.dataset.SortedTable
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Time
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.time.format.DateTimeFormatter.ISO_TIME
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class DbPlugin @JvmOverloads constructor(
    private val dbTester: DbTester,
    private val connectOnDemand: Boolean = true,
    private val valuePrinter: ValuePrinter = ValuePrinter.Default(),
    private val override: Map<String, Command> = mapOf()
) : ExamPlugin {
    /**
     * @param dbUnitConfig properties for org.dbunit.database.DatabaseConfig
     */
    @JvmOverloads
    @Suppress("unused", "LongParameterList")
    constructor(
        driver: String,
        url: String,
        user: String,
        password: String,
        schema: String? = null,
        connectOnDemand: Boolean = true,
        valuePrinter: ValuePrinter = ValuePrinter.Default(),
        dbUnitConfig: DbUnitConfig = DbUnitConfig(),
        override: Map<String, Command> = mapOf()
    ) : this(
        DbTester(driver, url, user, password, schema, dbUnitConfig),
        connectOnDemand,
        valuePrinter,
        override
    )

    /**
     * @param defaultTester Default datasource, used when `ds` attribute is omitted: `<e:db-set ...>`
     * @param others Map of additional datasources, used when `ds` attribute present: `<e:db-set ds="other" ...>`
     *
     *```
     * DbPlugin(
     *     DbTester(...),
     *     mapOf("other" to DbTester(...)),
     *  ...
     * )
     * ```
     */
    @Suppress("unused")
    @JvmOverloads
    constructor(
        defaultTester: DbTester,
        others: Map<String, DbTester>,
        connectOnDemand: Boolean = true,
        valuePrinter: ValuePrinter = ValuePrinter.Default()
    ) : this(defaultTester, connectOnDemand, valuePrinter) {
        for ((key, value) in others) {
            dbTester.executors[key] = value
        }
    }

    init {
        dbTester.executors[DbTesterBase.DEFAULT_DATASOURCE] = dbTester
    }

    override fun commands(): Map<String, Command> = mapOf(
        "db-show" to DbShowCommand(dbTester, valuePrinter),
        "db-check" to DbCheckCommand(dbTester, valuePrinter),
        "db-set" to DbSetCommand(dbTester, valuePrinter),
        "db-clean" to DbCleanCommand(dbTester)
    ) + override

    override fun setUp() {
        if (!connectOnDemand) dbTester.connection
    }

    override fun tearDown() = dbTester.close()

    /**
     * Defines how to print and render values in '<e:db-*' commands
     */
    interface ValuePrinter {
        open class Default @JvmOverloads constructor(
            formatter: DateTimeFormatter = ISO_LOCAL_DATE_TIME,
            private val tableColumnStyle: Map<TableColumn, String> = mapOf()
        ) : AbstractDefault(formatter) {
            data class TableColumn(val table: String, val column: String)

            override fun orElse(value: Any): String = value.toString()

            override fun wrap(table: String, column: String, value: Any?): Element =
                super.wrap(table, column, value).let { e ->
                    tableColumnStyle.entries
                        .filter { (tc, _) -> tc.eq(table, column) }
                        .map { it.value }
                        .firstOrNull()
                        ?.let { e.addStyleClass(it) }
                        ?: e
                }

            private fun TableColumn.eq(t: String, c: String) =
                table.equals(t, ignoreCase = true) && column.equals(c, ignoreCase = true)
        }

        abstract class AbstractDefault(private val formatter: DateTimeFormatter) : ValuePrinter {
            override fun print(table: String, column: String, value: Any?): String = when (value) {
                null -> "(null)"
                is Array<*> -> value.contentToString()
                is Time -> printTime(value)
                is java.sql.Date -> printDate(Date(value.time))
                is Date -> printDate(value)
                is Content -> value.pretty()
                else -> orElse(value)
            }

            private fun printDate(value: Date) = formatter.withZone(ZoneId.systemDefault()).format(value.toInstant())
            private fun printTime(value: Time) = ISO_TIME.withZone(ZoneId.systemDefault()).format(value.toLocalTime())
            override fun wrap(table: String, column: String, value: Any?): Element =
                when (value) {
                    is Content -> pre(print(table, column, value), "class" to value.type).el
                    else -> span(print(table, column, value)).el
                }

            abstract fun orElse(value: Any): String
        }

        fun print(table: String, column: String, value: Any?): String
        fun wrap(table: String, column: String, value: Any?): Element
    }
}

data class DbUnitConfig @JvmOverloads constructor(
    val databaseConfigProperties: Map<String, Any?> = mapOf(),
    val tableColumnValueComparer: List<TableColumnValueComparer> = listOf(),
    val valueComparer: ExamMatchersAwareValueComparer = ExamMatchersAwareValueComparer(),
    val overrideRowSortingComparer: RowComparator = RowComparator(),
    val diffFailureHandler: DiffCollectingFailureHandler = DiffCollectingFailureHandler(),
    val isColumnSensing: Boolean = false
) {
    data class TableColumnValueComparer(
        val table: String,
        val columnValueComparer: Map<String, ExamMatchersAwareValueComparer>
    )

    @Suppress("unused")
    class Builder {
        var databaseConfigProperties: Map<String, Any?> = mapOf()
        var valueComparer: ExamMatchersAwareValueComparer = ExamMatchersAwareValueComparer()
        var tableColumnValueComparers: List<TableColumnValueComparer> = listOf()
        var overrideRowSortingComparer: RowComparator = RowComparator()
        var diffFailureHandler: DiffCollectingFailureHandler = DiffCollectingFailureHandler()
        var columnSensing: Boolean = false

        fun databaseConfigProperties(databaseConfigProperties: Map<String, Any?>) =
            apply { this.databaseConfigProperties += databaseConfigProperties }

        fun valueComparer(valueComparer: ExamMatchersAwareValueComparer) =
            apply { this.valueComparer = valueComparer }

        fun columnValueComparer(columnValueComparer: List<TableColumnValueComparer>) =
            apply { this.tableColumnValueComparers = columnValueComparer }

        fun overrideRowSortingComparer(overrideRowSortingComparer: RowComparator = RowComparator()) =
            apply { this.overrideRowSortingComparer = overrideRowSortingComparer }

        fun diffFailureHandler(diffFailureHandler: DiffCollectingFailureHandler) =
            apply { this.diffFailureHandler = diffFailureHandler }

        fun columnSensing(columnSensing: Boolean) = apply { this.columnSensing = columnSensing }
        fun build() = DbUnitConfig(
            databaseConfigProperties,
            tableColumnValueComparers,
            valueComparer,
            overrideRowSortingComparer,
            diffFailureHandler,
            columnSensing
        )
    }

    fun isCaseSensitiveTableNames() = databaseConfigProperties.containsKey("caseSensitiveTableNames") &&
        databaseConfigProperties["caseSensitiveTableNames"].toString().toBoolean()
}

open class RowComparator {
    fun init(table: ITable, sortCols: Array<String>) =
        object : SortedTable.AbstractRowComparator(table, findColumnsByName(sortCols, table.tableMetaData)) {
            override fun compare(col: Column?, val1: Any?, val2: Any?) = this@RowComparator.compare(col, val1, val2)
        }

    open fun compare(column: Column?, value1: Any?, value2: Any?): Int = try {
        column!!.dataType.compare(value1, value2)
    } catch (ignore: Exception) {
        0
    }
}

@Suppress("unused")
open class DbHelpers(protected val dbTester: DbTester) {

    fun dbCount(context: Any, options: Options) = dbTester.connection.getRowCount(context.toString())

    protected fun queryStringFrom(table: String, target: String, filter: Map<String, Any>): String =
        dbTester.useStatement {
            it.query(target, table, filter)
                .getString(target)
                .also { logger.debug { "${compositeFilter(filter)} $table id is $it" } }
        }

    protected fun queryIntFrom(table: String, target: String, filter: Map<String, Any>) =
        dbTester.useStatement {
            it.query(target, table, filter)
                .getInt(target)
                .also { logger.debug { "${compositeFilter(filter)} $table id is $it" } }
        }

    protected fun queryLongFrom(table: String, target: String, filter: Map<String, Any>) =
        dbTester.useStatement {
            it.query(target, table, filter)
                .getLong(target)
                .also { logger.debug { "${compositeFilter(filter)} $table id is $it" } }
        }

    protected fun compositeFilter(filter: Map<String, Any>) =
        filter.entries.joinToString(separator = " and ") {
            "${it.key}=${if (it.value is String) "'${it.value}'" else "${it.value}"}"
        }

    protected fun compositeKey(options: Options) =
        options.hash.entries.joinToString(separator = "|") { "${it.key}=${it.value}" }

    protected fun Statement.queryNext(sql: String): ResultSet = executeQuery(sql).apply { next() }
    protected fun Statement.query(target: String, table: String, filter: Map<String, Any>) =
        queryNext("select $target from $table where ${compositeFilter(filter)}")

    companion object : KLogging()
}
