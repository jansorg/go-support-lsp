package dev.j_a.gosupport.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.j_a.ide.lsp.api.clientCapabilities.ClientFeature
import dev.j_a.ide.lsp.api.descriptor.CommandLineLanguageServerDescriptor
import dev.j_a.ide.lsp.api.descriptor.customization.ClientCustomization
import dev.j_a.ide.lsp.api.descriptor.customization.ClientFoldingSupport
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.FoldingRange

class GoplsServerDescriptor(project: Project) : CommandLineLanguageServerDescriptor(
    project,
    GoplsLanguageServerSupport
) {
    override fun isSupported(file: VirtualFile): Boolean {
        return Gopls.isSupported(file)
    }

    override fun getLanguageId(file: VirtualFile): String {
        return Gopls.filenameToLanguageId[file.name]
            ?: Gopls.extensionToLanguageId[file.extension]
            ?: super.getLanguageId(file)
    }

    override val clientCustomization: ClientCustomization = object : ClientCustomization() {
        override val foldingSupport: ClientFoldingSupport = object : ClientFoldingSupport() {
            override fun preProcess(foldingRange: FoldingRange) {
                // https://github.com/golang/go/issues/71489
                if (foldingRange.endLine > foldingRange.startLine && foldingRange.endCharacter == null) {
                    foldingRange.endCharacter = 0
                }
            }
        }
    }

    override fun createCommandLine(): GeneralCommandLine {
        val executable = requireNotNull(PathEnvironmentVariableUtil.findInPath("gopls"))
        return GeneralCommandLine(executable.toString()).withParameters("serve")
    }
}