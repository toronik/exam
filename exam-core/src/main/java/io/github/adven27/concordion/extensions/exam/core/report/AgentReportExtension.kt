package io.github.adven27.concordion.extensions.exam.core.report

import org.concordion.api.extension.ConcordionExtender
import org.concordion.api.extension.ConcordionExtension

class AgentReportExtension : ConcordionExtension {
    override fun addTo(ex: ConcordionExtender) {
        ex.withSpecificationProcessingListener(AgentReportListener(ACCUMULATOR, COMMAND_NAMES))
    }

    companion object {
        private val ACCUMULATOR = AgentReportAccumulator().apply {
            configure(
                java.nio.file.Path.of(
                    System.getProperty("concordion.output.dir")
                        ?: "${System.getProperty("java.io.tmpdir")}/concordion"
                )
            )
        }

        private val COMMAND_NAMES = setOf(
            "db-check", "db-set", "db-show", "db-clean",
            "mq-check", "mq-set", "mq-clean",
            "http", "eq", "eq-json",
            "set", "echo", "example"
        )
    }
}
