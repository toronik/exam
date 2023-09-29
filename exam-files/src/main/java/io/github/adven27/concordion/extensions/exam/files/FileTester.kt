package io.github.adven27.concordion.extensions.exam.files

import io.github.adven27.concordion.extensions.exam.core.Content
import nu.xom.Document

interface FileTester {
    fun clearFolder(path: String)
    fun createFile(filePath: String, fileContent: String?)
    fun fileNames(path: String): List<String>
    fun fileExists(filePath: String): Boolean
    fun documentFrom(path: String): Document
    fun read(path: String, file: String): String

    data class File @JvmOverloads constructor(val name: String, val content: Content? = null)
}
