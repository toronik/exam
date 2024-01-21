package io.github.adven27.concordion.extensions.exam.mq

import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCheckCommand
import io.github.adven27.concordion.extensions.exam.mq.commands.MqCleanCommand
import io.github.adven27.concordion.extensions.exam.mq.commands.MqSetCommand
import org.concordion.api.Command

class MqPlugin(
    private val testers: Map<String, MqTester>,
    private val override: Map<String, Command> = mapOf()
) : ExamPlugin {

    constructor(vararg testers: Pair<String, MqTester>) : this(testers.toMap())

    override fun commands(): Map<String, Command> = mapOf(
        "mq-set" to MqSetCommand(testers),
        "mq-check" to MqCheckCommand(testers),
        "mq-clean" to MqCleanCommand(testers)
    ) + override

    override fun setUp() = testers.forEach { (_, t) -> t.start() }
    override fun tearDown() = testers.forEach { (_, t) -> t.stop() }
}
