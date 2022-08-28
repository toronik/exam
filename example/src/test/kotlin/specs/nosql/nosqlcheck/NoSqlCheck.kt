package specs.nosql.nosqlcheck

import io.github.adven27.concordion.extensions.exam.core.findResource
import specs.Specs

class NoSqlFailures : NoSqlCheck()

open class NoSqlCheck : Specs() {

    val filesDir: String
        get() = "/specs/nosql/nosqlcheck/data/".findResource().path

    fun cleanCollection(collection: String) {
        nosqlTester.clean(listOf(collection))
    }
}
