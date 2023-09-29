package io.github.adven27.concordion.extensions.exam.mq.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.mq.MqTester

abstract class MqCommand<M, R>(var mqTesters: Map<String, MqTester>, attrs: Set<String> = setOf()) :
    ExamCommand<M, R>(setOf(NAME, HEADERS, PARAMS, FROM, CONTENT_TYPE) + attrs) {

    companion object {
        const val NAME = "name"
        const val HEADERS = "headers"
        const val PARAMS = "params"
        const val FROM = "from"
        const val CONTENT_TYPE = "contentType"
    }
}
