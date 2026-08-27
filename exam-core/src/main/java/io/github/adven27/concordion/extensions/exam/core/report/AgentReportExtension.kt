package io.github.adven27.concordion.extensions.exam.core.report

import org.concordion.api.extension.ConcordionExtender
import org.concordion.api.extension.ConcordionExtension
import java.nio.file.Path

/**
 * On by default: `AbstractSpecs` registers it, so a fixture gets the report without knowing the
 * extension exists. That is the point - a reader who has to be told the report is available has
 * already lost the time the report was meant to save. Opt out with `-Dexam.agentReport=false`.
 */
class AgentReportExtension : ConcordionExtension {
    override fun addTo(ex: ConcordionExtender) {
        if (!ENABLED) return
        ex.withSpecificationProcessingListener(AgentReportListener(ACCUMULATOR, EXAM_COMMANDS))
        ex.withExampleListener(CURRENT_EXAMPLE)
    }

    companion object {
        const val PROP_ENABLED = "exam.agentReport"

        @JvmField
        val ENABLED: Boolean = System.getProperty(PROP_ENABLED, "true").toBoolean()

        /**
         * Left unconfigured when disabled, so nothing writes a file and no message points a reader
         * at one that will not be there.
         */
        @JvmField
        val ACCUMULATOR = AgentReportAccumulator().apply {
            if (ENABLED) configure(Path.of(System.getProperty("concordion.output.dir") ?: defaultDir()))
        }

        private fun defaultDir() = "${System.getProperty("java.io.tmpdir")}/concordion"

        @JvmField
        val CURRENT_EXAMPLE = CurrentExample()
    }
}
