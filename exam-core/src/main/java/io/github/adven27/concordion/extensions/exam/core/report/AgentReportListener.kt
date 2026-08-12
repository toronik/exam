package io.github.adven27.concordion.extensions.exam.core.report

import org.concordion.api.Element
import org.concordion.api.listener.SpecificationProcessingEvent
import org.concordion.api.listener.SpecificationProcessingListener

class AgentReportListener(
    private val accumulator: AgentReportAccumulator,
    private val commandNames: Set<String>
) : SpecificationProcessingListener {

    override fun beforeProcessingSpecification(event: SpecificationProcessingEvent) = Unit

    override fun afterProcessingSpecification(event: SpecificationProcessingEvent) {
        val resource = event.resource.path.removePrefix("/").replace(".html", ".adoc")
        val examples = extractExamples(event.rootElement)
        val status = examples.overallStatus()
        accumulator.add(SpecResult(resource, status, examples, 0))
    }

    private fun extractExamples(root: Element): List<ExampleResult> =
        findElements(root) { it.getAttributeValue("data-summary-success") != null }
            .map { extractExample(it) }

    private fun extractExample(el: Element): ExampleResult {
        val name = el.getFirstChildElement("h3")?.text
            ?: el.getFirstChildElement("h4")?.text
            ?: el.getAttributeValue("id")
            ?: "unnamed"
        val failureCount = el.getAttributeValue("data-summary-failure")?.toIntOrNull() ?: 0
        val exceptionCount = el.getAttributeValue("data-summary-exception")?.toIntOrNull() ?: 0
        val status = Status.from(failureCount, exceptionCount)

        val failures = if (failureCount > 0) extractFailures(el) else emptyList()
        val error = if (exceptionCount > 0) extractError(el) else null

        return ExampleResult(name, status, failures, error)
    }

    private fun extractFailures(el: Element): List<Failure> {
        val errorContainers = findElements(el) { it.getAttributeValue("id")?.startsWith("error-") == true }

        return if (errorContainers.isNotEmpty()) {
            errorContainers.map { extractFromErrorContainer(it) }
        } else {
            extractFromFailureElements(el)
        }
    }

    private fun extractFromErrorContainer(container: Element): Failure {
        val anchor = container.getAttributeValue("id") ?: ""

        val message = findElements(container) { hasClass(it, "failure") && it.localName == "pre" }
            .firstOrNull()
            ?.let { it.getFirstChildElement("code")?.text ?: it.text }
            ?.trim() ?: ""

        val failureDiv = findElements(container) { hasClass(it, "failure") && it.localName == "div" }.firstOrNull()
        val command = failureDiv?.let { detectCommandFromCss(it) }
            ?: detectCommandFromCss(container) ?: "check"

        val context = extractContext(container)

        val del = findElements(container) { it.localName == "del" && hasClass(it, "expected") }.firstOrNull()
        val ins = findElements(container) { it.localName == "ins" && hasClass(it, "actual") }.firstOrNull()

        return Failure(
            command = command,
            context = context,
            message = message,
            expected = del?.text?.trim() ?: "",
            actual = ins?.text?.trim() ?: "",
            htmlAnchor = anchor
        )
    }

    /**
     * Extracts identifying context for the failed check.
     * For HTTP: tries to find the request line (e.g. "POST /conflicts/1/resolve") from nearby elements.
     * Falls back to first line of expected/actual which shows HTTP status.
     */
    private fun extractContext(container: Element) =
        findHttpAttrInAncestors(container) ?: findRequestInSiblings(container) ?: ""

    private fun findHttpAttrInAncestors(el: Element) =
        generateSequence(el) { it.getParentElement() }
            .mapNotNull { it.getAttributeValue("http", NS)?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?.lines()
            ?.first()
            ?.trim()

    private fun findRequestInSiblings(container: Element) =
        container.getParentElement()?.childElements
            ?.takeWhile { it !== container }
            ?.map { it.text.trim() }
            ?.lastOrNull { line -> HTTP_METHODS.any(line::startsWith) }
            ?.lines()
            ?.first()
            ?.trim()

    private fun extractFromFailureElements(el: Element): List<Failure> =
        findElements(el) { hasClass(it, "failure") && it.localName != "del" && it.localName != "ins" }
            .mapNotNull { toFallbackFailure(it, el) }

    private fun toFallbackFailure(failEl: Element, boundary: Element): Failure? {
        val del = failEl.getFirstChildElement("del")
        val ins = failEl.getFirstChildElement("ins")
        val code = failEl.getFirstChildElement("code")

        return extractExpectedActualPair(del, ins, code, failEl)
            .takeUnless { del != null && ins == null && code == null }
            ?.takeUnless { (e, a) -> e.isBlank() && a.isBlank() }
            ?.let { (expected, actual) ->
                Failure(
                    command = detectCommandFromAncestors(failEl, boundary) ?: "check",
                    context = "",
                    message = "",
                    expected = expected,
                    actual = actual,
                    htmlAnchor = ""
                )
            }
    }

    private fun extractExpectedActualPair(del: Element?, ins: Element?, code: Element?, failEl: Element) = when {
        del != null || ins != null -> (del?.text ?: "") to (ins?.text ?: "")
        code != null -> parseErrorSummary(code.text)
        else -> parseErrorSummary(failEl.text)
    }

    private fun detectCommandFromCss(el: Element): String? {
        val cls = el.getAttributeValue("class") ?: return null
        return CSS_COMMAND_PATTERNS.firstOrNull { cls.contains(it) }
    }

    private fun detectCommandFromAncestors(from: Element, boundary: Element): String? =
        generateSequence(from) { it.getParentElement() }
            .takeWhile { it !== boundary }
            .firstNotNullOfOrNull { el ->
                commandNames.firstOrNull { el.getAttributeValue(it, NS) != null } ?: detectCommandFromCss(el)
            }

    private fun parseErrorSummary(text: String): Pair<String, String> {
        ERROR_PATTERN.find(text)?.let { return it.groupValues[1].trim() to it.groupValues[2].trim() }
        return text.trim() to ""
    }

    private fun extractError(el: Element): String? {
        val errorEl = findElements(el) { hasClass(it, "error") }.firstOrNull() ?: return null
        val message = errorEl.getFirstChildElement("span")?.text ?: errorEl.text
        val stackTrace = findElements(errorEl) { hasClass(it, "stackTrace") }.firstOrNull()?.text
        return if (stackTrace != null) "$message\n$stackTrace" else message
    }

    private fun findElements(root: Element, predicate: (Element) -> Boolean): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(el: Element) {
            if (predicate(el)) result.add(el)
            el.childElements.forEach { walk(it) }
        }
        walk(root)
        return result
    }

    private fun List<ExampleResult>.overallStatus(): Status = when {
        any { it.status == Status.ERROR } -> Status.ERROR
        any { it.status == Status.FAIL } -> Status.FAIL
        else -> Status.PASS
    }

    companion object {
        private const val NS = "http://exam.extension.io"
        private val HTTP_METHODS = listOf("GET ", "POST ", "PUT ", "DELETE ", "PATCH ")
        private val CSS_COMMAND_PATTERNS = listOf("http", "mq-check", "db-check", "eq")
        private val ERROR_PATTERN = Regex("expected:\\s*<(.+?)>\\s*but was:\\s*<(.+?)>", RegexOption.DOT_MATCHES_ALL)

        private fun hasClass(el: Element, cls: String): Boolean =
            el.getAttributeValue("class")?.split(" ")?.contains(cls) == true
    }
}
