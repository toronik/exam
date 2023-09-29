package specs.core

import org.concordion.api.FullOGNL
import specs.Specs

class CoreDecorTest : Specs()
class CoreSetTest : Specs()

@FullOGNL
class CoreVerifyTest : Specs() {
    fun lowercase(name: String): Result = name.lowercase().let {
        Result(it, """{ "result": "$it" }""", """<result>$it</result>""")
    }

    val emptyString = ""

    data class Result(val text: String, val json: String, val xml: String)
}
