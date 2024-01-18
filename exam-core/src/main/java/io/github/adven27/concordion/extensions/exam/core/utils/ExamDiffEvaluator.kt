package io.github.adven27.concordion.extensions.exam.core.utils

import org.w3c.dom.Attr
import org.w3c.dom.Node
import org.w3c.dom.Text
import org.xmlunit.diff.Comparison
import org.xmlunit.diff.ComparisonResult
import org.xmlunit.diff.ComparisonResult.EQUAL
import org.xmlunit.diff.DifferenceEvaluator

open class ExamDiffEvaluator(private val matcher: XmlPlaceholderAwareMatcher) : DifferenceEvaluator {
    override fun evaluate(comparison: Comparison, outcome: ComparisonResult) = when (outcome) {
        EQUAL -> outcome
        else -> (comparison.controlDetails.target to comparison.testDetails.target).let { (expected, actual) ->
            EQUAL.takeIf { comparable(expected, actual) && matcher.matches(expected.textContent, actual.textContent) }
                ?: outcome
        }
    }

    private fun comparable(expected: Node?, actual: Node?) = text(expected, actual) || attrs(expected, actual)
    private fun attrs(expected: Node?, actual: Node?) = expected is Attr && actual is Attr
    private fun text(expected: Node?, actual: Node?) = expected is Text && actual is Text
}
