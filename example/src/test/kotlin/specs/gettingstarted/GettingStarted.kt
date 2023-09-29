package specs.gettingstarted

import org.concordion.api.FullOGNL
import specs.Specs

@FullOGNL
class GettingStarted : Specs() {
    fun isDone(id: String) = true //TODO !get("/jobs/$id").body().jsonPath().getBoolean("running")
}
