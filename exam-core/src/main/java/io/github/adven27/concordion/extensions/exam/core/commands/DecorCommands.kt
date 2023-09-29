package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.html.tag
import org.concordion.api.CommandCall

class GivenCommand : RenderCommand() {
    override fun render(commandCall: CommandCall) = pLeadAndHr(commandCall.html().css("given"), "text-primary", "Given")
}

class WhenCommand : RenderCommand() {
    override fun render(commandCall: CommandCall) = pLeadAndHr(commandCall.html().css("when"), "text-warning", "When")
}

class ThenCommand : RenderCommand() {
    override fun render(commandCall: CommandCall) = pLeadAndHr(commandCall.html().css("then"), "text-success", "Then")
}

private fun pLeadAndHr(html: Html, style: String, title: String) {
    html.prependChild(tag("hr")).prependChild(tag("p").css("lead $style").text(title))
}
