package specs.mq

import io.github.adven27.concordion.extensions.exam.core.utils.findResource
import specs.Specs

class MqPlugin : Mq()
class MqSend : Mq()
class MqPurge : Mq()
class MqCheck : Mq() {
    val dir: String
        get() = "/specs/mq/check/".findResource().path
}

open class Mq : Specs()
