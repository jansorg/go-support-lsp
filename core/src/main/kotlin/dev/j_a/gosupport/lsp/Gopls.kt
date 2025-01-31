package dev.j_a.gosupport.lsp

import com.intellij.openapi.vfs.VirtualFile

/**
 * Interaction with gopls.
 *
 * https://github.com/golang/tools/blob/44670c7f61274a590108667938d4e33d8d9bdd4a/gopls/internal/file/kind.go
 */
object Gopls {
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