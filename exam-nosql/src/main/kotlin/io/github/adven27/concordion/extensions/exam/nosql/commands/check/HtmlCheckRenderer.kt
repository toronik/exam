package io.github.adven27.concordion.extensions.exam.nosql.commands.check

import io.github.adven27.concordion.extensions.exam.core.commands.VerifyFailureEvent
import io.github.adven27.concordion.extensions.exam.core.commands.VerifyListener
import io.github.adven27.concordion.extensions.exam.core.commands.VerifySuccessEvent
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Actual
import io.github.adven27.concordion.extensions.exam.nosql.commands.check.CheckCommand.Expected

class HtmlCheckRenderer : VerifyListener<Expected, Actual> {

    override fun successReported(event: VerifySuccessEvent<Expected, Actual>) {
        TODO("Not yet implemented")
    }

    override fun failureReported(event: VerifyFailureEvent<Expected>) {
        TODO("Not yet implemented")
    }
}
