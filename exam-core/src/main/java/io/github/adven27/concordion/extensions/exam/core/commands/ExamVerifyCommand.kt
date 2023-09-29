package io.github.adven27.concordion.extensions.exam.core.commands

import org.concordion.api.AbstractCommand
import org.concordion.api.CommandCall
import org.concordion.api.Element
import org.concordion.api.Evaluator
import org.concordion.api.Fixture
import org.concordion.api.ResultRecorder
import org.concordion.api.listener.AbstractElementEvent

class VerifyFailureEvent<E>(el: Element, val expected: E, val fail: Throwable) : AbstractElementEvent(el)
class VerifySuccessEvent<E, A>(el: Element, val expected: E, val actual: A) : AbstractElementEvent(el)

class FirstSuitableSetUpListener<T>(private vararg val renderers: SuitableSetUpListener<T>) : SetUpListener<T> {
    override fun setUpCompleted(event: SetUpEvent<T>) =
        renderers.first { it.isSuitFor(event.element) }.setUpCompleted(event)
}

abstract class SuitableSetUpListener<T> : SetUpListener<T> {
    abstract fun isSuitFor(element: Element): Boolean
}

abstract class ExamSetUpCommand<T>(
    private val parser: CommandParser<T>,
    private val listener: SetUpListener<T>
) : AbstractCommand() {
    protected fun setUpCompleted(element: Element, target: T) = listener.setUpCompleted(SetUpEvent(element, target))

    abstract fun setUp(target: T, eval: Evaluator)

    override fun setUp(cmd: CommandCall, evaluator: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        parser.parse(cmd, evaluator).apply {
            setUp(this, evaluator)
            setUpCompleted(cmd.element, this)
        }
    }
}

/*
open class ExamAssertCommand<E, A>(
    private val commandParser: CommandParser<E>,
    private val verifier: AwaitVerifier<E, A>,
    private val actualProvider: ActualProvider<E, Pair<Boolean, A>>,
    private val listener: VerifyListener<E, A>
) : ExamCommand() {

    override fun verify(cmd: CommandCall, eval: Evaluator, resultRecorder: ResultRecorder, fixture: Fixture) {
        commandParser.parse(cmd, eval).apply {
            verifier.verify(eval, this) { actualProvider.provide(this) }
                .onSuccess { success(resultRecorder, cmd.element, it.actual, it.expected) }
                .onFailure { failure(resultRecorder, cmd.element, this, it) }
        }
    }

    protected fun success(recorder: ResultRecorder, element: Element, actual: A, expected: E) {
        recorder.record(SUCCESS)
        listener.successReported(VerifySuccessEvent(element, expected, actual))
    }

    protected fun failure(recorder: ResultRecorder, element: Element, expected: E, fail: Throwable) {
        recorder.record(FAILURE)
        listener.failureReported(VerifyFailureEvent(element, expected, fail))
    }
}
*/
