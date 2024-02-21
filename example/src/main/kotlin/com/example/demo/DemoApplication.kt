package com.example.demo

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType.parseMediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@RestController
class DemoApplication {

    @PostMapping("/mirror/soap")
    fun soap(@RequestBody body: String) = ResponseEntity<String>(body, soapHeader(), OK)

    private fun soapHeader() =
        HttpHeaders().apply { contentType = parseMediaType("application/soap+xml; charset=utf-8") }

    @RequestMapping("/mirror/request")
    fun mirror(@RequestBody body: Map<String, Any>?, r: HttpServletRequest) = listOfNotNull(
        r.method to r.uri(),
        body?.let { "body" to it },
        r.headerNames.toList().takeIf { it.isNotEmpty() }?.let { n -> "headers" to n.associateWith(r::getHeader) },
        r.cookies?.takeIf { it.isNotEmpty() }?.let { c -> "cookies" to c.associate { it.name to it.value } }
    ).toMap()

    private fun HttpServletRequest.uri() =
        requestURI + (queryString.takeUnless { it.isNullOrBlank() }?.let { "?$it" } ?: "")
}

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
