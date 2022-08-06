package io.github.adven27.concordion.extensions.exam.nosql.commands

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.adven27.concordion.extensions.exam.core.commands.CommandParser
import io.github.adven27.concordion.extensions.exam.core.content
import io.github.adven27.concordion.extensions.exam.core.html.html
import io.github.adven27.concordion.extensions.exam.core.resolveJson
import org.concordion.api.CommandCall
import org.concordion.api.Evaluator
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Objects.nonNull

private const val DOCUMENT_TAG = "doc"

class SetParser : CommandParser<SetCommand.Operation> {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    override fun parse(command: CommandCall, evaluator: Evaluator) = SetCommand.Operation(
        collection = collectionFrom(command, evaluator),
        documents = documentsFrom(command, evaluator)
    )

    private fun documentsFrom(command: CommandCall, eval: Evaluator): List<NoSqlDocument> {
        val sourceFile = command.html().takeAwayAttr("from", eval)
        return if (nonNull(sourceFile)) fileAsJsonStrings(sourceFile!!, eval) else documentsFromBody(command, eval)
    }

    private fun documentsFromBody(command: CommandCall, eval: Evaluator): List<NoSqlDocument> =
        command.html().all(DOCUMENT_TAG).map {
            NoSqlDocument(
                mapper.readValue(
                    it.content(eval),
                    object : TypeReference<Map<String, Any>>() {}
                )
            )
        }

    private fun fileAsJsonStrings(sourceFile: String, eval: Evaluator): List<NoSqlDocument> {
        val docsString: String = eval.resolveJson(
            fileInputStream(sourceFile).bufferedReader().use(BufferedReader::readText)
        )
        val docsList: List<Map<String, Any>> = mapper.readValue(
            docsString,
            object : TypeReference<List<Map<String, Any>>>() {}
        )
        return docsList.map { NoSqlDocument(it) }
    }

    private fun fileInputStream(sourceFile: String): InputStream {
        val file = withLeadingSlash(sourceFile)
        return javaClass.getResourceAsStream(file)
            ?: javaClass.getResourceAsStream("/datasets$file")
            ?: throw FileNotFoundException(file.substring(1))
    }

    private fun withLeadingSlash(sourceFile: String): String {
        var file = sourceFile
        if (!file.startsWith("/")) {
            file = "/$file"
        }
        return file
    }

    private fun collectionFrom(command: CommandCall, eval: Evaluator) = (command.html().takeAwayAttr("collection", eval)
        ?: throw IllegalArgumentException("collection attribute is missing in set command"))
}
