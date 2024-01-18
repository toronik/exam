package io.github.adven27.concordion.extensions.exam.ws

import java.util.Locale

enum class HttpContentType(val contentTypes: Set<String>) {
    ANY(setOf("*/*")),
    TEXT(setOf("text/plain")),
    JSON(setOf("application/json", "application/javascript", "text/javascript", "text/json")),
    XML(setOf("application/xml", "text/xml", "application/xhtml+xml", "application/soap+xml")),
    HTML(setOf("text/html")),
    URLENC(setOf("application/x-www-form-urlencoded")),
    BINARY(setOf("application/octet-stream")),
    MULTIPART(
        setOf(
            "multipart/form-data",
            "multipart/alternative",
            "multipart/byteranges",
            "multipart/digest",
            "multipart/mixed",
            "multipart/parallel",
            "multipart/related",
            "multipart/report",
            "multipart/signed",
            "multipart/encrypted"
        )
    );

    companion object {
        @Suppress("CyclomaticComplexMethod")
        fun from(contentType: String) = contentType.lowercase(Locale.getDefault()).substringBefore(";").let {
            when {
                it in JSON.contentTypes || it.endsWith("+json") -> JSON
                it in XML.contentTypes || it.endsWith("+xml") -> XML
                it in HTML.contentTypes || it.endsWith("+html") -> HTML
                else -> when (it) {
                    in TEXT.contentTypes -> TEXT
                    in ANY.contentTypes -> ANY
                    in URLENC.contentTypes -> URLENC
                    in BINARY.contentTypes -> BINARY
                    in MULTIPART.contentTypes -> MULTIPART
                    else -> null
                }
            }
        }
    }
}
