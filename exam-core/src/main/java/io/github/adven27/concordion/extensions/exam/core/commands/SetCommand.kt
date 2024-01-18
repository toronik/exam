package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.internal.command.SetCommand
import org.concordion.internal.util.Check

open class SetCommand : SetCommand() {
    override fun setUp(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        Check.isFalse(cmd.hasChildCommands(), "Nesting commands inside a 'set' is not supported")
        val value = eval.resolveToObj(cmd.element.text.trim())
        eval.setVariable(cmd.expression.takeIf { it.startsWith("#") } ?: "#${cmd.expression}", value)
        cmd.element.swapText(value.toString())
    }
}
