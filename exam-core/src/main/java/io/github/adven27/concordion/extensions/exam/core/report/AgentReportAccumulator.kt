package io.github.adven27.concordion.extensions.exam.core.report

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.writeText

open class AgentReportAccumulator {
    private val results = ConcurrentLinkedQueue<SpecResult>()
    private var outputDir: Path? = null
    private val hookRegistered = AtomicBoolean(false)

    /** Set once the output directory is known; before that there is nothing to point a reader at. */
    val reportFile: Path? get() = outputDir?.resolve(REPORT_NAME)

    /** Last result recorded on this thread, kept only to recognise a repeat of it. */
    private val onThisThread = ThreadLocal<SpecResult?>()

    fun configure(outputDir: Path) {
        this.outputDir = outputDir
        if (hookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread(::flush))
        }
    }

    open fun add(result: SpecResult) {
        // A fixture that also registers the extension by hand gets two listeners on one extender,
        // and a retried example is processed twice; both would double-count. Identical results in a
        // row on one thread are the same processing, not two runs of it.
        if (onThisThread.get() == result) return
        onThisThread.set(result)
        results.add(result)
    }

    private fun flush() {
        val dir = outputDir ?: return
        if (results.isEmpty()) return
        val all = results.toList()
        val report = AgentReportRenderer.render(all)
        dir.toFile().mkdirs()
        val file = dir.resolve(REPORT_NAME)
        file.writeText(report)
        val failed = all.count { it.status != Status.PASS }
        println("\n" + AgentReportHint.forConsole(file, failed, all.size))
    }

    private companion object {
        const val REPORT_NAME = "agent-report.md"
    }
}
