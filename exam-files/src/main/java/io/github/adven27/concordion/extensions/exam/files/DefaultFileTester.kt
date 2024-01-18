package io.github.adven27.concordion.extensions.exam.files

import io.github.adven27.concordion.extensions.exam.core.InvalidXml
import nu.xom.Builder
import nu.xom.Document
import nu.xom.ParsingException
import java.io.File
import java.io.File.separator
import java.io.IOException

open class DefaultFileTester : FileTester {

    override fun clearFolder(path: String) {
        File(path).listFiles()?.forEach { if (!it.delete()) throw CantDeleteFile(it) }
    }

    override fun createFile(path: String, content: String?) {
        File(path).also {
            createParentDirs(it)
            if (it.createNewFile() && !content.isNullOrEmpty()) {
                it.appendText(content)
            }
        }
    }

    override fun fileNames(path: String) = getFileNamesForDir(File(path), "")

    private fun getFileNamesForDir(dir: File, s: String): List<String> = dir.listFiles()?.flatMap {
        if (it.isFile) {
            listOf((if ("" == s) "" else s + separator) + it.name)
        } else {
            getFileNamesForDir(it, if ("" == s) it.name else s + separator + it.name)
        }
    } ?: listOf()

    override fun fileExists(filePath: String): Boolean = File(filePath).exists()

    override fun documentFrom(path: String): Document = try {
        Builder().build(File(path))
    } catch (e: ParsingException) {
        throw InvalidXml(e)
    } catch (e: IOException) {
        throw InvalidXml(e)
    }

    override fun read(path: String, file: String): String = readFile(File("$path$separator$file"))

    protected fun readFile(file: File) = runCatching { file.readText() }.getOrDefault("ERROR WHILE FILE READING")

    companion object {
        class CantDeleteFile(file: File) : RuntimeException("could not delete file " + file.path)

        private fun createParentDirs(file: File) {
            file.canonicalFile.parentFile?.also {
                it.mkdirs()
                if (!it.isDirectory) {
                    throw IOException("Unable to create parent directories of $file")
                }
            }
        }
    }
}
