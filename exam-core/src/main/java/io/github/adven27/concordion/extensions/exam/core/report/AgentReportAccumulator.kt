package io.github.adven27.concordion.extensions.exam.core.report

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.writeText

open class AgentReportAccumulator {
    private val results = ConcurrentLinkedQueue<SpecResult>()
    private var outputDir: Path? = null
    private val hookRegistered = AtomicBoolean(false)

    fun configure(outputDir: Path) {
        this.outputDir = outputDir
        if (hookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread(::flush))
        }
    }

    open fun add(result: SpecResult) {
        results.add(result)
    }

    private fun flush() {
        val dir = outputDir ?: return
        if (results.isEmpty()) return
        val all = results.toList()
        val report = AgentReportRenderer.render(all)
        dir.toFile().mkdirs()
        val file = dir.resolve("agent-report.md")
        file.writeText(report)
        val failed = all.count { it.status != Status.PASS }
        println("\n[exam] Agent report: $file ($failed failed / ${all.size} total)")
    }
}
