package dev.j_a.gosupport.lsp

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Interaction with gopls.
 *
 * https://github.com/golang/tools/blob/44670c7f61274a590108667938d4e33d8d9bdd4a/gopls/internal/file/kind.go
 */
object Gopls {
    /**
     * Check if the gopls binary is available.
     */
    fun isAvailable(@Suppress("unused") project: Project): Boolean {
        return findGoplsPath() != null
    }

    /**
     * @return the path to the gopls binary, or `null` if it is not available.
     */
    fun findGoplsPath(): File? {
        return PathEnvironmentVariableUtil.findInPath("gopls")
    }

    fun isSupported(file: VirtualFile): Boolean {
        return file.extension in extensionToLanguageId || file.name in filenameToLanguageId
    }

    val extensionToLanguageId = mapOf(
        "go" to "go",
        "tmpl" to "tmpl",
        "gotmpl" to "gotmpl",
    )

    val filenameToLanguageId = mapOf(
        "go.mod" to "go.mod",
        "go.sum" to "go.sum",
        "go.work" to "go.work",
    )
}