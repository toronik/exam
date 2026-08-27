package io.github.adven27.concordion.extensions.exam.core.report

import org.concordion.api.listener.SpecificationProcessingEvent
import org.concordion.api.listener.SpecificationProcessingListener

/** Records one [SpecResult] per processed specification, for the file written at shutdown. */
class AgentReportListener(
    private val accumulator: AgentReportAccumulator,
    commandNames: Set<String>
) : SpecificationProcessingListener {

    private val parser = ExampleParser(commandNames)

    override fun beforeProcessingSpecification(event: SpecificationProcessingEvent) = Unit

    override fun afterProcessingSpecification(event: SpecificationProcessingEvent) {
        val resource = event.resource.path.removePrefix("/").replace(".html", ".adoc")
        val examples = parser.examples(event.rootElement)
        accumulator.add(SpecResult(resource, parser.statusOf(examples), examples, 0))
    }
}
