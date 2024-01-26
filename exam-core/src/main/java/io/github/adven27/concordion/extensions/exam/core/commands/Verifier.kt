package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.descendantTextContainer
import org.concordion.api.Element
import org.concordion.api.Evaluator

interface Verifier<E, A> {
    fun verify(eval: Evaluator, expected: E, actual: (E) -> A): Check<E, A>
    data class Check<E, A>(val expected: E, val actual: A?, val fail: Throwable? = null)
}

fun String.expression() = substring(indexOf("}") + 1).trim()
fun Element.swapText(value: String) {
    Html(descendantTextContainer(this)).removeChildren().text(value).el.appendNonBreakingSpaceIfBlank()
}
