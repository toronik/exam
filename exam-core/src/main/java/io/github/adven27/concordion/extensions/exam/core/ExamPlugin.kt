package io.github.adven27.concordion.extensions.exam.core

import org.concordion.api.Command

interface ExamPlugin {
    fun commands(): Map<String, Command>
    fun setUp()
    fun tearDown()

    abstract class NoSetUp : ExamPlugin {
        override fun setUp() = Unit
        override fun tearDown() = Unit
    }
}
