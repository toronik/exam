package io.github.adven27.concordion.extensions.exam.db.builder

import io.github.adven27.concordion.extensions.exam.core.commands.AwaitConfig
import io.github.adven27.concordion.extensions.exam.core.fileExt
import io.github.adven27.concordion.extensions.exam.core.findResource
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.builder.CompareOperation.EQUALS
import io.github.adven27.concordion.extensions.exam.db.commands.columnNamesArray
import io.github.adven27.concordion.extensions.exam.db.commands.sortedTable
import mu.KLogging
import org.awaitility.core.ConditionTimeoutException
import org.concordion.api.Evaluator
import org.dbunit.Assertion
import org.dbunit.assertion.DbComparisonFailure
import org.dbunit.assertion.Difference
import org.dbunit.assertion.comparer.value.ValueComparer
import org.dbunit.database.AmbiguousTableNameException
import org.dbunit.dataset.CompositeDataSet
import org.dbunit.dataset.CompositeTable
import org.dbunit.dataset.DataSetException
import org.dbunit.dataset.FilteredDataSet
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable
import org.dbunit.dataset.csv.CsvDataSet
import org.dbunit.dataset.excel.XlsDataSet
import org.dbunit.dataset.filter.DefaultColumnFilter
import org.dbunit.dataset.filter.SequenceTableFilter
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
class DataSetExecutor(private val dbTester: DbTester) {


    companion object : KLogging()
}
