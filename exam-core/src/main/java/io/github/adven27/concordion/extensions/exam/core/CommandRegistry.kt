package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.commands.BeforeEachExampleCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamBeforeExampleCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamExampleCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ExamplesSummaryCommand
import io.github.adven27.concordion.extensions.exam.core.commands.GivenCommand
import io.github.adven27.concordion.extensions.exam.core.commands.JsonCheckCommand
import io.github.adven27.concordion.extensions.exam.core.commands.MainCommand
import io.github.adven27.concordion.extensions.exam.core.commands.SetVarCommand
import io.github.adven27.concordion.extensions.exam.core.commands.ThenCommand
import io.github.adven27.concordion.extensions.exam.core.commands.WaitCommand
import io.github.adven27.concordion.extensions.exam.core.commands.WhenCommand
import io.github.adven27.concordion.extensions.exam.core.commands.XmlCheckCommand

class CommandRegistry(jsonVerifier: ContentVerifier, xmlVerifier: ContentVerifier) {
    private val commands = mutableListOf(
        MainCommand(),
        GivenCommand(),
        WhenCommand(),
        ThenCommand(),

        ExamExampleCommand("div"),
        ExamBeforeExampleCommand("div"),
        BeforeEachExampleCommand("div"),
        ExamplesSummaryCommand("summary", "div"),

        SetVarCommand(),
        WaitCommand("span"),

        JsonCheckCommand("json-check", "div", jsonVerifier),
        XmlCheckCommand("xml-check", "div", xmlVerifier)
    )

    fun getBy(name: String): ExamCommand? = commands.firstOrNull { it.name() == name }

    fun register(cmds: List<ExamCommand>) {
        commands.addAll(cmds)
    }

    fun commands(): List<ExamCommand> = ArrayList(commands)
}
