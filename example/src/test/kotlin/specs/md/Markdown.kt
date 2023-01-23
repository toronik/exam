package specs.md

import org.concordion.api.FullOGNL
import specs.Specs

@FullOGNL
class Vars : Md()
class Db : Md()
class Mq : Md()
class Decor : Md()

open class Md : Specs()
