package specs

import com.example.demo.DemoApplication
import com.github.jknack.handlebars.Helper
import io.github.adven27.concordion.extensions.exam.core.AbstractSpecs
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.VERIFIER_XML
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
import io.github.adven27.concordion.extensions.exam.db.commands.VerifierColumnComparer
import io.github.adven27.concordion.extensions.exam.mq.MqPlugin
import io.github.adven27.concordion.extensions.exam.mq.MqTester
import io.github.adven27.concordion.extensions.exam.mq.MqTester.Message
import io.github.adven27.concordion.extensions.exam.ws.WsPlugin
import io.github.adven27.env.core.Environment
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem
import net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.*

class Nested : Specs()
class WsCheckFailures : Specs()
class MqCheckFailures : Specs()
class DbSetOperations : Specs()
class DbCheckFailures : Specs()
class DbCheckContentTypes : Specs()

@Suppress("FunctionOnlyReturningConstant")
open class Specs : AbstractSpecs() {

    override fun init(): ExamExtension = ExamExtension(
        WsPlugin(port = ENV.sutPort()),
        MqPlugin("myQueue" to DummyMq(), "myAnotherQueue" to DummyMq()),
        DbPlugin(
            dbTester,
            valuePrinter = ValuePrinter.Default(
                tableColumnStyle = mapOf(
                    TableColumn("content_types", "data_xml") to "xml",
                    TableColumn("content_types", "data_json_with_extra_fields") to "details"
                )
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
        SUT = SpringApplication(DemoApplication::class.java).run()
    }

    override fun stopSut() {
        SUT.stop()
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
        private lateinit var SUT: ConfigurableApplicationContext
        val ENV: ApplicationEnvironment = ApplicationEnvironment().apply { up() }

        @JvmStatic
        val dbTester = with(ENV.database()) {
            DbTester(
                driver = driver,
                url = jdbcUrl,
                user = username,
                password = password,
                dbUnitConfig = DbUnitConfig(
                    tableColumnValueComparer = listOf(
                        TableColumnValueComparer(
                            table = "content_types",
                            columnValueComparer = mapOf(
                                "data_json_with_extra_fields" to VerifierColumnComparer("jsonIgnoreExtraFields"),
                                "data_xml" to VerifierColumnComparer(VERIFIER_XML)
                            )
                        )
                    )
                )
            )
        }

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

class ApplicationEnvironment : Environment(
    "DB" to PostgreSqlContainerSystem()
) {
    fun database() = env<PostgreSqlContainerSystem>().config
    fun sutPort() = 8888.also { System.setProperty("server.port", it.toString()) }
}
