package com.noumenadigital.npl.lang.server

import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.compilation.DefaultCompilerService
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import kotlin.system.exitProcess

interface SystemExitHandler {
    fun exit(status: Int)
}

class DefaultSystemExitHandler : SystemExitHandler {
    override fun exit(status: Int) = exitProcess(status)
}

class LanguageServer(
    private val clientProvider: LanguageClientProvider = LanguageClientProvider(),
    private val compilerServiceFactory: (LanguageClientProvider) -> CompilerService = ::DefaultCompilerService,
    private val systemExitHandler: SystemExitHandler = DefaultSystemExitHandler(),
) : LanguageServer,
    LanguageClientAware {
    private val compilerService by lazy { compilerServiceFactory(clientProvider) }

    private val textDocumentService = TextDocumentHandler()
    private val workspaceService = WorkspaceHandler()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities =
            ServerCapabilities().apply {
                textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
            }
        params.workspaceFolders
            ?.singleOrNull()
            ?.uri
            ?.let { preloadSources(it) }
        return completedFuture(InitializeResult(capabilities))
    }

    private fun preloadSources(nplRootUri: String) {
        compilerService.preloadSources(nplRootUri)
    }

    override fun shutdown(): CompletableFuture<Any> = completedFuture(null)

    override fun exit() {
        systemExitHandler.exit(0)
    }

    override fun setTrace(params: SetTraceParams) {
        // no-op for now (we get an annoying exception if we don't implement this)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    override fun connect(client: LanguageClient?) {
        clientProvider.client = client
    }

    inner class TextDocumentHandler : TextDocumentService {
        override fun didOpen(params: DidOpenTextDocumentParams) {
            compilerService.updateSource(params.textDocument.uri, params.textDocument.text)
        }

        override fun didChange(params: DidChangeTextDocumentParams) {
            params.contentChanges.forEach { change ->
                compilerService.updateSource(params.textDocument.uri, change.text)
            }
        }

        override fun didClose(params: DidCloseTextDocumentParams?) { /* no-op */ }

        override fun didSave(params: DidSaveTextDocumentParams?) { /* no-op -- compilation occurs on change */ }
    }

    inner class WorkspaceHandler : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {}

        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {}
    }
}
