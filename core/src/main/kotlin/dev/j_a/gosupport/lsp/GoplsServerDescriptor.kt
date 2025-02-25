package dev.j_a.gosupport.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.j_a.gosupport.lsp.customization.GoplsClientCustomization
import dev.j_a.ide.lsp.api.descriptor.CommandLineLanguageServerDescriptor
import dev.j_a.ide.lsp.api.descriptor.customization.ClientCustomization

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

    override fun createCommandLine(): GeneralCommandLine {
        val executable = requireNotNull(Gopls.findGoplsPath())
        return GeneralCommandLine(executable.toString()).withParameters("serve")
    }

    override val clientCustomization: ClientCustomization = GoplsClientCustomization
}