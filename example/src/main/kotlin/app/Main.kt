@file:JvmName("SutApp")
@file:Suppress("MagicNumber")

package app

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveOrNull
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ofPattern
import java.util.TimeZone
import kotlin.collections.ArrayList

private val engine = embeddedServer(Netty, port = System.getProperty("server.port").toInt(), host = "") {
    install(ContentNegotiation) {
        jackson {
            setTimeZone(TimeZone.getDefault())
            registerModule(
                JavaTimeModule()
                    .addSerializer(
                        LocalDateTime::class.java,
                        LocalDateTimeSerializer(ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))
                    )
            )
            configure(SerializationFeature.INDENT_OUTPUT, true)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }
    DatabaseFactory.init()
    val jobs: MutableList<Int> = ArrayList()
    val widgetService = WidgetService()
    val jobService = JobService()
    routing {
        post("/jobs") {
            call.application.environment.log.info("trigger job " + call.receiveOrNull<NewWidget>())
            val id = jobService.add()
            jobs.add(id)

            // do some hard work async
            GlobalScope.launch {
                delay(2000L)
                jobService.update(id, "done")
                jobs.remove(id)
            }
            call.respond("""{ "id" : $id }""")
        }

        get("/jobs/{id}") {
            call.application.environment.log.info("is job running?")
            call.respond("""{ "running" : "${jobs.contains(call.parameters["id"]?.toInt()!!)}" }""")
        }

        get("/widgets") {
            call.application.environment.log.info("service.getAll: " + widgetService.getAll().toString())
            call.respond(widgetService.getAll())
        }

        get("/widgets/{id}") {
            val widget = widgetService.getBy(call.parameters["id"]?.toInt()!!)
            if (widget == null) call.respond(HttpStatusCode.NotFound) else call.respond(widget)
        }

        post("/widgets") {
            val widget = call.receive<NewWidget>()
            return@post when {
                widget.name == null -> call.respond(BadRequest, mapOf("error" to "name is required"))
                widget.quantity == null -> call.respond(BadRequest, mapOf("error" to "quantity is required"))
                else -> {
                    try {
                        call.respond(Created, widgetService.add(widget))
                    } catch (expected: Exception) {
                        call.respond(BadRequest, mapOf("error" to expected.message))
                    }
                }
            }
        }

        put("/widgets") {
            val widget = call.receive<NewWidget>()
            val updated = widgetService.update(widget)
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/widgets/{id}") {
            val removed = widgetService.delete(call.parameters["id"]?.toInt()!!)
            if (removed) call.respond(HttpStatusCode.OK) else call.respond(HttpStatusCode.NotFound)
        }

        post("/mirror/body") { call.respond(call.receiveText()) }
        put("/mirror/body") { call.respond(call.receiveText()) }
        post("/mirror/request") { mirrorRequestWithBody() }
        put("/mirror/request") { mirrorRequestWithBody() }
        delete("/mirror/request") { mirrorRequest() }
        get("/mirror/request") { mirrorRequest() }
        get("/mirror/headers") { mirrorHeaders() }
        delete("/mirror/headers") { mirrorHeaders() }

        get("/ignoreJson") {
            call.respond(
                """
                    {
                    "param1":"value1",
                    "param2":"value2",
                    "arr": [{"param3":"value3", "param4":"value4"}, {"param3":"value3", "param4":"value4"}]
                    }
                """.trimIndent()
            )
        }
        static("/ui") {
            resources("files")
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.mirrorRequestWithBody() {
    with(call) {
        respond(
            mapOf(
                request.httpMethod.value to request.uri,
                "body" to receive(Map::class)
            )
        )
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.mirrorRequest() {
    call.respond(mapOf(call.request.httpMethod.value to call.request.uri))
}

private suspend fun PipelineContext<Unit, ApplicationCall>.mirrorHeaders() {
    with(call) {
        respond(
            mapOf(
                request.httpMethod.value to request.uri,
                "Authorization" to request.header("Authorization"),
                "Accept-Language" to request.header("Accept-Language"),
                "cookies" to request.cookies.rawCookies
            )
        )
    }
}

fun main() {
    start()
}

fun start() = engine.start()
fun stop() = engine.stop(1000L, 2000L)

class WidgetService {
    suspend fun getAll(): List<Widget> = DatabaseFactory.dbQuery { Widgets.selectAll().map { toWidget(it) } }

    suspend fun getBy(id: Int): Widget? = DatabaseFactory.dbQuery {
        Widgets.select { (Widgets.id eq id) }.mapNotNull { toWidget(it) }.singleOrNull()
    }

    suspend fun update(widget: NewWidget) = widget.id?.let {
        DatabaseFactory.dbQuery {
            Widgets.update({ Widgets.id eq it }) {
                it[name] = widget.name!!
                it[quantity] = widget.quantity!!
                it[updatedAt] = LocalDateTime.now()
            }
        }
        getBy(it)
    } ?: add(widget)

    suspend fun add(widget: NewWidget): Widget {
        var key = 0
        DatabaseFactory.dbQuery {
            key = (
                Widgets.insert {
                    it[name] = if (widget.name!!.isBlank()) throw BlankNotAllowed() else widget.name
                    it[quantity] = widget.quantity!!
                    it[updatedAt] = LocalDateTime.now()
                } get Widgets.id
                )
        }
        return getBy(key)!!
    }

    class BlankNotAllowed : RuntimeException("blank value not allowed")

    suspend fun delete(id: Int): Boolean = DatabaseFactory.dbQuery { Widgets.deleteWhere { Widgets.id eq id } > 0 }

    private fun toWidget(row: ResultRow) = Widget(
        id = row[Widgets.id],
        name = row[Widgets.name],
        quantity = row[Widgets.quantity],
        updatedAt = row[Widgets.updatedAt]
    )
}

class JobService {
    suspend fun add(): Int {
        var key = 0
        DatabaseFactory.dbQuery {
            key = (JobResult.insert {} get JobResult.id)
        }
        return key
    }

    suspend fun update(id: Int, jobResult: String) {
        DatabaseFactory.dbQuery {
            JobResult.update({ JobResult.id eq id }) {
                it[result] = jobResult
            }
        }
    }
}
