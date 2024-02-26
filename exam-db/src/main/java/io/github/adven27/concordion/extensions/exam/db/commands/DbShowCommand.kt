package io.github.adven27.concordion.extensions.exam.db.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.code
import io.github.adven27.concordion.extensions.exam.core.html.fileExt
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.pre
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.builder.JSONWriter
import io.github.adven27.concordion.extensions.exam.db.commands.DbShowCommand.Model
import io.github.adven27.concordion.extensions.exam.db.commands.DbShowCommand.Result
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.ResultRecorder
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.ITable
import org.dbunit.dataset.csv.CsvDataSetWriter
import org.dbunit.dataset.excel.XlsDataSet
import org.dbunit.dataset.xml.FlatXmlDataSet
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths

open class DbShowCommand(
    dbTester: DbTester,
    private val valuePrinter: DbPlugin.ValuePrinter,
    private val parser: Parser = Parser.Default()
) : DbCommand<Model, Result>(dbTester, setOf(CREATE_DATASET, SAVE_TO)) {
    companion object {
        const val CREATE_DATASET = "createDataSet"
        const val SAVE_TO = "saveTo"
    }

    override fun model(context: Context) = parser.parse(context)

    override fun process(model: Model, eval: Evaluator, recorder: ResultRecorder) = with(model) {
        Result(
            caption = caption,
            table = dbTester.select(ds, table, cols, where),
            generated = dataset(this)
        )
    }

    private fun dataset(model: Model) =
        if (model.createDataSet || !model.saveTo.isNullOrEmpty()) {
            ByteArrayOutputStream().use {
                save(
                    path = model.saveTo,
                    dataSet = dbTester.actualWithDependentTables(model.ds, model.table),
                    out = it
                )
                it.toString(UTF_8)
            }
        } else {
            null
        }

    override fun render(commandCall: CommandCall, result: Result) {
        with(commandCall.html()) {
            attr("hidden", "true")
            result.generated?.let { below(pre()(code(it).attrs("class" to "language-xml"))) }
            below(renderTable(result.table, valuePrinter, result.caption))
        }
    }

    data class Model(
        val ds: String?,
        val table: String,
        val caption: String?,
        val createDataSet: Boolean,
        val saveTo: String?,
        val where: String?,
        val cols: Set<String>
    )

    data class Result(val caption: String?, val table: ITable, val generated: String?)

    private fun save(path: String?, dataSet: IDataSet, out: ByteArrayOutputStream) {
        when (path?.fileExt() ?: "xml") {
            "json" -> JSONWriter(dataSet, out).write().run { saveIfNeeded(path, out) }
            "xls" -> XlsDataSet.write(dataSet, out).run { saveIfNeeded(path, out) }
            "csv" -> path?.apply {
                File(Paths.get("src", "test", "resources").toFile(), substringBeforeLast(".")).apply {
                    mkdirs()
                    CsvDataSetWriter.write(dataSet, this)
                }
            }

            else -> FlatXmlDataSet.write(dataSet, out).run { saveIfNeeded(path, out) }
        }
    }

    private fun saveIfNeeded(path: String?, outputStream: ByteArrayOutputStream) {
        path?.apply {
            FileOutputStream(
                File(Paths.get("src", "test", "resources").toFile(), this).apply {
                    parentFile.mkdirs()
                    createNewFile()
                },
                false
            ).use { outputStream.writeTo(it) }
        }
    }

    interface Parser {
        fun parse(context: Context): Model

        open class Default : Parser {
            override fun parse(context: Context) = Model(
                ds = context[DS],
                table = context.expression,
                caption = context.el.firstOrNull("caption")?.text(),
                where = context[WHERE],
                createDataSet = context[CREATE_DATASET].toBoolean(),
                saveTo = context[SAVE_TO],
                cols = parseCols(context.el).toSet()
            )

            private fun parseCols(html: Html) =
                html.el.getFirstChildElement("thead")?.getFirstChildElement("tr")?.childElements?.map { it.text.trim() }
                    ?: listOf()
        }
    }
}
