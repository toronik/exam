package io.github.adven27.concordion.extensions.exam.files

import io.github.adven27.concordion.extensions.exam.core.ExamPlugin
import io.github.adven27.concordion.extensions.exam.core.commands.ExamCommand
import io.github.adven27.concordion.extensions.exam.core.html.TABLE
import io.github.adven27.concordion.extensions.exam.files.commands.FilesCheckCommand
import io.github.adven27.concordion.extensions.exam.files.commands.FilesSetCommand
import io.github.adven27.concordion.extensions.exam.files.commands.FilesShowCommand

class FlPlugin @JvmOverloads constructor(
    private var filesLoader: FilesLoader = DefaultFilesLoader()
) : ExamPlugin.NoSetUp(), ExamPlugin {
    override fun commands(): List<ExamCommand> = listOf(
        FilesShowCommand("fl-show", TABLE, filesLoader),
        FilesSetCommand("fl-set", TABLE, filesLoader),
        FilesCheckCommand("fl-check", "div", filesLoader)
    )
}
