package dev.j_a.gosupport.lsp.customization

import dev.j_a.ide.lsp.api.descriptor.customization.ClientCustomization
import dev.j_a.ide.lsp.api.descriptor.customization.ClientFoldingSupport
import org.eclipse.lsp4j.FoldingRange

object GoplsClientCustomization : ClientCustomization() {
    override val foldingSupport: ClientFoldingSupport = GoplsClientFoldingSupport
}

private object GoplsClientFoldingSupport : ClientFoldingSupport() {
    override fun preProcess(foldingRange: FoldingRange) {
        // https://github.com/golang/go/issues/71489
        if (foldingRange.endLine > foldingRange.startLine && foldingRange.endCharacter == null) {
            foldingRange.endCharacter = 0
        }
    }
}