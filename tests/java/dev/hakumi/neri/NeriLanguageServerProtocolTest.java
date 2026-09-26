package dev.hakumi.neri;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard;
import com.intellij.openapi.util.Disposer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Real LSP4J transport, compiler edits and the Rider document/caret adapter. */
public final class NeriLanguageServerProtocolTest {
    private static final String URI = "untitled:neri-editor-integration";

    private static void open(LanguageServer server, String uri, String text) {
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "neri", 1, text)));
    }

    private static void change(LanguageServer server, String uri, int version, String text) {
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, version),
                List.of(new TextDocumentContentChangeEvent(text))));
    }

    private static long enter(LanguageServer server, String uri, String source) throws Exception {
        var document = new DocumentImpl(source, true);
        int originalCaret = document.getTextLength();
        var pending = NeriEnterHandler.Pending.capture(document, originalCaret);
        long started = System.nanoTime();
        var edits = server.getTextDocumentService().onTypeFormatting(new DocumentOnTypeFormattingParams(
                new TextDocumentIdentifier(uri), new FormattingOptions(2, true),
                new Position(pending.line(), pending.caret() - pending.start()), "\n"))
                .get(10, TimeUnit.SECONDS);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        int caret = pending.apply(document, originalCaret, edits);

        if (!document.getText().endsWith("\n  \nend") || caret != pending.start() + 2) {
            throw new AssertionError("Real Enter must insert end and keep the caret in the body: " + edits);
        }
        return elapsed;
    }

    @SuppressWarnings("deprecation") // rootUri exercises older LSP clients supported by Neri.
    public static void main(String[] args) throws Exception {
        String compiler = args.length > 0 ? args[0] : System.getenv("NERI_LSP_TEST_COMPILER");
        if (compiler == null || compiler.isBlank()) {
            System.out.println("Neri LSP4J integration skipped: set NERI_LSP_TEST_COMPILER.");
            return;
        }
        var lifetime = Disposer.newDisposable();
        new CoreApplicationEnvironment(lifetime);
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
                DocumentWriteAccessGuard.EP_NAME, DocumentWriteAccessGuard.class);
        Process process = new ProcessBuilder(compiler, "lsp").redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var executor = Executors.newCachedThreadPool();
        var idleDiagnostics = new CompletableFuture<PublishDiagnosticsParams>();
        var launcher = LSPLauncher.createClientLauncher(new LanguageClient() {
            @Override public void telemetryEvent(Object value) { }
            @Override public void publishDiagnostics(PublishDiagnosticsParams params) {
                if (URI.equals(params.getUri()) && Integer.valueOf(24).equals(params.getVersion())) {
                    idleDiagnostics.complete(params);
                }
            }
            @Override public void showMessage(MessageParams params) { }
            @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void logMessage(MessageParams params) { }
        }, process.getInputStream(), process.getOutputStream(), executor, consumer -> consumer);
        var listening = launcher.startListening();
        try {
            var server = launcher.getRemoteProxy();
            var initialize = new InitializeParams();
            var capabilities = new ClientCapabilities();
            var textCapabilities = new TextDocumentClientCapabilities();
            var completionCapabilities = new CompletionCapabilities();
            completionCapabilities.setCompletionItem(new CompletionItemCapabilities(true));
            completionCapabilities.setContextSupport(true);
            textCapabilities.setCompletion(completionCapabilities);
            capabilities.setTextDocument(textCapabilities);
            initialize.setCapabilities(capabilities);
            if (args.length > 1) initialize.setRootUri(Path.of(args[1]).toUri().toString());
            var initialized = server.initialize(initialize).get(10, TimeUnit.SECONDS);

            if (!"\n".equals(initialized.getCapabilities().getDocumentOnTypeFormattingProvider().getFirstTriggerCharacter())) {
                throw new AssertionError("Compiler must advertise newline formatting");
            }
            server.initialized(new InitializedParams());
            for (String trigger : List.of(" ", ":", ")")) {
                if (!initialized.getCapabilities().getCompletionProvider().getTriggerCharacters().contains(trigger)
                        || !NeriCompletionSupport.INSTANCE.isTriggerCharacterRespected(trigger.charAt(0))) {
                    throw new AssertionError("Rider must honor declaration trigger: " + trigger);
                }
            }
            String declarationUri = "untitled:neri-declaration-integration";
            open(server, declarationUri, "def blabla()");
            var declarationParams = new CompletionParams(new TextDocumentIdentifier(declarationUri), new Position(0, 12));
            declarationParams.setContext(new CompletionContext(CompletionTriggerKind.TriggerCharacter, ")"));
            var declaration = server.getTextDocumentService().completion(declarationParams).get(10, TimeUnit.SECONDS);

            if (!declaration.isRight() || !declaration.getRight().isIncomplete()) {
                throw new AssertionError("Typed declarations must refresh their insertion text as typing continues");
            }
            var template = declaration.getRight().getItems().getFirst();

            if (template.getInsertTextFormat() != InsertTextFormat.Snippet
                    || !": ${1:Void}\n  $0\nend".equals(template.getTextEdit().getLeft().getNewText())
                    || !new Range(new Position(0, 12), new Position(0, 12)).equals(template.getTextEdit().getLeft().getRange())) {
                throw new AssertionError("Complete the current signature with an editable return type: " + template);
            }
            change(server, declarationUri, 2, "def renamed()");
            declarationParams.setPosition(new Position(0, 13));
            var renamed = server.getTextDocumentService().completion(declarationParams).get(10, TimeUnit.SECONDS);

            if (!new Range(new Position(0, 13), new Position(0, 13)).equals(
                    renamed.getRight().getItems().getFirst().getTextEdit().getLeft().getRange())) {
                throw new AssertionError("Declaration completion reused an earlier typed name");
            }
            server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(declarationUri)));
            open(server, URI, "# 🙂\ndef run(): Void\n");
            enter(server, URI, "# 🙂\ndef run(): Void\n");

            // Optional real-workspace replay: compiler, root, then the open source paths.
            for (int index = 2; index < args.length; index++) {
                var path = Path.of(args[1]).resolve(args[index]);
                open(server, path.toUri().toString(), Files.readString(path));
            }
            String typingUri = args.length > 2 ? Path.of(args[1]).resolve(args[2]).toUri().toString() : URI;
            String text = args.length > 2 ? Files.readString(Path.of(args[1]).resolve(args[2])) + "\nclass EnterProbe\n"
                    : "# 🙂\nclass EnterProbe\n";
            change(server, typingUri, 2, text);
            long opened = enter(server, typingUri, text);
            for (int version = 3; version <= 22; version++) {
                change(server, typingUri, version, text + " ".repeat(version));
            }
            long burst = enter(server, typingUri, text + " ".repeat(22));

            String member = "class Counter\n  def increment(): Int\n    return 1\n  end\nend\n"
                    + "def main(): Void\n  let counter = new Counter()\n  counter.\nend\n";
            change(server, URI, 23, member);
            long started = System.nanoTime();
            var completion = server.getTextDocumentService().completion(new CompletionParams(
                    new TextDocumentIdentifier(URI), new Position(7, 10))).get(10, TimeUnit.SECONDS);
            long completionMs = (System.nanoTime() - started) / 1_000_000;
            var items = completion.isLeft() ? completion.getLeft() : completion.getRight().getItems();
            var increment = items.stream().filter(item -> "increment".equals(item.getLabel())).findFirst().orElseThrow();

            if (increment.getTextEdit() == null || !increment.getTextEdit().isLeft()
                    || !"increment()$0".equals(increment.getTextEdit().getLeft().getNewText())) {
                throw new AssertionError("Member completion must use current bindings and insert parentheses");
            }
            change(server, URI, 24, member.replace("  counter.\n", "\n  counter.increment()\n"));
            var diagnostics = idleDiagnostics.get(10, TimeUnit.SECONDS);

            if (diagnostics.getDiagnostics().stream().anyMatch(item -> item.getSeverity() == DiagnosticSeverity.Error)) {
                throw new AssertionError("Idle analysis must publish the final valid version: " + diagnostics);
            }
            server.shutdown().get(10, TimeUnit.SECONDS);
            server.exit();
            System.out.println("Real Enter: " + opened + " ms; 20-change burst: " + burst
                    + " ms; member completion: " + completionMs + " ms. Caret and idle diagnostics passed.");
        } finally {
            listening.cancel(true);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            Disposer.dispose(lifetime);
        }
    }
}
