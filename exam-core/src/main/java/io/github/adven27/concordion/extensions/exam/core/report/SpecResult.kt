package io.github.adven27.concordion.extensions.exam.core.report

data class SpecResult(
    val resource: String,
    val status: Status,
    val examples: List<ExampleResult>,
    val durationMs: Long
)

data class ExampleResult(
    val name: String,
    val status: Status,
    val failures: List<Failure>,
    val error: String?
)

data class Failure(
    val command: String,
    val context: String,
    val message: String,
    val expected: String,
    val actual: String,
    val htmlAnchor: String
)

enum class Status {
    PASS,
    FAIL,
    ERROR
    ;

    companion object {
        fun from(failures: Int, exceptions: Int): Status = when {
            exceptions > 0 -> ERROR
            failures > 0 -> FAIL
            else -> PASS
        }
    }
}
