package io.github.adven27.concordion.extensions.exam

import io.github.adven27.concordion.extensions.exam.core.resolve
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import io.github.adven27.concordion.extensions.exam.core.utils.DateFormattedAndWithin.Companion.PARAMS_SEPARATOR
import org.concordion.internal.FixtureInstance
import org.concordion.internal.OgnlEvaluator
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.startsWith
import org.junit.Test
import kotlin.test.assertEquals

class PlaceholdersResolverTest {
    private val eval = OgnlEvaluator(FixtureInstance(Object()))

    private companion object {
        const val MATCHES = "\${test-unit.matches"
    }

    @Test
    fun canUseConcordionVars() {
        eval.setVariable("#value", 3)

        assertThat(eval.resolve("{{value}}"), `is`("3"))
        assertEquals(eval.resolveToObj("{{value}}"), 3)
    }

    @Test
    fun canUseJsonUnitMatcherAliases() {
        assertThat(
            eval.resolve("{{formattedAs 'dd.MM.yyyy'}}"),
            `is`("$MATCHES:formattedAs}dd.MM.yyyy")
        )
        assertThat(
            eval.resolve("{{formattedAs 'dd.MM.yyyy HH:mm'}}"),
            `is`("$MATCHES:formattedAs}dd.MM.yyyy HH:mm")
        )
        assertThat(
            eval.resolve("{{formattedAndWithin 'yyyy-MM-dd' '1d' (parse '1951-05-13')}}"),
            `is`("$MATCHES:formattedAndWithin}yyyy-MM-dd${PARAMS_SEPARATOR}1d${PARAMS_SEPARATOR}1951-05-13T00:00")
        )
    }

    @Test
    fun canUseJsonUnitMatcherAliasWithRegexp() {
        val time = "HH:mm:ss[.SSSSSSSSS][.SSSSSS][.SSS]"
        assertThat(
            eval.resolve("{{formattedAs 'yyyy-MM-dd\\'T\\'$time'}}"),
            `is`("$MATCHES:formattedAs}yyyy-MM-dd'T'$time")
        )
        assertThat(
            eval.resolve("{{formattedAndWithin 'yyyy-MM-dd\\'T\\'$time' '1d'}}"),
            startsWith("$MATCHES:formattedAndWithin}yyyy-MM-dd'T'$time${PARAMS_SEPARATOR}1d")
        )
    }
}
