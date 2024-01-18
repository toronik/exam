package io.github.adven27.concordion.extensions.exam.mq

import io.github.adven27.concordion.extensions.exam.core.Content

interface MqTester {
    fun start()
    fun stop()
    fun send(message: Message)
    fun receive(): List<Message>
    fun purge()
    fun accumulateOnRetries(): Boolean = true

    open class NOOP : MqTester {
        override fun start() = Unit
        override fun stop() = Unit
        override fun send(message: Message) = Unit
        override fun receive(): List<Message> = listOf()
        override fun purge() = Unit
    }

    open class Message @JvmOverloads constructor(
        val body: String = "",
        open val headers: Map<String, String?> = mapOf(),
        open val params: Map<String, String> = mapOf()
    ) {
        override fun toString() = "params: $params\nheaders: ${printHeaders()}\nbody:\n$body"
        private fun printHeaders() = headers.takeIf { it.isNotEmpty() }
            ?.entries
            ?.joinToString("\n", prefix = "\n") { it.key + ": " + it.value }
    }

    open class TypedMessage(
        open val content: Content,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String> = emptyMap()
    ) : Message(content.body, headers, params)
}
