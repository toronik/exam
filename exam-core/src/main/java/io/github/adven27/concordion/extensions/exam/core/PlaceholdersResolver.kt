@file:JvmName("PlaceholdersResolver")

package io.github.adven27.concordion.extensions.exam.core

import io.github.adven27.concordion.extensions.exam.core.handlebars.HANDLEBARS
import io.github.adven27.concordion.extensions.exam.core.handlebars.matchers.PLACEHOLDER_TYPE
import io.github.adven27.concordion.extensions.exam.core.handlebars.resolveObj
import org.concordion.api.Evaluator

fun Evaluator.resolve(content: Content) = Content(resolve(content.body, content.type), content.type)
fun Evaluator.resolve(template: String): String = resolveToObj(template).toString()
fun Evaluator.resolve(body: String, type: String): String =
    apply { setVariable("#$PLACEHOLDER_TYPE", type) }.resolveToObj(body).toString()
        .also { setVariable("#$PLACEHOLDER_TYPE", null) }

fun Evaluator.resolveToObj(placeholder: String?): Any? = HANDLEBARS.resolveObj(this, placeholder)

fun String?.vars(eval: Evaluator, setVar: Boolean = false, separator: String = ","): Map<String, Any?> =
    pairs(separator)
        .mapValues { (k, v) -> k to eval.resolveToObj(v).apply { if (setVar) eval.setVariable("#$k", this) } }

fun String?.headers(separator: String = ","): Map<String, String> = pairs(separator)

private fun String?.pairs(separator: String) = this?.split(separator)
    ?.map { it.split('=', limit = 2) }
    ?.associate { (k, v) -> k.trim() to v.trim() }
    ?: emptyMap()
