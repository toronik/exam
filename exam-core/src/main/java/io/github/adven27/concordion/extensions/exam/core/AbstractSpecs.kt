package io.github.adven27.concordion.extensions.exam.core

import org.concordion.api.AfterSuite
import org.concordion.api.BeforeSuite
import org.concordion.api.ConcordionResources
import org.concordion.api.FullOGNL
import org.concordion.api.extension.Extension
import org.concordion.api.extension.Extensions
import org.concordion.api.option.ConcordionOptions
import org.concordion.integration.junit4.ConcordionRunner
import org.junit.runner.RunWith

@Suppress("unused", "TooManyFunctions")
@RunWith(ConcordionRunner::class)
@ConcordionOptions(declareNamespaces = ["c", "http://www.concordion.org/2007/concordion", "e", ExamExtension.NS])
@ConcordionResources(includeDefaultStyling = false)
@FullOGNL
@Extensions(AdocExtension::class)
abstract class AbstractSpecs {

    @Extension
    private val exam = if (EXAM == null) this.init().also { EXAM = it } else EXAM

    @BeforeSuite
    fun specsSetUp() {
        beforeSetUp()
        EXAM!!.setUp()
        beforeSutStart()
        if (SPECS_SUT_START) {
            startSut()
        }
    }

    @AfterSuite
    fun specsTearDown() {
        if (SPECS_SUT_START) {
            stopSut()
        }
        afterSutStop()
        EXAM!!.tearDown()
        afterTearDown()
    }

    protected abstract fun init(): ExamExtension
    protected open fun beforeSetUp() = Unit
    protected open fun beforeSutStart() = Unit
    protected abstract fun startSut()
    protected abstract fun stopSut()
    protected open fun afterSutStop() = Unit
    protected open fun afterTearDown() = Unit

    fun addToMap(old: Map<String, String>?, name: String, value: String) = mapOf(name to value) + (old ?: emptyMap())
    fun prettyXml(text: String) = text.prettyXml()
    fun prettyJson(text: String) = text.prettyJson()

    companion object {
        const val PROP_SPECS_SUT_START = "SPECS_SUT_START"

        @JvmField
        val SPECS_SUT_START: Boolean = System.getProperty(PROP_SPECS_SUT_START, "true").toBoolean()
        private var EXAM: ExamExtension? = null
    }
}
