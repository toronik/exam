package io.github.adven27.concordion.extension.exam.nosql.mongo

import com.mongodb.client.MongoClients
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDBTester
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import org.bson.Document

@Suppress("unused")
class MongoTester(
    connectionString: String,
    dbName: String
) : NoSqlDBTester {

    private val client = MongoClients.create(connectionString)
    private val db = client.getDatabase(dbName)

    override fun set(collection: String, documents: List<NoSqlDocument>) {
        clean(listOf(collection))
        insert(collection, documents)
    }

    override fun setWithAppend(collection: String, documents: List<NoSqlDocument>) {
        insert(collection, documents)
    }

    private fun insert(collection: String, documents: List<NoSqlDocument>) {
        db.getCollection(collection)
            .insertMany(
                documents.map { Document.parse(it.body) }
            )
    }

    override fun read(collection: String): List<NoSqlDocument> =
        db.getCollection(collection)
            .find()
            .map { NoSqlDocument(it.toJson()) }
            .toCollection(ArrayList<NoSqlDocument>())

    override fun clean(collections: Collection<String>) {
        collections.forEach {
            db.getCollection(it).deleteMany(Document())
        }
    }
}
