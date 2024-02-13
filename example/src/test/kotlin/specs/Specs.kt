package specs

import app.start
import app.stop
import com.github.jknack.handlebars.Helper
import io.github.adven27.concordion.extensions.exam.core.AbstractSpecs
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.JsonVerifier
import io.github.adven27.concordion.extensions.exam.core.handlebars.date.DateHelpers
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.MatcherHelpers
import io.github.adven27.concordion.extensions.exam.core.handlebars.misc.MiscHelpers
import io.github.adven27.concordion.extensions.exam.db.DbPlugin
import io.github.adven27.concordion.extensions.exam.db.DbPlugin.ValuePrinter
import io.github.adven27.concordion.extensions.exam.db.DbPlugin.ValuePrinter.Default.TableColumn
import io.github.adven27.concordion.extensions.exam.db.DbTester
import io.github.adven27.concordion.extensions.exam.db.DbUnitConfig
import io.github.adven27.concordion.extensions.exam.db.DbUnitConfig.TableColumnValueComparer
import io.github.adven27.concordion.extensions.exam.db.commands.IgnoreMillisComparer
import io.github.adven27.concordion.extensions.exam.db.commands.JsonColumnComparer
import io.github.adven27.concordion.extensions.exam.mq.MqPlugin
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.MqTester.Message
import io.github.adven27.concordion.extensions.exam.ws.WsPlugin
import net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS
import java.util.ArrayDeque

class Nested : Specs()
class WsCheckFailures : Specs()
class MqCheckFailures : Specs()
class DbSetOperations : Specs()
class DbCheckFailures : Specs()

@Suppress("FunctionOnlyReturningConstant")
open class Specs : AbstractSpecs() {

    override fun init(): ExamExtension = ExamExtension(
        WsPlugin(PORT.also { System.setProperty("server.port", it.toString()) }),
        MqPlugin("myQueue" to DummyMq(), "myAnotherQueue" to DummyMq()),
        DbPlugin(
            dbTester,
            valuePrinter = ValuePrinter.Default(
                tableColumnType = mapOf(TableColumn("product", "meta_json") to "json")
            )
        )
    ).withHandlebar { hb ->
        hb.registerHelper(
            "hi",
            Helper { context: Any?, options ->
                /*
                    {{hi '1' 'p1 'p2' o1='a' o2='b'}} => Hello context = 1; params = [p1, p2]; options = {o1=a, o2=b}!
                    {{hi variable1 variable2 o1=variable3}} => Hello context = 1; params = [2]; options = {o1=3}!
                 */
                "Hello context = $context; params = ${options.params.map { it.toString() }}; options = ${options.hash}!"
            }
        )
    }.withVerifiers(
        "jsonIgnoreExtraFields" to JsonVerifier { it.withOptions(IGNORING_EXTRA_FIELDS) }
    )

    override fun startSut() {
        start()
    }

    override fun stopSut() {
        stop()
    }

    private val users = mutableListOf<String>()
    fun split(s: String) = s.split(" ").let { it[0] to it[1] }
    fun greetingFor(s: String) = "Hello $s!"
    fun greeting() = "Hello World!"
    fun someJson() = "{\"result\": 1}"
    fun someXml() = "<result>1</result>"
    fun setUpUser(s: String) = users.add(s)
    fun search(s: String) = users.filter { it.contains(s) }
    fun lowercase(name: String): Result = name.lowercase().let {
        Result(it, """{ "result": "$it" }""", """<result>$it</result>""")
    }

    data class Result(val text: String, val json: String, val xml: String)

    val emptyString = ""
    val dateHelpers: String = DateHelpers.entries.joinToString("\n") { it.describe() }
    val matcherHelpers: String = MatcherHelpers.entries.joinToString("\n") { it.describe() }
    val miscHelpers: String = MiscHelpers.entries.joinToString("\n") { it.describe() }

    companion object {
        const val PORT = 8888

        @JvmStatic
        val dbTester = DbTester(
            driver = "org.h2.Driver",
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:sql/populate.sql'",
            user = "sa",
            password = "",
            dbUnitConfig = DbUnitConfig(
                tableColumnValueComparer = listOf(
                    TableColumnValueComparer(
                        table = "types",
                        columnValueComparer = mapOf("DATETIME_TYPE" to IgnoreMillisComparer())
                    ),
                    TableColumnValueComparer(
                        table = "product",
                        columnValueComparer = mapOf("META_JSON" to JsonColumnComparer())
                    )
                )
            )
        )

        private class DummyMq : MqTester.NOOP() {
            private val queue = ArrayDeque<Message>()
            override fun purge() = queue.clear()
            override fun receive() = queue.map { queue.poll() }
            override fun send(message: Message) {
                queue += message
            }
        }
    }
}
