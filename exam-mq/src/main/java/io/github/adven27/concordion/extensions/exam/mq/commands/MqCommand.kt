package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.mq.MqTester

abstract class MqCommand<M, R>(
    var testers: Map<String, MqTester>,
    attrs: Set<String> = setOf()
) : ExamCommand<M, R>(attrs) {

    companion object {
        fun Map<String, MqTester>.getOrFail(mqName: String?): MqTester =
            requireNotNull(this[mqName]) { "MQ with name '$mqName' not registered in MqPlugin" }
    }
}
