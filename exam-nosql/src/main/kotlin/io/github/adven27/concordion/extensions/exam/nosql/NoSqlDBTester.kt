package io.github.adven27.concordion.extensions.exam.nosql

import io.github.adven27.concordion.extensions.exam.nosql.commands.NoSqlDocument

interface NoSqlDBTester {
    fun set(collection: String, documents: List<NoSqlDocument>)
}

class NOOPTester : NoSqlDBTester {

    @Suppress("EmptyFunctionBlock")
    override fun set(collection: String, documents: List<NoSqlDocument>){
    }
}
