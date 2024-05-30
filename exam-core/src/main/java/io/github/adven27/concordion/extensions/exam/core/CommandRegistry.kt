package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.ExamExtension.Companion.contentVerifier
import io.github.adven27.concordion.extensions.exam.core.commands.EchoCommand
import io.github.adven27.concordion.extensions.exam.core.commands.EqCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamExampleCommand
import io.github.adven27.concordion.extensions.exam.core.commands.SetCommand
import org.concordion.api.Command
import org.concordion.internal.command.VerifyRowsCommand
import org.concordion.internal.command.executeCommand.ExecuteCommand

class CommandRegistry {
    private val commands = mutableMapOf<String, Command>(
        "example" to ExamExampleCommand(),
        "set" to SetCommand(),
        "echo" to EchoCommand(),
        "execute" to ExecuteCommand(),
        "verify-rows" to VerifyRowsCommand(),
        "eq" to EqCommand(contentVerifier("text")),
        "eq-xml" to EqCommand(contentVerifier("xml")),
        "eq-json" to EqCommand(contentVerifier("json"))
    )

    operator fun get(name: String) = commands[name]
    fun commands() = commands.toMap()
    fun register(cmds: Map<String, Command>) = commands.putAll(cmds)
}
