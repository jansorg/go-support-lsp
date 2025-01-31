package dev.j_a.gosupport.lsp

import dev.j_a.ide.lsp.api.registry.RegisterLanguageServerSupportActivity

/**
 * Registers the LSP support for Go when a project is opened.
 */
class GoplsRegisterLanguageServerActivity : RegisterLanguageServerSupportActivity(GoplsLanguageServerSupport)