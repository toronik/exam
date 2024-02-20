package io.github.adven27.concordion.extensions.exam

import io.github.adven27.concordion.extensions.exam.core.handlebars.ExamHelper
import io.github.adven27.concordion.extensions.exam.core.handlebars.date.DateHelpers
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.MatcherHelpers
import io.github.adven27.concordion.extensions.exam.core.handlebars.misc.MiscHelpers
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.parseDate
import io.github.adven27.concordion.extensions.exam.core.utils.parseLocalDate
import io.github.adven27.concordion.extensions.exam.core.utils.parseLocalDateTime
import io.github.adven27.concordion.extensions.exam.core.utils.toDate
import org.assertj.core.api.Assertions.assertThat
import org.concordion.internal.FixtureInstance
import org.concordion.internal.OgnlEvaluator
import org.junit.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

class HandlebarsResolverTest {
    private val eval = OgnlEvaluator(FixtureInstance(Html("div").el))

    @Test
    fun resolve_with_vars() {
        assertEquals(
            "var = v, date = ${LocalDate.now().toDate()}",
            sut("{{resolve 'var = {{var}}, date = {{td}}' var='v' td='{{today}}'}}")
        )
        assertEquals(
            "today is ${LocalDate.now().toDate()}; var1 is v; var2 is ${LocalDate.now().toDate()}",
            sut("{{file '/hb/resolve-file-vars.txt' var1='v' var2='{{today}}'}}")
        )
    }

    @Test
    fun parse() {
        assertEquals(
            "2019-06-30T09:10:00".parseDate("yyyy-MM-dd'T'HH:mm:ss"),
            sut("{{parse \"2019-06-30T09:10:00\"}}")
        )
        assertEquals(
            "2019-06-30T00:00:00".parseDate("yyyy-MM-dd'T'HH:mm:ss"),
            sut("{{parse \"2019-06-30\"}}")
        )
    }

    @Test
    fun format() {
        val expDate = "2019-06-30"
        val expDateTime = "${expDate}T09:10:00"
        eval.setVariable("#d", expDateTime.parseDate())
        eval.setVariable("#ldt", expDateTime.parseLocalDateTime())
        eval.setVariable("#ld", expDate.parseLocalDate())

        assertEquals(expDateTime, sut("{{format d}}"))
        assertEquals(expDateTime, sut("{{format ldt}}"))
        assertEquals(expDate, sut("{{format ld}}"))
    }

    @Test
    fun now() {
        assertThat(eval.resolveToObj("{{now}}") as Date).isCloseTo(Date(), 5000)
    }

    @Test
    fun `date helpers`() {
        DateHelpers.entries.forEach {
            val expected = it.expected
            val result = helper(it)
            when (expected) {
                is Date -> assertThat(if (result is String) result.parseDate() else result as Date)
                    .describedAs("Failed helper: %s", it).isCloseTo(expected, 5000)

                else -> assertEquals(expected, result, "Failed helper: $it")
            }
        }
    }

    @Test
    fun `misc helpers`() {
        MiscHelpers.entries.forEach { assertEquals(it.expected, helper(it), "Failed helper: $it") }
    }

    @Test
    fun `matcher helpers`() {
        MatcherHelpers.entries.forEach { assertEquals(it.expected, helper(it), "Failed helper: $it") }
    }

    private fun helper(h: ExamHelper): Any? {
        h.context.forEach { (t, u) -> eval.setVariable("#$t", u) }
        return eval.resolveToObj(h.example)
    }

    private fun sut(placeholder: String) = eval.resolveToObj(placeholder)
}
