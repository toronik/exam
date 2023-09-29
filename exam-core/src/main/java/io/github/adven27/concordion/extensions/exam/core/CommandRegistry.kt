package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.commands.BeforeEachExampleCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamExampleCommand
import io.github.adven27.concordion.extensions.exam.core.commands.GivenCommand
import io.github.adven27.concordion.extensions.exam.core.commands.JsonEqualsCommand
import io.github.adven27.concordion.extensions.exam.core.commands.JsonEqualsFileCommand
import io.github.adven27.concordion.extensions.exam.core.commands.SetVarCommand
import io.github.adven27.concordion.extensions.exam.core.commands.TextEqualsCommand
import io.github.adven27.concordion.extensions.exam.core.commands.TextEqualsFileCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ThenCommand
import io.github.adven27.concordion.extensions.exam.core.commands.AwaitCommand
import io.github.adven27.concordion.extensions.exam.core.commands.WhenCommand
import io.github.adven27.concordion.extensions.exam.core.commands.XmlEqualsCommand
import io.github.adven27.concordion.extensions.exam.core.commands.XmlEqualsFileCommand
import org.concordion.api.Command

class CommandRegistry(jsonVerifier: ContentVerifier, xmlVerifier: ContentVerifier) {
    private val commands = mutableMapOf<String, Command>(
        "given" to GivenCommand(),
        "when" to WhenCommand(),
        "then" to ThenCommand(),

        "example" to ExamExampleCommand(),
        "before-each" to BeforeEachExampleCommand(),

        "set" to SetVarCommand("pre"),
        "await" to AwaitCommand("span"),

//        "json-check" to JsonCheckCommand(jsonVerifier),
//        "xml-check" to XmlCheckCommand(xmlVerifier),

        "equals" to TextEqualsCommand(),
        "equalsFile" to TextEqualsFileCommand(),
        "xmlEquals" to XmlEqualsCommand(),
        "xmlEqualsFile" to XmlEqualsFileCommand(),
        "jsonEquals" to JsonEqualsCommand(),
        "jsonEqualsFile" to JsonEqualsFileCommand()
    )

    operator fun get(name: String) = commands[name]
    fun commands() = commands.toMap()
    fun register(cmds: Map<String, Command>) = commands.putAll(cmds)
}
