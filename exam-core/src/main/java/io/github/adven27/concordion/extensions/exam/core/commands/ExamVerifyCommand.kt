package io.github.adven27.concordion.extensions.exam.core.commands

import io.github.adven27.concordion.extensions.exam.core.html.Html
import org.concordion.api.Element
import org.concordion.api.Result.FAILURE
import org.concordion.api.Result.SUCCESS
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.AssertEqualsListener
import org.concordion.api.listener.AssertFailureEvent
import org.concordion.api.listener.AssertSuccessEvent
import org.concordion.internal.util.Announcer

open class ExamVerifyCommand(name: String, tag: String, listener: AssertEqualsListener) : ExamCommand(name, tag) {
    private val listeners = Announcer.to(AssertEqualsListener::class.java)

    init {
        listeners.addListener(listener)
    }

    protected fun success(resultRecorder: ResultRecorder, element: Element) {
        resultRecorder.record(SUCCESS)
        listeners.announce().successReported(AssertSuccessEvent(element))
    }

    protected fun success(resultRecorder: ResultRecorder, element: Html) = success(resultRecorder, element.el())

    protected fun failure(resultRecorder: ResultRecorder, element: Element, actual: Any, expected: String) {
        resultRecorder.record(FAILURE)
        listeners.announce().failureReported(AssertFailureEvent(element, expected, actual))
    }

    protected fun failure(resultRecorder: ResultRecorder, element: Html, actual: Any, expected: String) =
        failure(resultRecorder, element.el(), actual, expected)

    protected fun ResultRecorder.pass(element: Html): Html = element.also { success(this, it) }

    protected fun ResultRecorder.failure(element: Html, actual: String, expected: String) =
        element.also { failure(this, it, actual, expected) }

    protected fun ResultRecorder.check(
        root: Html,
        actual: String,
        expected: String,
        test: (String, String) -> Boolean = { a, e -> a == e }
    ) =
        if (test(actual, expected)) {
            root.text(expected)
            this.pass(root)
        } else this.failure(root, actual, expected)
}
