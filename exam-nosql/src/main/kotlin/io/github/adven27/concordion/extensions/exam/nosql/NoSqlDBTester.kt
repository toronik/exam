package io.github.adven27.concordion.extensions.exam.nosql

interface NoSqlDBTester {
    fun set(collection: String, documents: List<NoSqlDocument>)
    fun read(collection: String): List<NoSqlDocument>
    fun clean(collections: Collection<String>)
}

class NoSqlDefaultTester : NoSqlDBTester {

    private val docs: MutableMap<String, List<NoSqlDocument>> = HashMap()

    override fun set(collection: String, documents: List<NoSqlDocument>) {
        if (docs.containsKey(collection)) {
            docs[collection] = docs[collection]!! + documents
        } else {
            docs[collection] = documents
        }
    }

    override fun read(collection: String): List<NoSqlDocument> {
        return docs[collection] ?: emptyList()
    }

    override fun clean(collections: Collection<String>) {
        collections.forEach { c -> docs.remove(c) }
    }
}
