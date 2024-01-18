@file:JvmName("HtmlBuilder")
@file:Suppress("TooManyFunctions")

package io.github.adven27.concordion.extensions.exam.core.html

import io.github.adven27.concordion.extensions.exam.core.ExamExtension
import io.github.adven27.concordion.extensions.exam.core.commands.swapText
import io.github.adven27.concordion.extensions.exam.core.resolveToObj
import nu.xom.Attribute
import nu.xom.Builder
import org.concordion.api.CommandCall
import org.concordion.api.Element
import org.concordion.api.Evaluator
import org.concordion.internal.ConcordionBuilder
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Optional
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

const val ID = "id"
const val ONCLICK = "onclick"
const val CLASS = "class"
const val STYLE = "style"
const val NAME = "name"
const val TABLE = "table"

@Suppress("TooManyFunctions", "SpreadOperator")
class Html(val el: Element) {
    constructor(tag: String) : this(Element(tag))
    constructor(tag: String, vararg attrs: Pair<String, String>) : this(tag, null, *attrs)
    constructor(tag: String, text: String? = null, vararg attrs: Pair<String, String>) : this(Element(tag)) {
        if (text != null) {
            this.text(text)
        }
        attrs(*attrs)
    }

    @JvmName("childs")
    operator fun invoke(vararg htmls: Html?): Html {
        htmls.filterNotNull().forEach { el.appendChild(it.el) }
        return this
    }

    @JvmName("childs")
    operator fun invoke(htmls: Collection<Html?>): Html {
        htmls.filterNotNull().forEach { el.appendChild(it.el) }
        return this
    }

    fun childs(): List<Html> {
        val result = ArrayList<Html>()
        for (e in el.childElements) {
            result.add(Html(e))
        }
        return result
    }

    fun childs(name: String): List<Html> {
        val result = ArrayList<Html>()
        for (e in el.getChildElements(name)) {
            result.add(Html(e))
        }
        return result
    }

    fun child(name: String) = childs(name).single()
    fun childOrNull(name: String) = childs(name).singleOrNull()
    fun child(predicate: (Html) -> Boolean) = childs().single(predicate)
    fun childOrNull(predicate: (Html) -> Boolean) = childs().singleOrNull(predicate)

    fun child(name: String, vararg names: String) = names.fold(child(name)) { acc, s -> acc.child(s) }

    fun attrs(vararg attrs: Pair<String, String>): Html {
        attrs.forEach {
            el.addAttribute(it.first, it.second)
        }
        return this
    }

    fun attr(name: String): String? = el.getAttributeValue(name)

    fun hasClass(name: String) = attr("class")?.let { name in it.split(" ") } ?: false

    operator fun get(name: String): String? = attr(name)

    fun attr(attr: String, value: String): Html {
        el.addAttribute(attr, value)
        return this
    }

    fun swapText(text: String) = el.swapText(text)

    infix fun collapse(target: String) =
        attrs("data-bs-toggle" to "collapse", "data-bs-target" to "#$target", "aria-expanded" to "true")

    infix fun css(classes: String): Html {
        el.addStyleClass(classes)
        return this
    }

    infix fun style(style: String): Html {
        attrs("style" to style)
        return this
    }

    fun muted(): Html {
        css("text-muted")
        return this
    }

    infix fun dropAllTo(element: Html): Html {
        moveChildrenTo(element)
        el.appendChild(element.el)
        return this
    }

    infix fun prependChild(html: Html): Html {
        el.prependChild(html.el)
        return this
    }

    infix fun below(html: Html): Html {
        el.appendSister(html.el)
        return this
    }

    fun remove() {
        parent().remove(this)
    }

    @JvmOverloads
    fun getAttr(name: String, eval: Evaluator? = null): String? {
        var attr = attr(name)
        if (attr != null) {
            attr = eval?.resolveToObj(attr)?.toString() ?: attr
        }
        return attr
    }

    fun getAttr(name: String, def: String): String = getAttr(name) ?: def

    fun el() = el

    fun success(): Html {
        css("bd-callout bd-callout-success")
        return this
    }

    fun panel(header: String, lvl: Int): Html = generateId().let {
        this(
            title(header, it, lvl),
            body(this, it)
        ).css("exam-example mb-3").attrs("data-type" to "example")
    }

    private fun body(root: Html, id: String) = div()
        .css("bd-example collapse show rounded bg-light bg-gradient")
        .attrs("id" to id).apply {
            root.moveChildrenTo(this)
        }

    private fun title(header: String, id: String, lvl: Int) = div()(
        tag("h$lvl").text(header).style("visibility: hidden; height:0;").css("test-class"),
        italic("", "class" to "far fa-caret-square-down"),
        tag("span").text(" "),
        tag("a").text(header).css("bd-example-title text-muted fw-lighter")
    ) collapse id

    fun localName() = el.localName!!

    fun hasChildren() = el.hasChildren()

    fun moveChildrenTo(html: Html): Html {
        el.moveChildrenTo(html.el)
        return this
    }

    fun moveAttributesTo(html: Html): Html {
        el.moveAttributesTo(html.el)
        return this
    }

    fun text() = el.text!!

    infix fun text(txt: String): Html {
        el.appendText(txt)
        return this
    }

    infix fun insteadOf(original: Element): Html {
        original.moveChildrenTo(this.el)
        original.moveAttributesTo(this.el)
        // FIXME may skip some attributes after first turn, repeat to move the rest... probably bug
        original.moveAttributesTo(this.el)
        original.appendSister(this.el)
        original.parentElement.removeChild(original)
        return this
    }

    infix fun insteadOf(original: Html) = insteadOf(original.el)

    fun first(tag: String): Html? {
        val first = el.childElements.firstOrNull { it.localName == tag }
        return if (first == null) null else Html(first)
    }

    fun last(tag: String): Html? {
        val last = el.childElements.lastOrNull { it.localName == tag }
        return if (last == null) null else Html(last)
    }

    fun firstOptional(tag: String): Optional<Html> = Optional.ofNullable(first(tag))

    fun all(tag: String): Collection<Html> {
        return el.childElements.asList().filter { it.localName == tag }.map { Html(it) }
    }

    fun firstOrThrow(tag: String) = first(tag) ?: error("<$tag> tag is required")

    fun removeChildren(): Html {
        moveChildrenTo(Html("tmp"))
        return this
    }

    fun remove(vararg children: Html?): Html {
        children.filterNotNull().forEach { el.removeChild(it.el) }
        return this
    }

    fun deepClone() = Html(el.deepClone())

    fun parent() = Html(el.parentElement)

    fun findBy(id: String): Html? {
        val e = this.el.getElementById(id)
        return if (e == null) null else Html(e)
    }

    fun descendants(tag: String) = this.el.getDescendantElements(tag).toList().map(::Html)

    fun tooltip(text: String, decorate: Boolean = false) = attrs(
        "title" to text,
        "data-toggle" to "tooltip",
        "data-placement" to "top",
        "style" to if (decorate) "text-decoration: underline grey dashed !important;" else ""
    )

    fun removeClass(name: String): Html {
        el.addAttribute(
            "class",
            el.getAttributeValue("class").let {
                it.split(" ").filterNot { it == name }.joinToString(" ")
            }
        )
        return this
    }

    override fun toString(): String {
        return el.toXML()
    }
}

fun div(txt: String? = null, vararg attrs: Pair<String, String>) = Html("div", txt, *attrs)

fun div(vararg attrs: Pair<String, String>) = Html("div", *attrs)

fun table(vararg attrs: Pair<String, String>) = table(Html("table", *attrs))

fun table(el: Html): Html = el.css("table table-sm caption-top")

fun table(el: Element): Html = table(Html(el))

fun thead(vararg attrs: Pair<String, String>) = Html("thead", *attrs).css("thead-default")

@JvmOverloads
fun th(txt: String? = null, vararg attrs: Pair<String, String>) = Html("th", txt, *attrs)

fun tbody(vararg attrs: Pair<String, String>) = Html("tbody", *attrs)

fun tr(vararg attrs: Pair<String, String>) = Html("tr", *attrs)

fun trWithTDs(vararg cellElements: Html): Html {
    val tr = tr()
    cellElements.forEach { tr(td()(it)) }
    return tr
}

fun td(txt: String? = null, vararg attrs: Pair<String, String>) = Html("td", txt, *attrs)

fun td(vararg attrs: Pair<String, String>) = Html("td", *attrs)

@JvmOverloads
fun italic(txt: String? = null, vararg attrs: Pair<String, String>) = Html("i", txt, *attrs)

fun code(txt: String) = Html("code", txt)

@JvmOverloads
fun span(txt: String? = null, vararg attrs: Pair<String, String>) = Html("span", txt, *attrs)

fun badge(txt: String, style: String) = span(txt).css("badge bg-$style me-1 ms-1")

fun pill(count: Long, style: String) = pill(if (count == 0L) "" else count.toString(), style)

fun pill(txt: String, style: String) = span(txt).css("badge badge-pill badge-$style")

fun link(txt: String) = Html("a", txt)

fun link(txt: String, vararg childs: Html) = link(txt)(*childs)

fun link(txt: String, src: String) = link(txt).attrs("href" to src)

@JvmOverloads
fun thumbnail(src: String, size: Int = 360) = link("", src)(image(src, size, size))

fun descendantTextContainer(element: Element): Element {
    val child = element.childElements.firstOrNull()
    return if (child == null) element else descendantTextContainer(child)
}

fun image(src: String) = Html("image").attrs("src" to src)

fun image(src: String, width: Int, height: Int) = image(src).css("img-thumbnail")
    .attrs("width" to "$width", "height" to "$height")

fun h(n: Int, text: String) = Html("h$n", text)

@JvmOverloads
fun caption(txt: String? = null) = Html("caption", txt)

@JvmOverloads
fun pre(txt: String? = null, vararg attrs: Pair<String, String>) = Html("pre", txt, *attrs)

fun paragraph(txt: String) = Html("p", txt)

fun codeHighlight(text: String?, lang: String? = null) =
    pre().attrs("class" to "doc-code ${if (lang != null) "language-$lang" else ""}")(code(text ?: ""))

fun tag(tag: String) = Html(tag)

fun body() = Html("body")

fun body(txt: String) = Html("body", txt)

fun ul(vararg attrs: Pair<String, String>) = Html("ul", *attrs)

fun list() = ul() css "list-group"

fun nu.xom.Element.moveAttributesTo(element: nu.xom.Node) {
    val count = attributeCount
    for (i in 0 until count) {
        val attribute: Attribute = getAttribute(0)
        removeAttribute(attribute)
        (element as nu.xom.Element).addAttribute(attribute)
    }
}

@JvmOverloads
fun li(text: String? = null) = Html("li", text)

fun button(txt: String = "", vararg attrs: Pair<String, String>) =
    Html("button", txt, *attrs).attrs("type" to "button") css "btn btn-light btn-sm text-muted me-1"

fun CommandCall?.html() = Html(this!!.element)
fun CommandCall?.attr(name: String, def: String) = html().attr(name) ?: def

fun generateId(): String = "e${UUID.randomUUID()}"

fun nu.xom.Element.html() = Html(Element(this))

fun String.toHtml() = parseTemplate(this)
fun parseTemplate(tmpl: String) = Html(Element(Builder().build(StringReader(tmpl)).rootElement).deepClone())

fun loadXMLFromString(xml: String): org.w3c.dom.Document? = DocumentBuilderFactory.newInstance().let {
    it.isNamespaceAware = true
    it.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
}

fun String.fileExt() = substring(lastIndexOf('.') + 1).lowercase()

fun String.toMap(): Map<String, String> = unboxIfNeeded(this).split(",").associate {
    val (n, v) = it.split("=")
    Pair(n.trim(), v.trim())
}

private fun unboxIfNeeded(it: String) = if (it.trim().startsWith("{")) it.substring(1, it.lastIndex) else it

private fun failTemplate(header: String = "", help: String = "", cntId: String) = //language=xml
    """
    <div class="alert-warning">
      ${if (header.isNotEmpty()) "<div class='card-header bg-danger text-white'>$header</div>" else ""}
      <div>
        <div id='$cntId'> </div>
        ${help(help, cntId)}
      </div>
    </div>
    """

//language=xml
private fun help(help: String, cntId: String) = if (help.isNotEmpty()) {
    """
<p data-bs-toggle="collapse" data-bs-target="#help-$cntId" aria-expanded="false">
    <i class="far fa-caret-square-down"> </i><span> Help</span>
</p>
<div id='help-$cntId' class='collapse'>$help</div>
"""
} else {
    ""
}

fun errorMessage(
    header: String = "",
    message: String,
    help: String = "",
    html: Html = span(),
    type: String? = null
): Pair<String, Html> =
    "error-${System.currentTimeMillis()}".let { id ->
        id to failTemplate(header, help, id).toHtml().apply {
            findBy(id)!!(
                codeHighlight(message, type).css("failure"),
                html
            )
        }
    }

fun Throwable.rootCause(): Throwable {
    var rootCause = this
    while (rootCause.cause != null && rootCause.cause !== rootCause) {
        rootCause = rootCause.cause!!
    }
    return rootCause
}

fun Throwable.rootCauseMessage() = this.rootCause().let { it.message ?: it.toString() }

fun nu.xom.Element.addCcAttr(name: String, value: String) {
    if (value.isNotEmpty()) {
        addAttribute(Attribute(name, value).apply { setNamespace("c", ConcordionBuilder.NAMESPACE_CONCORDION_2007) })
    }
}

fun nu.xom.Element.addExamAttr(name: String, value: String) {
    if (value.isNotEmpty()) addAttribute(Attribute(name, value).apply { setNamespace("e", ExamExtension.NS) })
}
