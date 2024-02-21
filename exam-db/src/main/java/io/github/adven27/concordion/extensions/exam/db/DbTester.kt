package io.github.adven27.concordion.extensions.exam.db

import io.github.adven27.concordion.extensions.exam.core.Content
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.commands.Verifier
import io.github.adven27.concordion.extensions.exam.core.html.fileExt
import io.github.adven27.concordion.extensions.exam.db.builder.CompareOperation
import io.github.adven27.concordion.extensions.exam.db.builder.CompareOperation.EQUALS
import io.github.adven27.concordion.extensions.exam.db.builder.ContainsFilterTable
import io.github.adven27.concordion.extensions.exam.db.builder.DataBaseSeedingException
import io.github.adven27.concordion.extensions.exam.db.builder.ExamDataSet
import io.github.adven27.concordion.extensions.exam.db.builder.ExamTable
import io.github.adven27.concordion.extensions.exam.db.builder.JSONDataSet
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy
import io.github.adven27.concordion.extensions.exam.db.builder.SeedStrategy.CLEAN_INSERT
import io.github.adven27.concordion.extensions.exam.db.commands.columnNames
import io.github.adven27.concordion.extensions.exam.db.commands.columnNamesArray
import io.github.adven27.concordion.extensions.exam.db.commands.sortedTable
import io.github.adven27.concordion.extensions.exam.db.commands.tableName
import io.github.adven27.concordion.extensions.exam.db.commands.withColumnsAsIn
import mu.KLogging
import org.concordion.api.Evaluator
import org.dbunit.Assertion.assertWithValueComparer
import org.dbunit.JdbcDatabaseTester
import org.dbunit.assertion.DbComparisonFailure
import org.dbunit.assertion.Difference
import org.dbunit.database.DatabaseConfig
import org.dbunit.database.DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS
import org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY
import org.dbunit.database.DatabaseConfig.PROPERTY_METADATA_HANDLER
import org.dbunit.database.DatabaseConfig.PROPERTY_TABLE_TYPE
import org.dbunit.database.IDatabaseConnection
import org.dbunit.database.search.TablesDependencyHelper.getAllDependentTables
import org.dbunit.dataset.AbstractDataSet
import org.dbunit.dataset.CompositeDataSet
import org.dbunit.dataset.CompositeTable
import org.dbunit.dataset.FilteredDataSet
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable
import org.dbunit.dataset.csv.CsvDataSet
import org.dbunit.dataset.datatype.AbstractDataType
import org.dbunit.dataset.datatype.DataType
import org.dbunit.dataset.excel.XlsDataSet
import org.dbunit.dataset.filter.DefaultColumnFilter.includedColumnsTable
import org.dbunit.dataset.filter.SequenceTableFilter
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.dbunit.ext.db2.Db2DataTypeFactory
import org.dbunit.ext.db2.Db2MetadataHandler
import org.dbunit.ext.h2.H2DataTypeFactory
import org.dbunit.ext.hsqldb.HsqldbDataTypeFactory
import org.dbunit.ext.mssql.MsSqlDataTypeFactory
import org.dbunit.ext.mysql.MySqlDataTypeFactory
import org.dbunit.ext.mysql.MySqlMetadataHandler
import org.dbunit.ext.oracle.OracleDataTypeFactory
import org.dbunit.ext.postgresql.PostgresqlDataTypeFactory
import org.dbunit.util.QualifiedTableName
import org.postgresql.util.PGobject
import java.io.File
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
open class DbTester @JvmOverloads constructor(
    driver: String,
    url: String,
    user: String,
    password: String,
    schema: String? = null,
    dbUnitConfig: DbUnitConfig = DbUnitConfig(),
    dataTypeConfig: Map<String, (DatabaseConfig) -> DatabaseConfig> = DATA_TYPES,
    private val allowedSeedStrategies: List<SeedStrategy> = SeedStrategy.entries
) : DbTesterBase(driver, url, user, password, schema, dbUnitConfig, dataTypeConfig), AutoCloseable {

    private val dataSetVerifier: DataSetVerifier = DataSetVerifier(dbUnitConfig)
    private val tableVerifier: TableVerifier = TableVerifier(dbUnitConfig)

    fun seed(seed: TableSeed, eval: Evaluator) = requireAllowedStrategy(seed).apply {
        strategy.operation.execute(connection(ds), ExamDataSet(table, eval))
    }

    fun metaData(seed: TableSeed) = TableSeed(
        seed.ds,
        ExamTable(
            CompositeTable(
                connection(seed.ds)
                    .createTable(seed.table.tableName()).withColumnsAsIn(seed.table).tableMetaData,
                (seed.table as ExamTable).delegate
            ),
            seed.table.eval
        ),
        seed.strategy
    )

    fun seed(seed: FilesSeed, eval: Evaluator) = requireAllowedStrategy(seed).let {
        try {
            order(loadDataSet(eval, it.datasets), it.tableOrdering).apply {
                it.strategy.operation.execute(connection(it.ds), this)
            }
        } catch (expected: Exception) {
            throw DataBaseSeedingException("Could not seed dataset: $this", expected)
        }
    }

    fun seed(seed: DatasetSeed) = requireAllowedStrategy(seed).let {
        try {
            it.strategy.operation.execute(connection(it.ds), it.dataset)
        } catch (expected: Exception) {
            throw DataBaseSeedingException("Could not seed dataset: $this", expected)
        }
    }

    private fun <T : Seed> requireAllowedStrategy(seed: T) = seed.apply {
        require(strategy in allowedSeedStrategies) {
            "Forbidden seed strategy $strategy. Allowed: $allowedSeedStrategies"
        }
    }

    fun test(expectation: DataSetFilesExpectation, eval: Evaluator) = test(
        DataSetExpectation(
            loadDataSet(eval, expectation.datasets),
            expectation.ds,
            expectation.await,
            expectation.orderBy,
            expectation.excludeCols,
            expectation.compareOperation
        ),
        eval
    )

    fun test(expectation: DataSetExpectation, eval: Evaluator) =
        dataSetVerifier.verify(eval, expectation) { this[it.ds].actualDataSet(it.dataset.tableNames) }

    fun test(expectation: TableExpectation, eval: Evaluator) = tableVerifier.verify(eval, expectation) {
        with(connection(it.ds)) {
            val qualifiedName = QualifiedTableName(it.table.tableName(), schema).qualifiedName
            createQueryTable(
                qualifiedName,
                "SELECT * FROM $qualifiedName ${if (it.where.isEmpty()) "" else "WHERE ${it.where}"}"
            )
        }
    }

    data class DataSetFilesExpectation(
        val datasets: List<String>,
        override val ds: String? = null,
        override val await: AwaitConfig? = null,
        override val orderBy: Set<String> = setOf(),
        override val excludeCols: Set<String> = setOf(),
        override val compareOperation: CompareOperation = EQUALS
    ) : Expectation

    data class DataSetExpectation(
        val dataset: IDataSet,
        override val ds: String? = null,
        override val await: AwaitConfig? = null,
        override val orderBy: Set<String> = setOf(),
        override val excludeCols: Set<String> = setOf(),
        override val compareOperation: CompareOperation = EQUALS
    ) : Expectation

    data class TableExpectation(
        val table: ITable,
        override val ds: String? = null,
        override val await: AwaitConfig? = null,
        override val orderBy: Set<String> = setOf(),
        val where: String = "",
        override val excludeCols: Set<String> = setOf(),
        override val compareOperation: CompareOperation = EQUALS
    ) : Expectation

    interface Expectation {
        val ds: String?
        val await: AwaitConfig?
        val orderBy: Set<String>
        val excludeCols: Set<String>
        val compareOperation: CompareOperation
    }

    data class TableSeed(
        override val ds: String?,
        val table: ITable,
        override val strategy: SeedStrategy = CLEAN_INSERT
    ) : Seed

    data class DatasetSeed(
        override val ds: String?,
        val dataset: IDataSet,
        override val strategy: SeedStrategy = CLEAN_INSERT
    ) : Seed

    data class FilesSeed(
        override val ds: String?,
        val datasets: List<String>,
        override val strategy: SeedStrategy = CLEAN_INSERT,
        val tableOrdering: List<String> = listOf()
    ) : Seed

    interface Seed {
        val ds: String?
        val strategy: SeedStrategy
    }

    open class DataSetVerifier(private val dbUnitConfig: DbUnitConfig) : Verifier<DataSetExpectation, IDataSet> {
        companion object : KLogging()

        override fun verify(
            eval: Evaluator,
            expected: DataSetExpectation,
            actual: (DataSetExpectation) -> IDataSet
        ): Verifier.Check<DataSetExpectation, IDataSet> {
            dbUnitConfig.apply {
                valueComparer.setEvaluator(eval)
                tableColumnValueComparer.onEach {
                    it.columnValueComparer.forEach { (_, comparer) -> comparer.setEvaluator(eval) }
                }
            }
            return checkWith(expected.await) {
                check(
                    actualDataSet = { actual(expected) },
                    expectedDataSet = expected.dataset,
                    dbUnitConfig = dbUnitConfig,
                    orderBy = expected.orderBy,
                    compareOperation = expected.compareOperation,
                    excludeCols = expected.excludeCols
                )
            }.let { (actual, fails) ->
                Verifier.Check(expected, actual, fails.takeIf { it.isNotEmpty() }?.let { Failed(it) })
            }
        }

        open class Failed(val fails: List<Fail>) : java.lang.AssertionError("Dataset check failed\n$fails") {
            init {
                require(fails.isNotEmpty())
            }

            val tables = fails.map { it.expected.tableName() }
        }

        protected fun checkWith(await: AwaitConfig?, check: () -> Pair<IDataSet, List<Fail>>) = await?.let {
            lateinit var result: Pair<IDataSet, List<Fail>>
            runCatching { it.await().until { check().also { result = it }.second.isEmpty() } }
            result
        } ?: check()

        private fun check(
            actualDataSet: (Array<String>) -> IDataSet,
            expectedDataSet: IDataSet,
            dbUnitConfig: DbUnitConfig,
            orderBy: Set<String>,
            compareOperation: CompareOperation,
            excludeCols: Set<String>
        ): Pair<IDataSet, List<Fail>> {
            val tableNames = expectedDataSet.tableNames
            val actual = actualDataSet(tableNames)
            return actual to tableNames.mapNotNull { table ->
                var expectedTable = expectedDataSet.getTable(table)
                val sortCols = orderBy.takeIf { it.isNotEmpty() }?.filterBy(table) ?: expectedTable.columnNamesArray()
                var actualTable = includedColumnsTable(
                    sortedTable(actual.getTable(table), sortCols, dbUnitConfig.overrideRowSortingComparer),
                    columns(expectedTable, sortCols).toTypedArray()
                )
                expectedTable = CompositeTable(actualTable.tableMetaData, expectedTable)
                if (compareOperation == CompareOperation.CONTAINS) {
                    actualTable = ContainsFilterTable(actualTable, expectedTable, excludeCols.toList())
                }
                assert(expectedTable, actualTable, dbUnitConfig)
            }.also { logger.info(it.toString()) }
        }

        private fun columns(t: ITable, sortCols: Array<String>) =
            t.tableMetaData.columns.map { it.columnName }.toSet() + sortCols

        private fun assert(expectedTable: ITable, actualTable: ITable, config: DbUnitConfig) = try {
            assertWithValueComparer(
                expectedTable,
                actualTable,
                config.diffFailureHandler.apply { diffList.clear() },
                config.valueComparer,
                config.tableColumnValueComparer.filter { it.table == expectedTable.tableName() }
                    .flatMap { it.columnValueComparer.toList() }.toMap()
            )
            config.diffFailureHandler.diffList.map { it as Difference }.takeIf { it.isNotEmpty() }?.let {
                ContentMismatch(expectedTable, actualTable, it)
            }
        } catch (f: DbComparisonFailure) {
            SizeMismatch(expectedTable, actualTable, f)
        }

        private fun Set<String>.filterBy(tableName: String) = map { it.uppercase() }
            .filter { it.startsWith("${tableName.uppercase()}.") }
            .map { it.removePrefix("${tableName.uppercase()}.") }
            .toTypedArray()

        data class ContentMismatch(
            override val expected: ITable,
            override val actual: ITable,
            val diff: List<Difference>
        ) : Fail {
            init {
                require(diff.isNotEmpty())
            }

            fun diff(row: Int, col: String) = diff[row, col]
            private operator fun List<Difference>.get(row: Int, col: String) =
                singleOrNull { it.rowIndex == row && it.columnName.equals(col, ignoreCase = true) }
        }

        data class SizeMismatch(
            override val expected: ITable,
            override val actual: ITable,
            val rowsMismatch: DbComparisonFailure
        ) : Fail

        sealed interface Fail {
            val expected: ITable
            val actual: ITable
        }
    }

    open class TableVerifier(private val dbUnitConfig: DbUnitConfig) : Verifier<TableExpectation, ITable> {
        companion object : KLogging()

        override fun verify(
            eval: Evaluator,
            expected: TableExpectation,
            actual: (TableExpectation) -> ITable
        ): Verifier.Check<TableExpectation, ITable> {
            dbUnitConfig.apply {
                valueComparer.setEvaluator(eval)
                tableColumnValueComparer.forEach {
                    it.columnValueComparer.forEach { (_, comparer) -> comparer.setEvaluator(eval) }
                }
            }
            return with(expected) {
                val sortCols =
                    (if (orderBy.isEmpty() && table.rowCount > 0) table.columnNames() else orderBy).toTypedArray()
                val actualTable = actual(this)
                var sortedActual = sortedTable(
                    actualTable.withColumnsAsIn(table, sortCols),
                    sortCols,
                    dbUnitConfig.overrideRowSortingComparer
                )
                try {
                    val expectedTable = CompositeTable(actualTable.withColumnsAsIn(table).tableMetaData, table)
                    await?.let {
                        it.await("Await DB table ${expectedTable.tableName()}").untilAsserted {
                            sortedActual = sortedTable(
                                actual(this).withColumnsAsIn(expectedTable),
                                sortCols,
                                dbUnitConfig.overrideRowSortingComparer
                            )
                            dbUnitAssert(expectedTable, sortedActual, sortCols)
                        }
                    } ?: dbUnitAssert(expectedTable, sortedActual, sortCols)
                    Verifier.Check(expected = expected, actual = sortedActual)
                } catch (ignore: Throwable) {
                    logger.warn("Check failed", ignore)
                    Verifier.Check(expected = expected, actual = sortedActual, fail = ignore)
                }
            }
        }

        class SizeMismatch(expected: ITable, actual: ITable, failure: DbComparisonFailure) :
            Mismatch(expected = expected, actual = actual, message = "table size mismatch", failure = failure)

        class ContentMismatch(
            expected: ITable,
            actual: ITable,
            val diff: List<Difference>
        ) : Mismatch(expected = expected, actual = actual, message = "table content mismatch:\n${diff.prettyPrint()}") {
            init {
                require(diff.isNotEmpty())
            }

            private operator fun List<Difference>.get(row: Int, col: String) =
                singleOrNull { it.rowIndex == row && it.columnName.equals(col, ignoreCase = true) }
        }

        sealed class Mismatch(
            val expected: ITable,
            val actual: ITable,
            message: String,
            failure: DbComparisonFailure? = null
        ) : AssertionError(message, failure)

        private fun dbUnitAssert(expected: ITable, actual: ITable, sortCols: Array<String>) {
            try {
                assert(expected, actual, sortCols, dbUnitConfig)
            } catch (fail: DbComparisonFailure) {
                throw SizeMismatch(expected, actual, fail)
            }
            if (dbUnitConfig.diffFailureHandler.diffList.isNotEmpty()) {
                throw ContentMismatch(
                    expected,
                    actual,
                    dbUnitConfig.diffFailureHandler.diffList.map { it as Difference }
                )
            }
        }

        protected fun assert(expected: ITable, actual: ITable, sortCols: Array<String>, config: DbUnitConfig) {
            assertWithValueComparer(
                expected,
                actual.withColumnsAsIn(expected),
                config.diffFailureHandler.apply { diffList.clear() },
                config.valueComparer,
                config.tableColumnValueComparer.filter { it.table == expected.tableName() }
                    .flatMap { it.columnValueComparer.toList() }.toMap()

            )
        }
    }
}

@Suppress("LongParameterList", "TooManyFunctions")
open class DbTesterBase @JvmOverloads constructor(
    driver: String,
    url: String,
    user: String,
    password: String,
    schema: String? = null,
    val dbUnitConfig: DbUnitConfig = DbUnitConfig(),
    private val dataTypeConfig: Map<String, (DatabaseConfig) -> DatabaseConfig> = DATA_TYPES
) : JdbcDatabaseTester(driver, url, user, password, schema), AutoCloseable {

    companion object : KLogging() {
        const val DEFAULT_DATASOURCE = "default"
        val DATA_TYPES: Map<String, (DatabaseConfig) -> DatabaseConfig> = mapOf(
            "Db2" to {
                it.apply {
                    setProperty(PROPERTY_DATATYPE_FACTORY, Db2DataTypeFactory())
                    setProperty(PROPERTY_METADATA_HANDLER, Db2MetadataHandler())
                }
            },
            "DB2/LINUXX8664" to {
                it.apply {
                    setProperty(PROPERTY_DATATYPE_FACTORY, Db2DataTypeFactory())
                    setProperty(PROPERTY_METADATA_HANDLER, Db2MetadataHandler())
                }
            },
            "MySQL" to {
                it.apply {
                    setProperty(PROPERTY_DATATYPE_FACTORY, MySqlDataTypeFactory())
                    setProperty(PROPERTY_METADATA_HANDLER, MySqlMetadataHandler())
                }
            },
            "HSQL Database Engine" to { it.apply { setProperty(PROPERTY_DATATYPE_FACTORY, HsqldbDataTypeFactory()) } },
            "H2" to { it.apply { setProperty(PROPERTY_DATATYPE_FACTORY, H2DataTypeFactory()) } },
            "Oracle" to { it.apply { setProperty(PROPERTY_DATATYPE_FACTORY, OracleDataTypeFactory()) } },
            "PostgreSQL" to {
                it.apply {
                    setProperty(PROPERTY_DATATYPE_FACTORY, JsonbPostgresqlDataTypeFactory())
                    setProperty(PROPERTY_TABLE_TYPE, arrayOf("VIEW", "TABLE", "PARTITIONED TABLE"))
                }
            },
            "Microsoft SQL Server" to { it.apply { setProperty(PROPERTY_DATATYPE_FACTORY, MsSqlDataTypeFactory()) } }
        )
    }

    val executors = ConcurrentHashMap<String, DbTester>()
    private var conn: IDatabaseConnection? = null

    fun connection(ds: String?) = this[ds ?: DEFAULT_DATASOURCE].connection

    override fun getConnection(): IDatabaseConnection =
        if (conn == null || conn!!.connection.isClosed) createConnection().also { conn = it } else conn!!

    private fun createConnection(): IDatabaseConnection {
        val conn = super.getConnection()
        val cfg = conn.config

        setDbSpecificProperties(conn.connection.metaData.databaseProductName, cfg)
        cfg.setProperty(FEATURE_ALLOW_EMPTY_FIELDS, true)
        dbUnitConfig.databaseConfigProperties.forEach { (k, v) -> cfg.setProperty(k, v) }
        return conn
    }

    private fun setDbSpecificProperties(dbName: String, config: DatabaseConfig) =
        dataTypeConfig[dbName]?.let { it(config) } ?: logger.error("No matching database product found $dbName")

    override fun close() {
        try {
            if (conn?.connection?.isClosed != true) conn?.close()
        } catch (e: SQLException) {
            logger.warn("Error on connection closing", e)
        }
    }

    @Suppress("unused")
    fun <R> useStatement(fn: (Statement) -> R): R = connection.connection.createStatement().use { fn(it) }

    fun select(ds: String?, table: String, cols: Set<String>, where: String? = null): ITable = connection(ds).let { c ->
        (if (where.isNullOrEmpty()) c.createTable(table) else c.select(table, where)).let {
            if (cols.isEmpty()) it else includedColumnsTable(it, cols.toTypedArray())
        }
    }

    private fun IDatabaseConnection.select(tableName: String, filter: String): ITable =
        createQueryTable(tableName, "SELECT * FROM $tableName WHERE $filter")

    fun actualWithDependentTables(ds: String?, table: String): IDataSet = connection(ds).let {
        it.createDataSet(getAllDependentTables(it, QualifiedTableName(table, it.schema).qualifiedName))
    }

    protected fun actualDataSet(tables: Array<String>): IDataSet = connection.createDataSet(tables)

    /**
     * @param dataSets one or more dataset names to instantiate
     * @return loaded dataset (in case of multiple dataSets they will be merged in composite dataset)
     */
    fun loadDataSet(eval: Evaluator, dataSets: List<String>): IDataSet = Loader().load(
        dataSets,
        eval,
        dbUnitConfig.isCaseSensitiveTableNames(),
        dbUnitConfig.isColumnSensing
    )

    protected fun order(target: IDataSet, tableOrdering: List<String>): IDataSet {
        var ordered = target
        if (tableOrdering.isNotEmpty()) {
            ordered = FilteredDataSet(
                SequenceTableFilter(tableOrdering.toTypedArray(), dbUnitConfig.isCaseSensitiveTableNames()),
                ordered
            )
        }
        return ordered
    }

    operator fun get(ds: String?) =
        requireNotNull(executors[ds ?: DEFAULT_DATASOURCE]) { "Datasource $ds not found. Registered: $executors" }

    open class Loader {
        fun load(dataSets: List<String>, eval: Evaluator, tableSensing: Boolean, columnSensing: Boolean) =
            CompositeDataSet(
                loadAll(dataSets, eval, tableSensing, columnSensing).toTypedArray(),
                true,
                tableSensing
            )

        private fun loadAll(dataSets: List<String>, eval: Evaluator, tableSensing: Boolean, columnSensing: Boolean) =
            dataSets
                .mapNotNull { load(it, columnSensing, tableSensing) }
                .map { ExamDataSet(tableSensing, it, eval) }
                .ifEmpty { throw NoDatasetLoaded(dataSets) }

        private fun load(dataSet: String, columnSensing: Boolean, tableSensing: Boolean): AbstractDataSet? {
            val name = dataSet.trim()
            val url = dataSetUrl(name)
            return when (name.fileExt()) {
                "xml" -> try {
                    FlatXmlDataSetBuilder()
                        .setColumnSensing(columnSensing)
                        .setCaseSensitiveTableNames(tableSensing)
                        .build(url)
                } catch (expected: Exception) {
                    FlatXmlDataSetBuilder()
                        .setColumnSensing(columnSensing)
                        .setCaseSensitiveTableNames(tableSensing)
                        .build(url.openStream())
                }

                "json" -> JSONDataSet(url.openStream())
                "xls" -> XlsDataSet(url.openStream())
                "csv" -> CsvDataSet(File(url.file).parentFile)
                else -> null.also { logger.error("Unsupported dataset extension") }
            }
        }

        class NoDatasetLoaded(names: List<String>) : RuntimeException("No dataset loaded for $names")

        private fun dataSetUrl(ds: String) = (ds.takeIf { it.startsWith("/") } ?: "/$ds").let {
            requireNotNull(javaClass.getResource(it) ?: javaClass.getResource("/datasets$it")) {
                "Could not find dataset '$it' under 'resources' or 'resources/datasets' directory."
            }
        }
    }
}

private fun List<Difference>.prettyPrint(): String =
    associate { (""""${it.columnName}" in row ${it.rowIndex + 1}""") to it.failMessage }
        .toSortedMap().entries.joinToString("\n") { """${it.key}: ${it.value}""" }

class JsonbPostgresqlDataTypeFactory : PostgresqlDataTypeFactory() {
    override fun createDataType(sqlType: Int, sqlTypeName: String?): DataType = when (sqlTypeName) {
        in listOf("jsonb", "json") -> JsonbDataType(sqlTypeName!!)
        else -> super.createDataType(sqlType, sqlTypeName)
    }

    class JsonbDataType(name: String) : AbstractDataType(name, Types.OTHER, Content.Json::class.java, false) {
        override fun typeCast(obj: Any?): Content.Json? = obj?.let {
            if (it is Content.Json) it else Content.Json(it.toString())
        }

        override fun getSqlValue(column: Int, resultSet: ResultSet): Any? =
            resultSet.getString(column)?.let { Content.Json(it) }

        override fun setSqlValue(value: Any?, column: Int, statement: PreparedStatement) = statement.setObject(
            column,
            PGobject().apply {
                this.type = "json"
                this.value = value?.toString()
            }
        )
    }
}
