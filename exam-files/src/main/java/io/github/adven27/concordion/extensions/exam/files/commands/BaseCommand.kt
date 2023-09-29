package io.github.adven27.concordion.extensions.exam.files.commands

import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.html.Html
import io.github.adven27.concordion.extensions.exam.core.html.caption
import io.github.adven27.concordion.extensions.exam.core.html.italic
import io.github.adven27.concordion.extensions.exam.core.html.span
import io.github.adven27.concordion.extensions.exam.core.html.td
import io.github.adven27.concordion.extensions.exam.core.html.th
import io.github.adven27.concordion.extensions.exam.core.html.thead
import io.github.adven27.concordion.extensions.exam.core.html.tr

abstract class BaseCommand<M, R>(attrs: Set<String>) : ExamCommand<M, R>(setOf(NAME, DIR, FROM, CONTENT_TYPE) + attrs) {
    fun addHeader(element: Html, header: String?, content: String?) {
        element(
            thead()(
                th(header).style("width:20%"),
                th(content)
            )
        )
    }

    fun addRow(element: Html, vararg texts: String?) {
        element(tr().apply { texts.forEach { this(td(it)) } })
    }

    fun flCaption(dirPath: String) = caption()(
        italic(" ").css("far fa-folder-open me-1"),
        span(" ")
    ).text(dirPath)

    companion object {
        const val EMPTY = "<EMPTY>"
        const val HEADER = "file"
        const val FILE_CONTENT = "content"
        const val CONTENT_TYPE = "contentType"
        const val DIR = "dir"
        const val NAME = "name"
        const val FROM = "from"
    }
}
