import io.github.adven27.concordion.extensions.exam.core.AbstractSpecs
import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.JsonVerifier
import net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS

class Specs : AbstractSpecs() {

    override fun init() = ExamExtension(
        //...
    ).withVerifiers(
        "jsonIgnoreExtraFields" to JsonVerifier { it.withOptions(IGNORING_EXTRA_FIELDS) }
    )
}
