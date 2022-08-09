package io.github.adven27.concordion.extensions.exam.nosql.commands.set

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.adven27.concordion.extensions.exam.core.commands.SetUpEvent
import io.github.adven27.concordion.extensions.exam.core.commands.SetUpListener
import io.github.adven27.concordion.extensions.exam.core.pretty
import io.github.adven27.concordion.extensions.exam.core.toHtml
import io.github.adven27.concordion.extensions.exam.nosql.NoSqlDocument
import org.concordion.api.Element
import org.concordion.api.listener.AbstractElementEvent

class HtmlSetRenderer : SetUpListener<SetCommand.Operation> {

    private fun root(event: AbstractElementEvent): Element = event.element
    override fun setUpCompleted(event: SetUpEvent<SetCommand.Operation>) {
        with(root(event)) {
            appendSister(template(event.target.collection, event.target.documents).toHtml().el)
            parentElement.removeChild(this)
        }
    }

    fun template(collection: String, documents: List<NoSqlDocument>) = //language=html
        """
<div class="nosql-set">
    <table class="table table-sm caption-top">
        <caption><i class="fa fa-database me-1"> </i><span>$collection</span></caption>
        <tbody> ${renderDocuments(documents)} </tbody>
    </table>
</div>
        """.trimIndent()

    //language=html
    fun renderDocuments(documents: List<NoSqlDocument>) = documents.joinToString("\n") {
        """
<tr><td class='exp-body'>${renderDoc(it)}</td></tr>
        """.trimIndent()
    }.ifEmpty { """<tr><td class='exp-body'>EMPTY</td></tr>""" }

    // language=html
    fun renderDoc(doc: NoSqlDocument): String =
        """<div class="json"></div>""".toHtml()
            .text(ObjectMapper().writeValueAsString(doc.fields).pretty("json")).el.toXML()
}
