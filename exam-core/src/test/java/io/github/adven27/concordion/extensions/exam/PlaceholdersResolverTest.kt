package io.github.adven27.concordion.extensions.exam

import io.github.adven27.concordion.extensions.exam.core.resolveJson
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.DateWithin.Companion.PARAMS_SEPARATOR
import io.github.adven27.concordion.extensions.exam.core.utils.toDate
import io.github.adven27.concordion.extensions.exam.core.utils.toString
import org.assertj.core.api.Assertions
import org.concordion.internal.FixtureInstance
import org.concordion.internal.OgnlEvaluator
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.startsWith
import org.junit.Test
import java.time.LocalDateTime
import java.util.Date
import kotlin.test.assertEquals

class PlaceholdersResolverTest {
    private val eval = OgnlEvaluator(FixtureInstance(Object()))

    private companion object {
        const val MATCHES = "\${json-unit.matches"
    }

    @Test
    fun canUseConcordionVars() {
        eval.setVariable("#value", 3)

        assertThat(eval.resolveJson("{{value}}"), `is`("3"))
        assertEquals(eval.resolveToObj("{{value}}"), 3)
    }

    @Test
    fun resolveToObj_shouldResolveFormattedConcordionVarToString() {
        val date = Date()
        eval.setVariable("#value", date)

        val expected = date.toString("dd.MM.yyyy HH:mm")
        assertThat(eval.resolveToObj("{{dateFormat value \"dd.MM.yyyy HH:mm\"}}").toString(), `is`(expected))
    }

    @Test
    fun canUseJsonUnitMatcherAliases() {
        assertThat(
            eval.resolveJson("{{formattedAs 'dd.MM.yyyy'}}"),
            `is`("$MATCHES:formattedAs}dd.MM.yyyy")
        )
        assertThat(
            eval.resolveJson("{{formattedAs 'dd.MM.yyyy HH:mm'}}"),
            `is`("$MATCHES:formattedAs}dd.MM.yyyy HH:mm")
        )
        assertThat(
            eval.resolveJson("{{formattedAndWithin 'yyyy-MM-dd' '1d' (date '1951-05-13')}}"),
            `is`("$MATCHES:formattedAndWithin}yyyy-MM-dd${PARAMS_SEPARATOR}1d${PARAMS_SEPARATOR}1951-05-13T00:00")
        )
    }

    @Test
    fun canUseJsonUnitMatcherAliasWithRegexp() {
        val time = "HH:mm:ss[.SSSSSSSSS][.SSSSSS][.SSS]"
        assertThat(
            eval.resolveJson("{{formattedAs 'yyyy-MM-dd\\'T\\'$time'}}"),
            `is`("$MATCHES:formattedAs}yyyy-MM-dd'T'$time")
        )
        assertThat(
            eval.resolveJson("{{formattedAndWithin 'yyyy-MM-dd\\'T\\'$time' '1d'}}"),
            startsWith("$MATCHES:formattedAndWithin}yyyy-MM-dd'T'$time${PARAMS_SEPARATOR}1d")
        )
    }

    @Test
    fun examDateVariables() {
        val p = "dd.MM.yyyy'T'hh:mm:ss"
        assertThat(eval.resolveJson("{{now \"$p\"}}"), `is`(Date().toString(p)))
        Assertions.assertThat(eval.resolveToObj("{{now}}") as Date)
            .isCloseTo(Date(), 1000)
        Assertions.assertThat(eval.resolveToObj("{{now minus='1 d'}}") as Date)
            .isCloseTo(LocalDateTime.now().minusDays(1).toDate(), 1000)
    }
}
