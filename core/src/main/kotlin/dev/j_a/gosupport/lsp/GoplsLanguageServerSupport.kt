package dev.j_a.gosupport.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.j_a.gosupport.i18n
import dev.j_a.ide.lsp.api.BaseLanguageServerSupport
import dev.j_a.ide.lsp.api.LanguageServerSupport

object GoplsLanguageServerSupport : BaseLanguageServerSupport(
    "dev.j_a.ide.gosupport",
    i18n("lsp.languageSupport.displayName")
) {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LanguageServerSupport.LanguageServerStarter
    ) {
        if (Gopls.isSupported(file)) {
            serverStarter.ensureStarted(GoplsServerDescriptor(project))
        }
    }
}