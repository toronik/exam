package io.github.adven27.concordion.extension.exam.nosql.elastic

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.clients.json.JsonData
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.adven27.concordion.extension.exam.nosql.elastic.ElasticTester.Companion.NO_ID_INDICATOR
import io.github.adven27.concordion.extensions.exam.core.utils.asString
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import mu.KLogging
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import java.io.StringReader
import java.util.Objects.nonNull
import javax.net.ssl.SSLContext

@Suppress("TooManyFunctions")
class ElasticTester(
    private val url: String,
    private val port: Int,
    private val user: String?,
    private val password: String?,
    private val sslContext: SSLContext?,
    // Documents need some time to start appearing in results of search requests
    private val syncTimeoutMs: Long = 1000
) : NoSqlDBTester {

    constructor(url: String, port: Int) : this(
        url = url,
        port = port,
        user = NO_AUTH,
        password = NO_AUTH,
        sslContext = null
    )

    constructor() : this(
        url = "localhost",
        port = 9200
    )

    companion object : KLogging() {
        const val NO_ID_INDICATOR = "NO_ID"
        const val NO_AUTH = "NO_AUTH"
        const val ID_FIELD = "_id"
    }

    private val client = initClient()
    private val mapper = ObjectMapper()

    fun client() = client
    private fun initClient(): ElasticsearchClient {
        val credentialsProvider = credentialsProvider()
        val client = RestClient.builder(httpHost())
            .setHttpClientConfigCallback { httpClientBuilder ->
                if (nonNull(sslContext)) {
                    httpClientBuilder.setSSLContext(sslContext)
                }
                if (nonNull(credentialsProvider)) {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                }
                httpClientBuilder
            }
            .build()
        val transport: ElasticsearchTransport = RestClientTransport(client, JacksonJsonpMapper())
        return ElasticsearchClient(transport)
    }

    private fun httpHost() = if (nonNull(sslContext)) {
        HttpHost(url, port, "https")
    } else {
        HttpHost(url, port)
    }

    private fun credentialsProvider(): BasicCredentialsProvider? {
        return if (user != NO_AUTH) {
            BasicCredentialsProvider().apply {
                setCredentials(AuthScope.ANY, UsernamePasswordCredentials(user, password))
            }
        } else {
            null
        }
    }

    override fun set(collection: String, documents: List<NoSqlDocument>) {
        createIndexIfDoesNotExists(collection)
        clean(listOf(collection))
        insert(collection, documents)
    }

    override fun setWithAppend(collection: String, documents: List<NoSqlDocument>) {
        createIndexIfDoesNotExists(collection)
        insert(collection, documents)
    }

    private fun createIndexIfDoesNotExists(index: String) {
        if (!isIndexExists(index)) {
            createIndex(index)
        }
    }

    private fun createIndex(index: String) {
        client.indices().create(CreateIndexRequest.Builder().index(index).build())
    }

    private fun insert(index: String, documents: List<NoSqlDocument>) {
        client.bulk(
            BulkRequest.Builder()
                .operations(documents.map { it.toBulkOperation(index) })
                .build()
        )
        Thread.sleep(syncTimeoutMs)
    }

    override fun read(collection: String): List<NoSqlDocument> {
        return client.search(searchAllRequest(collection), ObjectNode::class.java)
            .hits()
            .hits()
            .map {
                NoSqlDocument(mapper.writeValueAsString(it.source()?.put(ID_FIELD, it.id())))
            }
    }

    private fun isIndexExists(index: String) =
        client.indices()
            .exists(ExistsRequest.Builder().index(index).build()).value()

    private fun searchAllRequest(index: String) =
        SearchRequest.Builder()
            .index(listOf(index))
            .query(QueryBuilders.matchAll().build()._toQuery())
            .build()

    override fun clean(collections: Collection<String>) {
        collections.forEach { index ->
            client.deleteByQuery(
                DeleteByQueryRequest.Builder()
                    .index(index)
                    .query(QueryBuilders.matchAll().build()._toQuery())
                    .build()
            )
        }
        Thread.sleep(syncTimeoutMs)
    }
}

private fun NoSqlDocument.toBulkOperation(index: String): BulkOperation {
    // using JsonData as workaround for https://github.com/elastic/elasticsearch-java/issues/251
    val idAndDoc = asJsonDocWithId()
    val indexOperation = if (idAndDoc.first == NO_ID_INDICATOR) {
        indexOperation(index, idAndDoc.second)
    } else {
        indexOperationWithId(index, idAndDoc.first, idAndDoc.second)
    }
    return BulkOperation.Builder().index(indexOperation).build()
}

fun indexOperationWithId(index: String, id: String, document: JsonData): IndexOperation<JsonData> =
    IndexOperation.Builder<JsonData>()
        .index(index)
        .id(id)
        .document(document)
        .build()

fun indexOperation(index: String, document: JsonData): IndexOperation<JsonData> =
    IndexOperation.Builder<JsonData>()
        .index(index)
        .document(document)
        .build()

private fun NoSqlDocument.asJsonDocWithId(): Pair<String, JsonData> {
    val mapper = ObjectMapper()
    val json = mapper.readTree(body)
    // warning: this actually mutates state of initial json node
    val id = (json as ObjectNode).remove("_id")?.asString()?.replace("\"", "")
        ?: NO_ID_INDICATOR
    val jsonData = JsonData.from(StringReader(mapper.writeValueAsString(json)))
    return id to jsonData
}
