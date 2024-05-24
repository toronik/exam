package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.html
import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Element
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.internal.util.Check

class EchoCommand : AbstractCommand() {
    override fun verify(
        commandCall: CommandCall,
        evaluator: Evaluator,
        resultRecorder: ResultRecorder,
        fixture: Fixture
    ) {
        Check.isFalse(commandCall.hasChildCommands(), "Nesting commands inside an 'echo' is not supported")
        val result = evaluator.evaluate(commandCall.expression)
        val element = commandCall.element.takeUnless { it.localName == "td" && it.hasChildren() }
            ?: commandCall.html().deepestChild().el
        if (result != null) {
            element.appendText(result.toString())
        } else {
            val child = Element("em")
            child.appendText("null")
            element.appendChild(child)
        }
    }
}
